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
    fun cachedSongs(): Int = runCatching {
        val c = cache ?: return@runCatching 0
        c.keys().count { runCatching { c.isCached(it, 0, Long.MAX_VALUE) }.getOrDefault(false) }
    }.getOrDefault(0)

    /** Total bytes held by the offline cache. */
    fun cachedBytes(): Long = runCatching { cache?.cacheSpace }.getOrDefault(0L)

    /** Drop every downloaded song (LRU cache stays open, just emptied). */
    fun clear() {
        val c = cache ?: return
        c.keys().forEach { runCatching { c.removeResource(it) } }
    }
}

/**
 * Foreground service that owns the player. Because it is a MediaSessionService, Android gives us
 * the notification, lock-screen controls, Bluetooth / headset buttons and Android Auto for free,
 * and music keeps playing when the app is closed.
 */
class PlaybackService : MediaSessionService() {
    companion object { private const val ACTION_TOGGLE_LIKE = "com.rustym.mobile.action.TOGGLE_LIKE" }
    private var session: MediaSession? = null
    private lateinit var library: Library
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
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

        // Standard Media3 audio pipeline; the former 8D PCM processor has been removed.
        val player = ExoPlayer.Builder(this)
            .setMediaSourceFactory(DefaultMediaSourceFactory(dataSources))
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
        })

        val likeCommand = SessionCommand(ACTION_TOGGLE_LIKE, Bundle.EMPTY)
        library = Library(this)
        getSharedPreferences("library", MODE_PRIVATE).registerOnSharedPreferenceChangeListener(favoritesListener)
        val callback = object : MediaSession.Callback {
            override fun onConnect(
                session: MediaSession,
                controller: MediaSession.ControllerInfo,
            ): MediaSession.ConnectionResult {
                val commands = MediaSession.ConnectionResult.DEFAULT_SESSION_COMMANDS
                    .buildUpon().add(likeCommand).build()
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
        serviceScope.launch {
            val track = runCatching { player.currentMediaItem?.toTrack() }.getOrNull()
            PlayerWidget.update(this@PlaybackService, track, player.isPlaying)
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
                    p.seekToNextMediaItem()
                    if (!p.isPlaying) p.play()
                    updateWidget(p)
                }
            }
            PlayerWidget.ACTION_PREV -> {
                if (p != null) {
                    p.seekToPreviousMediaItem()
                    if (!p.isPlaying) p.play()
                    updateWidget(p)
                }
            }
            PlayerWidget.ACTION_REFRESH -> p?.let { updateWidget(it) }
        }
        return super.onStartCommand(intent, flags, startId)
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
