package com.rustym.mobile.playback

import android.net.Uri
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.ResolvingDataSource
import androidx.media3.datasource.cache.Cache
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.cache.LeastRecentlyUsedCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import androidx.media3.datasource.cache.CacheWriter
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Semaphore
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.audio.AudioSink
import androidx.media3.exoplayer.audio.DefaultAudioSink
import androidx.media3.exoplayer.audio.SilenceSkippingAudioProcessor
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.session.MediaSession
import android.content.Intent
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionResult
import androidx.media3.session.CommandButton
import android.os.Bundle
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.rustym.mobile.R
import com.rustym.mobile.data.Auth
import com.rustym.mobile.data.Library
import com.rustym.mobile.data.toTrack
import com.rustym.mobile.data.Track
import com.rustym.mobile.data.WATCH_URL
import androidx.media3.session.MediaSessionService
import com.rustym.mobile.data.Yt
import java.io.IOException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/** Shared disk cache used by playback and background prefetch. Cache keys are stable video IDs,
 * not expiring YouTube stream URLs. The LRU cap prevents the cache from filling the device. */
object AudioCache {
    @Volatile private var cache: Cache? = null
    @Volatile private var upstream: androidx.media3.datasource.DataSource.Factory? = null
    private val active = ConcurrentHashMap.newKeySet<String>()
    private val downloadPermits = Semaphore(3)

    fun initialize(context: android.content.Context, upstreamFactory: androidx.media3.datasource.DataSource.Factory) {
        if (cache != null) return
        synchronized(this) {
            if (cache == null) {
                val dir = File(context.filesDir, "rustym_audio_cache").apply { mkdirs() }
                cache = SimpleCache(dir, LeastRecentlyUsedCacheEvictor(2L * 1024L * 1024L * 1024L),
                    androidx.media3.database.StandaloneDatabaseProvider(context))
                upstream = upstreamFactory
            }
        }
    }

    fun dataSourceFactory(): CacheDataSource.Factory {
        val c = checkNotNull(cache) { "Audio cache is not initialized" }
        val u = checkNotNull(upstream)
        return CacheDataSource.Factory().setCache(c).setUpstreamDataSourceFactory(u)
            .setCacheKeyFactory { spec -> spec.key ?: spec.uri.getQueryParameter("v") ?: spec.uri.toString() }
    }

    fun prefetch(track: Track) {
        val c = cache ?: return
        if (track.isLocal || c.isCached(track.id, 0, Long.MAX_VALUE) || !active.add(track.id)) return
        var acquired = false
        try {
            downloadPermits.acquire()
            acquired = true
            val source = dataSourceFactory().createDataSource()
            val spec = DataSpec.Builder().setUri(WATCH_URL + track.id).setKey(track.id).build()
            CacheWriter(source, spec, null, null).cache()
        } catch (_: Exception) {
            // A failed prefetch is non-fatal; normal playback can retry it.
        } finally {
            if (acquired) downloadPermits.release()
            active.remove(track.id)
        }
    }

    fun isCached(track: Track): Boolean = !track.isLocal && (cache?.isCached(track.id, 0, Long.MAX_VALUE) == true)

    /** Number of songs downloaded into the offline cache, for the storage page. */
    fun cachedSongs(): Int {
        val c = cache ?: return 0
        return runCatching {
            var n = 0
            for (key in c.keys) {
                if (runCatching { c.isCached(key, 0, Long.MAX_VALUE) }.getOrDefault(false)) n++
            }
            n
        }.getOrDefault(0)
    }

    /** Total bytes held by the offline cache. */
    fun cachedBytes(): Long = runCatching { cache?.cacheSpace ?: 0L }.getOrDefault(0L)

    /** Drop every downloaded song (LRU cache stays open, just emptied). */
    fun clear() {
        val c = cache ?: return
        for (key in c.keys) {
            runCatching { c.removeResource(key) }
        }
    }
}

/**
 * Foreground service that owns the player. Because it is a MediaSessionService, Android gives us
 * the notification, lock-screen controls, Bluetooth / headset buttons and Android Auto for free,
 * and music keeps playing when the app is closed.
 */
