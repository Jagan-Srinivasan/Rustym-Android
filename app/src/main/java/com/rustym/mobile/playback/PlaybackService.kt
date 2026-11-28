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
    private const val ACTION_CROSSFADE_STATE = "com.rustym.mobile.action.CROSSFADE_STATE"
}
    private var session: MediaSession? = null
    private lateinit var library: Library
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    // ---- crossfade ----
    // The session player owns the queue and plays the incoming song from its own 0:00; a shadow
    // player carries the outgoing song's tail. Over the blend the tail ramps down (cos) while the
    // incoming ramps up (sin), so total power stays flat and neither song is ever restarted.
    private var renderersFactory: androidx.media3.exoplayer.RenderersFactory? = null
    private var shadowRenderersFactory: androidx.media3.exoplayer.RenderersFactory? = null
    private var mediaSourceFactory: androidx.media3.exoplayer.source.MediaSource.Factory? = null
    private var crossfadePlayer: ExoPlayer? = null
    @Volatile private var crossfadeActive = false
    @Volatile private var blendProgress = 0f
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
        // The tail player keeps the DSP chain but not silence skipping: skipping would jump the
        // outgoing tail forward and unanchor the blend from the song's real ending. It also keeps
        // its processors out of silenceProcessors, which the main player alone owns.
        shadowRenderersFactory = object : DefaultRenderersFactory(this) {
            override fun buildAudioSink(
                context: android.content.Context,
                enableFloatOutput: Boolean,
                enableAudioTrackPlaybackParams: Boolean,
            ): AudioSink? = DefaultAudioSink.Builder(context)
                .setEnableFloatOutput(enableFloatOutput)
                .setEnableAudioTrackPlaybackParams(enableAudioTrackPlaybackParams)
                .setAudioProcessors(arrayOf(EightDAudioProcessor()))
                .build()
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

            /**
             * A seek during a blend means the listener moved on their own, which makes the running
             * ramp meaningless. Our own hand-over seek is expected and filtered out by index.
             */
            override fun onPositionDiscontinuity(
                oldPosition: Player.PositionInfo,
                newPosition: Player.PositionInfo,
                reason: Int,
            ) {
                if (reason != Player.DISCONTINUITY_REASON_SEEK &&
                    reason != Player.DISCONTINUITY_REASON_SEEK_ADJUSTMENT
                ) return
                if (handoverPending && newPosition.mediaItemIndex == blendInIndex) {
                    handoverPending = false
                    return
                }
                if (crossfadeActive) externalSeek = true
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
                    .add(SessionCommand(ACTION_SKIP_SILENCE, Bundle.EMPTY))
                    .add(SessionCommand(ACTION_CROSSFADE_STATE, Bundle.EMPTY)).build()
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
                        startCrossfade(p, manual = true)
                    } else {
                        p?.seekToNextMediaItem()
                    }
                    return Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
                }
                if (customCommand.customAction == ACTION_CROSSFADE_STATE) {
                    val extras = Bundle().apply { putBoolean("active", crossfadeActive) }
                    return Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS, extras))
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

    /**
     * YouTube stream containers often run a few seconds longer than the song's
     * real (audible) length. The track metadata carries the audible duration -
     * anchor the fade window on it so the blend covers the song's real ending.
     */
    private fun audibleEndMs(p: ExoPlayer, containerMs: Long): Long {
        val sec = runCatching {
            p.currentMediaItem?.mediaMetadata?.extras?.getLong("dur") ?: 0L
        }.getOrDefault(0L)
        return if (sec > 0) minOf(sec * 1000L, containerMs) else containerMs
    }

    // ---- crossfade scheduling ----
    /** How far ahead of the fade window the tail player is built, so no network wait ever sits
     *  inside the audible blend. */
    private val prepLeadMs = 3_500L
    /** Shortest blend worth running; below this the fade trades places with its own artifacts. */
    private val minRampMs = 600L
    /** Longest wait for proof that the tail player is really rendering before audio moves to it. */
    private val handoverConfirmMs = 900L

    @Volatile private var attemptKey = ""
    @Volatile private var baseVolume = 1f
    @Volatile private var blendInIndex = -1
    @Volatile private var handoverPending = false
    @Volatile private var externalSeek = false

    private fun maybeCrossfade(p: ExoPlayer) {
        val secs = crossfadeSeconds()
        if (secs <= 0) {
            if (crossfadeActive) cancelCrossfade(p)
            return
        }
        // While a blend runs, the blend coroutine itself supervises it - no second watcher.
        if (crossfadeActive) return
        if (p.currentPosition < 2000L) attemptKey = ""     // a new song gets one attempt again
        if (!p.hasNextMediaItem() || !p.isPlaying || p.repeatMode == Player.REPEAT_MODE_ONE) return
        val dur = p.duration
        if (dur == C.TIME_UNSET || dur <= 0) return
        val key = "${p.currentMediaItemIndex}:${p.currentMediaItem?.mediaId}"
        if (key == attemptKey) return                      // a skipped fade must not retry every 200ms
        val remaining = audibleEndMs(p, dur) - p.currentPosition
        if (remaining in 1..(secs * 1000L + prepLeadMs)) {
            attemptKey = key
            startCrossfade(p, manual = false)
        }
    }

    /**
     * Crossfade with no hand-off and no silent window.
     *
     * The main player owns the queue, the session and the *incoming* song; the shadow player only
     * ever carries the outgoing song's tail. That ordering matters: the incoming song is started
     * once, at its own 0:00, and never seeked or restarted, so no part of it can be heard twice.
     *
     * The blend is anchored to the song's real (audible) end, not to wall-clock time, so it always
     * finishes exactly when the music does - even when buffering makes it shorter than requested.
     */
    private fun startCrossfade(p: ExoPlayer, manual: Boolean) {
        if (crossfadeActive) return
        val outIndex = p.currentMediaItemIndex
        val inIndex = p.nextMediaItemIndex
        val outItem = p.currentMediaItem
        if (outItem == null || inIndex == C.INDEX_UNSET || inIndex == outIndex) {
            if (manual) p.seekToNextMediaItem()
            return
        }
        val dur = p.duration
        if (dur == C.TIME_UNSET || dur <= 0) {
            if (manual) p.seekToNextMediaItem()
            return
        }
        val posNow = p.currentPosition
        val audibleEnd = audibleEndMs(p, dur)
        val fadeMs = (crossfadeSeconds() * 1000L).coerceIn(500L, 10_000L)
        if (audibleEnd - posNow < minRampMs) {
            if (manual) p.seekToNextMediaItem()
            return
        }
        val fadeStart = if (manual) posNow else maxOf(audibleEnd - fadeMs, posNow)
        val fadeEnd = minOf(audibleEnd, fadeStart + fadeMs)
        baseVolume = p.volume
        val shadow = newShadowPlayer(outItem, fadeStart) ?: run {
            if (manual) p.seekToNextMediaItem()
            return
        }
        crossfadePlayer = shadow
        crossfadeActive = true
        blendProgress = 0f
        blendInIndex = inIndex
        externalSeek = false
        handoverPending = false
        android.util.Log.d(
            "RustymCrossfade",
            "plan window=${fadeStart}..${fadeEnd}ms fade=${fadeMs}ms now=${posNow}ms vol=${baseVolume} manual=$manual",
        )
        serviceScope.launch(Dispatchers.Main) {
            runBlend(p, shadow, outIndex, inIndex, fadeStart, fadeEnd)
        }
    }

    /** The tail carrier: same stream pipeline, no audio focus (it must never interrupt the session),
     *  no silence skipping (it would jump the tail forward and unanchor the blend). */
    private fun newShadowPlayer(
        item: androidx.media3.common.MediaItem,
        startPositionMs: Long,
    ): ExoPlayer? {
        val shadow = runCatching {
            ExoPlayer.Builder(this, shadowRenderersFactory!!)
                .setMediaSourceFactory(mediaSourceFactory!!)
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                        .setUsage(C.USAGE_MEDIA)
                        .build(),
                    /* handleAudioFocus = */ false,
                )
                .setHandleAudioBecomingNoisy(false)
                .setWakeMode(C.WAKE_MODE_NETWORK)
                .build()
        }.getOrNull() ?: return null
        shadow.volume = baseVolume
        shadow.playWhenReady = false
        shadow.setMediaItem(item, startPositionMs)
        shadow.prepare()
        AudioFx.attachAux(shadow.audioSessionId)
        return shadow
    }

    private suspend fun runBlend(
        p: ExoPlayer,
        shadow: ExoPlayer,
        outIndex: Int,
        inIndex: Int,
        fadeStart: Long,
        fadeEnd: Long,
    ) {
        val base = baseVolume
        var blendEnd = fadeEnd

        /** The outgoing song must still be the one the main player holds. */
        fun stillOnTail() = p.currentMediaItemIndex == outIndex

        /** Anything abandoned before audio actually moves over is not a lost fade: allow a retry. */
        fun giveUp() {
            attemptKey = ""
        }

        try {
            // 1. Buffer the tail while the main player is still the audible one.
            var waited = 0L
            while (shadow.playbackState != Player.STATE_READY) {
                if (shadow.playbackState == Player.STATE_IDLE) return giveUp()
                if (!stillOnTail()) return giveUp()
                // Window gone (the song was scrubbed past it): let the queue advance by itself.
                if (fadeEnd - p.currentPosition < minRampMs) return giveUp()
                kotlinx.coroutines.delay(25)
                waited += 25
            }
            android.util.Log.d("RustymCrossfade", "tail ready in ${waited}ms")
            // 2. Hold the shadow parked until the song reaches its own fade window. Starting it
            //    here would play the tail twice, so only the pause state is mirrored.
            while (p.currentPosition < fadeStart) {
                if (!stillOnTail()) return giveUp()
                if (!p.playWhenReady) shadow.playWhenReady = false
                kotlinx.coroutines.delay(15)
            }
            // 3. Hand the tail over. Audio moves to the shadow only once it is *proven* to be
            //    rendering - muting the main player before that is exactly what left a gap.
            val handoverAt = p.currentPosition
            shadow.volume = base
            shadow.playWhenReady = p.playWhenReady
            var confirmed = false
            var probe = 0L
            while (probe < handoverConfirmMs) {
                if (p.currentMediaItemIndex != outIndex) return giveUp()
                if (shadow.isPlaying && shadow.currentPosition > handoverAt + 25L) {
                    confirmed = true
                    break
                }
                kotlinx.coroutines.delay(15)
                probe += 15
            }
            if (!confirmed) {
                android.util.Log.d("RustymCrossfade", "handover unconfirmed: song plays out, no fade")
                return giveUp()
            }
            p.volume = 0f
            handoverPending = true
            p.seekToNextMediaItem()
            if (!p.playWhenReady) p.playWhenReady = true
            android.util.Log.d("RustymCrossfade", "handed over at ${handoverAt}ms, incoming -> idx ${inIndex}")
            // 4. Equal-power blend, clocked by the tail's own media position.
            var anchor = -1L
            while (true) {
                if (crossfadePlayer !== shadow) return            // cancelCrossfade took charge
                if (p.currentMediaItemIndex != inIndex) return     // manual skip or queue edit
                val sp = shadow.currentPosition
                if (externalSeek) {
                    // The new song was scrubbed: close the blend fast instead of cutting it, and
                    // never let the tail be heard over a position it no longer matches.
                    externalSeek = false
                    blendEnd = (sp + 320L).coerceAtLeast(anchor.coerceAtLeast(sp))
                    if (anchor < 0L) anchor = sp
                    android.util.Log.d("RustymCrossfade", "seek during blend: closing over ${blendEnd - sp}ms")
                }
                if (p.playWhenReady && !shadow.playWhenReady) shadow.playWhenReady = true
                if (!p.playWhenReady && shadow.playWhenReady) shadow.playWhenReady = false
                if (anchor < 0L && sp >= blendEnd - 1L) {
                    // The tail is done and the incoming never started: better to let the song play
                    // out than to fade out into a pipeline that has nothing to show for it.
                    android.util.Log.d("RustymCrossfade", "no blend: incoming silent to the end")
                    break
                }
                if (anchor < 0L) {
                    // Begin as soon as the incoming is really sounding, using whatever of the
                    // requested window is left. A short blend beats a silent one, and a blend that
                    // fades into a still-buffering player throws the song's ending away.
                    val room = blendEnd - sp
                    if (p.isPlaying && room > 120L) {
                        anchor = maxOf(sp, blendEnd - minOf(minRampMs, room)).coerceAtMost(blendEnd - 1L)
                        android.util.Log.d(
                            "RustymCrossfade",
                            "blend from ${anchor}ms over ${blendEnd - anchor}ms (requested ${fadeEnd - fadeStart}ms)",
                        )
                    }
                }
                if (anchor >= 0L) {
                    val prog = ((sp - anchor).toFloat() / (blendEnd - anchor).coerceAtLeast(1L))
                        .coerceIn(0f, 1f)
                    blendProgress = prog
                    val angle = (prog * Math.PI / 2.0).toFloat()
                    p.volume = base * kotlin.math.sin(angle)
                    shadow.volume = base * kotlin.math.cos(angle)
                    if (prog >= 1f || shadow.playbackState == Player.STATE_ENDED) break
                }
                kotlinx.coroutines.delay(15)
            }
        } catch (e: Exception) {
            android.util.Log.d("RustymCrossfade", "blend aborted: ${e.message}")
        } finally {
            if (crossfadePlayer === shadow) {
                android.util.Log.d("RustymCrossfade", "blend released")
                crossfadePlayer = null
                blendProgress = 0f
                runCatching { shadow.stop(); shadow.release() }
                AudioFx.releaseAux()
                p.volume = baseVolume
            }
            crossfadeActive = false
            handoverPending = false
            blendInIndex = -1
        }
    }

    /** Drop the tail player and hand volume back to the main player (skip, seek, disable). */
    private fun cancelCrossfade(p: ExoPlayer?) {
        if (!crossfadeActive) return
        android.util.Log.d("RustymCrossfade", "cancelled")
        val shadow = crossfadePlayer
        crossfadePlayer = null
        crossfadeActive = false
        blendProgress = 0f
        blendInIndex = -1
        handoverPending = false
        externalSeek = false
        attemptKey = ""
        if (shadow != null) runCatching { shadow.stop(); shadow.release() }
        AudioFx.releaseAux()
        p?.volume = baseVolume
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