class PlaybackService : MediaSessionService() {
    companion object {
    private const val ACTION_TOGGLE_LIKE = "com.rustym.mobile.action.TOGGLE_LIKE"
    private const val ACTION_ROUTE_TO_DEVICE = "com.rustym.mobile.action.ROUTE_TO_DEVICE"
    private const val ACTION_CROSSFADE_NEXT = "com.rustym.mobile.action.CROSSFADE_NEXT"
    private const val ACTION_SKIP_SILENCE = "com.rustym.mobile.action.SKIP_SILENCE"
}
    private var session: MediaSession? = null
    private lateinit var library: Library
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    // ---- crossfade ----
    // The session player M jumps to the next item immediately (silent); a shadow
    // player S carries the outgoing track's tail. Over the fade window M's volume
    // ramps up while S's ramps down, so the two songs overlap like premium players.
    private var renderersFactory: androidx.media3.exoplayer.RenderersFactory? = null
    private var mediaSourceFactory: androidx.media3.exoplayer.source.MediaSource.Factory? = null
    private var crossfadePlayer: ExoPlayer? = null
    @Volatile private var crossfadeActive = false
    @Volatile private var crossfadeInternalSeek = false
    private val silenceProcessors = mutableListOf<SilenceSkippingAudioProcessor>()
    private val favoritesListener = android.content.SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        if (key == "favorites") refreshLikeButton()
    }

    private fun refreshLikeButton() {
        val currentId = session?.player?.currentMediaItem?.mediaId ?: return
        val liked = library.favorites().any { it.id == currentId }
        val command = SessionCommand(ACTION_TOGGLE_LIKE, Bundle.EMPTY)
        val button = CommandButton.Builder(CommandButton.ICON_UNDEFINED)
            .setIconResId(if (liked) R.drawable.ic_like_filled else R.drawable.ic_like_outline)
            .setDisplayName(if (liked) "Unlike" else "Like")
            .setSessionCommand(command)
            .build()
        session?.setCustomLayout(listOf(button))
    }

    override fun onCreate() {
        super.onCreate()

        val http = DefaultHttpDataSource.Factory()
            .setUserAgent(Yt.USER_AGENT)
            .setAllowCrossProtocolRedirects(true)
            .setConnectTimeoutMs(15_000)
            .setReadTimeoutMs(20_000)

        // Items carry a "youtube.com/watch?v=ID" placeholder; resolve it to a real stream URL just in time.
        val resolver = object : ResolvingDataSource.Resolver {
            override fun resolveDataSpec(dataSpec: DataSpec): DataSpec {
                val uri = dataSpec.uri
                val host = uri.host.orEmpty()
                if (host.endsWith("youtube.com") && uri.path == "/watch") {
                    val id = uri.getQueryParameter("v") ?: throw IOException("Bad track link")
                    return dataSpec.withUri(Uri.parse(Yt.audioUrl(id)))
                }
                return dataSpec
            }
        }
        val resolvedUpstream = DefaultDataSource.Factory(this, ResolvingDataSource.Factory(http, resolver))
        AudioCache.initialize(this, resolvedUpstream)
        val dataSources = AudioCache.dataSourceFactory()
        mediaSourceFactory = DefaultMediaSourceFactory(dataSources)
        // Standard Media3 audio pipeline with the 8D rotation processor in the sink chain.
        renderersFactory = object : DefaultRenderersFactory(this) {
            override fun buildAudioSink(
                context: android.content.Context,
                enableFloatOutput: Boolean,
                enableAudioTrackPlaybackParams: Boolean,
            ): AudioSink? {
                return DefaultAudioSink.Builder(context)
                    .setEnableFloatOutput(enableFloatOutput)
                    .setEnableAudioTrackPlaybackParams(enableAudioTrackPlaybackParams)
                    .setAudioProcessors(
                        arrayOf(
                            // Skip quiet intros / outros / long gaps. Disabled unless the
                            // user turns on "Skip silence"; applied live by enabling it.
                            SilenceSkippingAudioProcessor().apply {
                                setEnabled(skipSilenceEnabled())
                                silenceProcessors.add(this)
                            },
                            EightDAudioProcessor(),
                        )
                    )
                    .build()
            }
        }
        val player = ExoPlayer.Builder(this, renderersFactory!!)
            .setMediaSourceFactory(mediaSourceFactory!!)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                    .setUsage(C.USAGE_MEDIA)
                    .build(),
                /* handleAudioFocus = */ true,
            )
            .setHandleAudioBecomingNoisy(true) // pause when headphones are unplugged
            .setWakeMode(C.WAKE_MODE_NETWORK)  // keep CPU + Wi-Fi awake while streaming with the screen off
            .build()

        // A track that cannot be played (removed, region-locked, expired link) must not stop the queue.
        player.addListener(object : Player.Listener {
            override fun onPositionDiscontinuity(
                positionInfo: androidx.media3.common.Player.PositionInfo,
                newPositionInfo: androidx.media3.common.Player.PositionInfo,
                reason: Int,
            ) {
                // A manual seek/skip mid-fade hands control back to the main player.
                // (The seek inside startCrossfade itself is flagged so it's ignored here.)
                if (crossfadeActive && !crossfadeInternalSeek) cancelCrossfade(player as? ExoPlayer)
            }
            override fun onMediaItemTransition(mediaItem: androidx.media3.common.MediaItem?, reason: Int) {
                refreshLikeButton()
                prefetchNext(player)
                updateWidget(player)
            }
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                updateWidget(player)
            }
            override fun onTimelineChanged(timeline: androidx.media3.common.Timeline, reason: Int) {
                prefetchNext(player)
            }
            override fun onPlayerError(error: PlaybackException) {
                if (player.hasNextMediaItem()) {
                    player.seekToNextMediaItem()
                    player.prepare()
                    player.play()
                }
            }
            override fun onAudioSessionIdChanged(audioSessionId: Int) {
                AudioFx.attach(audioSessionId)
            }
        })
        AudioFx.init(this)
        AudioFx.attach(player.audioSessionId)

        // Crossfade ticker: watches for tracks approaching their end every 200ms.
        serviceScope.launch(Dispatchers.Main) {
            while (true) {
                kotlinx.coroutines.delay(200)
                (session?.player as? ExoPlayer)?.let { maybeCrossfade(it) }
            }
        }

        val likeCommand = SessionCommand(ACTION_TOGGLE_LIKE, Bundle.EMPTY)
        library = Library(this)
        getSharedPreferences("library", MODE_PRIVATE).registerOnSharedPreferenceChangeListener(favoritesListener)
        val callback = object : MediaSession.Callback {
            override fun onConnect(
                session: MediaSession,
                controller: MediaSession.ControllerInfo,
            ): MediaSession.ConnectionResult {
                val commands = MediaSession.ConnectionResult.DEFAULT_SESSION_COMMANDS
                    .buildUpon().add(likeCommand)
                    .add(SessionCommand(ACTION_ROUTE_TO_DEVICE, Bundle.EMPTY))
                    .add(SessionCommand(ACTION_CROSSFADE_NEXT, Bundle.EMPTY))
                    .add(SessionCommand(ACTION_SKIP_SILENCE, Bundle.EMPTY)).build()
                return MediaSession.ConnectionResult.AcceptedResultBuilder(session)
                    .setAvailableSessionCommands(commands)
                    .build()
            }

            override fun onCustomCommand(
                session: MediaSession,
                controller: MediaSession.ControllerInfo,
                customCommand: SessionCommand,
                args: Bundle,
            ): ListenableFuture<SessionResult> {
                if (customCommand.customAction == ACTION_TOGGLE_LIKE) {
                    session.player.currentMediaItem?.let { item ->
                        val track = item.toTrack()
                        val currentlyLiked = library.favorites().any { it.id == track.id }
                        if (track.isLocal) {
                            library.toggleFavorite(track)
                        } else if (Auth.loggedIn) {
                            serviceScope.launch {
                                runCatching {
                                    com.rustym.mobile.data.Ytm.setLike(track.id, !currentlyLiked)
                                    val remote = com.rustym.mobile.data.Ytm.liked()
                                    library.replaceFavorites(remote + library.favorites().filter { it.isLocal })
                                }
                            }
                        }
                    }
                    return Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
                }
                if (customCommand.customAction == ACTION_CROSSFADE_NEXT) {
                    // Manual "next" uses the same true crossfade as natural track end.
                    val p = session.player as? ExoPlayer
                    if (p != null && crossfadeSeconds() > 0 && p.hasNextMediaItem()) {
                        if (crossfadeActive) cancelCrossfade(p)
                        startCrossfade(p)
                    } else {
                        p?.seekToNextMediaItem()
                    }
                    return Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
                }
                if (customCommand.customAction == ACTION_SKIP_SILENCE) {
                    val on = args.getBoolean("enabled", false)
                    getSharedPreferences("rustym_settings", MODE_PRIVATE).edit()
                        .putBoolean("skip_silence", on).apply()
                    toggleSkipSilence(on)
                    return Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
                }
                if (customCommand.customAction == ACTION_ROUTE_TO_DEVICE) {
                    // The audio sink lives on ExoPlayer, so routing requests come through the session.
                    val id = args.getInt("id", -1)
                    val type = args.getInt("type", -1)
                    runCatching {
                        val am = getSystemService(AUDIO_SERVICE) as android.media.AudioManager
                        val device = if (id == -1) null else
                            am.getDevices(android.media.AudioManager.GET_DEVICES_OUTPUTS)
                                .firstOrNull { it.id == id && it.type == type }
                        (session.player as? ExoPlayer)?.setPreferredAudioDevice(device)
                    }
                    return Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
                }
                return Futures.immediateFuture(SessionResult(SessionResult.RESULT_ERROR_NOT_SUPPORTED))
            }
        }
        val likeButton = CommandButton.Builder(CommandButton.ICON_UNDEFINED)
            .setIconResId(R.drawable.ic_like_outline)
            .setDisplayName("Like")
            .setSessionCommand(likeCommand)
            .build()
        session = MediaSession.Builder(this, player)
            .setCallback(callback)
            .build()
        session?.setCustomLayout(listOf(likeButton))
        refreshLikeButton()
        updateWidget(player)
    }

    /** Paint the home-screen widget with the current song / play state. */
    private fun updateWidget(player: Player) {
        // ExoPlayer may only be touched on the thread it was created on (main); read state here,
        // then hand the values to the IO scope which decodes the artwork.
        val track = runCatching { player.currentMediaItem?.toTrack() }.getOrNull()
        val playing = runCatching { player.isPlaying }.getOrDefault(false)
        serviceScope.launch {
            PlayerWidget.update(this@PlaybackService, track, playing)
        }
    }

    /** Widget transport buttons arrive here as start-service intents. */
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val p = session?.player
        when (intent?.action) {
            PlayerWidget.ACTION_PLAY_PAUSE -> {
                if (p != null) {
                    if (p.isPlaying) p.pause() else p.play()
                    updateWidget(p)
                }
            }
            PlayerWidget.ACTION_NEXT -> {
                if (p != null && p.hasNextMediaItem()) {
                    if (crossfadeActive) cancelCrossfade(p as? ExoPlayer)
                    p.seekToNextMediaItem()
                    if (!p.isPlaying) p.play()
                    updateWidget(p)
                }
            }
            PlayerWidget.ACTION_PREV -> {
                if (p != null) {
                    if (crossfadeActive) cancelCrossfade(p as? ExoPlayer)
                    p.seekToPreviousMediaItem()
                    if (!p.isPlaying) p.play()
                    updateWidget(p)
                }
            }
            PlayerWidget.ACTION_REFRESH -> p?.let { updateWidget(it) }
        }
        return super.onStartCommand(intent, flags, startId)
    }

    private val SEEK_CANCEL_COMMANDS = setOf(
        Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM,
        Player.COMMAND_SEEK_TO_NEXT,
        Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM,
        Player.COMMAND_SEEK_TO_PREVIOUS,
        Player.COMMAND_SEEK_TO_DEFAULT_POSITION,
        Player.COMMAND_SEEK_BACK,
        Player.COMMAND_SEEK_FORWARD,
        Player.COMMAND_SEEK_IN_CURRENT_MEDIA_ITEM,
        Player.COMMAND_PLAY_PAUSE,
    )

    private fun crossfadeSeconds(): Int =
        getSharedPreferences("rustym_settings", MODE_PRIVATE).getInt("crossfade_seconds", 0)

    private fun skipSilenceEnabled(): Boolean =
        getSharedPreferences("rustym_settings", MODE_PRIVATE).getBoolean("skip_silence", true)

    /** Flip skip-silence on every audio sink (each player has its own). A seek flushes
     * the sink so the new state applies immediately. */
    private fun toggleSkipSilence(on: Boolean) {
        silenceProcessors.forEach { runCatching { it.setEnabled(on) } }
        session?.player?.let { p -> runCatching { p.seekTo(p.currentPosition) } }
    }

    /** Start a crossfade when the current track is within the fade window of its end. */
    private fun maybeCrossfade(p: ExoPlayer) {
        val secs = crossfadeSeconds()
        if (secs <= 0) {
            if (crossfadeActive) cancelCrossfade(p)
            return
        }
        if (crossfadeActive || !p.hasNextMediaItem() || !p.isPlaying) return
        val dur = p.duration
        if (dur == C.TIME_UNSET || dur <= 0) return
        val remaining = dur - p.currentPosition
        if (remaining > 0 && remaining <= secs * 1000L) startCrossfade(p)
    }

    /** Overlap the outgoing track's tail (shadow player) with the incoming track (main player). */
    private fun startCrossfade(p: ExoPlayer) {
        if (crossfadeActive || !p.hasNextMediaItem()) return
        val outgoing = p.currentMediaItem ?: return
        val startPos = p.currentPosition
        val dur = p.duration
        if (dur == C.TIME_UNSET || dur <= 0) return
        val remaining = (dur - startPos).coerceAtLeast(0L)
        val ms = (crossfadeSeconds() * 1000L).coerceIn(500L, 10_000L)
        // Never fade longer than the track has left - a dry tail mid-fade is worse
        // than a shorter blend.
        val fadeMs = minOf(ms, remaining)
        if (fadeMs < 400L) {
            cancelCrossfade(p)
            p.seekToNextMediaItem()
            return
        }
        crossfadeActive = true
        android.util.Log.d("RustymCrossfade", "start fade=${fadeMs}ms from pos=$startPos")
        val shadow = ExoPlayer.Builder(this, renderersFactory!!)
            .setMediaSourceFactory(mediaSourceFactory!!)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                    .setUsage(C.USAGE_MEDIA)
                    .build(),
                /* handleAudioFocus = */ false, // never let the tail steal focus from the live player
            )
            .setHandleAudioBecomingNoisy(false)
            .setWakeMode(C.WAKE_MODE_NETWORK)
            .build()
        crossfadePlayer = shadow
        shadow.setMediaItem(outgoing)
        shadow.seekTo(startPos)
        shadow.volume = 1f
        shadow.prepare()
        shadow.playWhenReady = true
        // The queue moves to the next item at once (UI, lyrics, stats update now);
        // its audio stays silent until the ramp brings it up.
        p.volume = 0f
        crossfadeInternalSeek = true
        p.seekToNextMediaItem()
        crossfadeInternalSeek = false
        serviceScope.launch(Dispatchers.Main) {
            val steps = 24
            try {
                for (i in 1..steps) {
                    kotlinx.coroutines.delay(fadeMs / steps)
                    if (crossfadePlayer !== shadow) return@launch
                    val f = i.toFloat() / steps
                    p.volume = f
                    shadow.volume = 1f - f
                    // NOTE: isPlaying is false while the incoming track buffers; the
                    // real "user paused" signal is playWhenReady.
                    if (!p.playWhenReady) {
                        android.util.Log.d("RustymCrossfade", "aborted: paused")
                        return@launch
                    }
                }
                android.util.Log.d("RustymCrossfade", "done")
            } finally {
                p.volume = 1f
                shadow.stop()
                shadow.release()
                if (crossfadePlayer === shadow) crossfadePlayer = null
                crossfadeActive = false
            }
        }
    }

    /** Hand control back to the main player and drop the fading tail. */
    private fun cancelCrossfade(p: ExoPlayer?) {
        val shadow = crossfadePlayer
        crossfadePlayer = null
        crossfadeActive = false
        if (shadow != null) runCatching { shadow.stop(); shadow.release() }
        p?.volume = 1f
    }

    private fun prefetchNext(player: Player) {
        val start = player.currentMediaItemIndex.coerceAtLeast(0) + 1
        val tracks = (start until minOf(player.mediaItemCount, start + 7)).mapNotNull {
            runCatching { player.getMediaItemAt(it).toTrack() }.getOrNull()
        }
        tracks.forEach { track -> serviceScope.launch { AudioCache.prefetch(track) } }
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = session

    override fun onTaskRemoved(rootIntent: android.content.Intent?) {
        val p = session?.player
        if (p == null || !p.playWhenReady || p.mediaItemCount == 0) stopSelf()
    }

    override fun onDestroy() {
        AudioFx.release()
        cancelCrossfade(session?.player as? ExoPlayer)
        serviceScope.coroutineContext[kotlinx.coroutines.Job]?.cancel()
        getSharedPreferences("library", MODE_PRIVATE).unregisterOnSharedPreferenceChangeListener(favoritesListener)
        session?.run {
            player.release()
            release()
        }
        session = null
        super.onDestroy()
    }
}
