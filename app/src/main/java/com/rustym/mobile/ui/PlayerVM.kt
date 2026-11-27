package com.rustym.mobile.ui

import android.app.Application
import android.content.ComponentName
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import android.webkit.CookieManager
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.rustym.mobile.data.Account
import com.rustym.mobile.data.Auth
import com.rustym.mobile.data.Library
import com.rustym.mobile.data.PlaylistCard
import com.rustym.mobile.data.Ytm
import com.rustym.mobile.data.Yt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import com.rustym.mobile.data.Track
import com.rustym.mobile.data.SpotifyImport
import com.rustym.mobile.data.toMediaItem
import com.rustym.mobile.data.toTrack
import com.rustym.mobile.playback.PlaybackService
import com.rustym.mobile.playback.AudioCache
import androidx.palette.graphics.Palette as AndroidPalette
import android.graphics.BitmapFactory
import androidx.compose.ui.graphics.Color
import java.net.URL
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.HttpUrl
import org.json.JSONObject
import org.json.JSONArray
import java.io.IOException
import java.util.concurrent.TimeUnit

/** Connects the UI to PlaybackService and exposes the player state to Compose. */
class PlayerVM(app: Application) : AndroidViewModel(app) {
    private val library = Library(app)
    private val libraryPrefs = app.getSharedPreferences("library", android.content.Context.MODE_PRIVATE)
    private val favoritesListener = android.content.SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        if (key == "favorites") favorites = library.favorites()
    }
    private var controller: MediaController? = null
    private var pending: (() -> Unit)? = null

    var current by mutableStateOf<Track?>(null); private set
    var isPlaying by mutableStateOf(false); private set
    var isLoading by mutableStateOf(false); private set
    var shuffle by mutableStateOf(false); private set
    var repeatMode by mutableIntStateOf(Player.REPEAT_MODE_OFF); private set
    var queue by mutableStateOf<List<Track>>(emptyList()); private set
    var queueIndex by mutableIntStateOf(0); private set
    var favorites by mutableStateOf(library.favorites()); private set
    var recent by mutableStateOf(library.recent()); private set
    var message by mutableStateOf<String?>(null)
    var playbackSpeed by mutableStateOf(1f); private set
    var sleepTimerMinutes by mutableIntStateOf(0); private set
    var lyricsText by mutableStateOf(""); private set
    var lyricsLoading by mutableStateOf(false); private set
    var lyricsNotFound by mutableStateOf(false); private set
    var lyricsSource by mutableStateOf(""); private set
    private var lyricsJob: Job? = null
    private val lyricsHttp = OkHttpClient.Builder()
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(12, TimeUnit.SECONDS)
        .callTimeout(15, TimeUnit.SECONDS)
        .build()
    private val noLyricsMarker = "__RUSTYM_NO_SYNCED_LYRICS__"
    var crossfadeSeconds by mutableIntStateOf(0); private set
    private var sleepJob: Job? = null
    private val settings = app.getSharedPreferences("rustym_settings", android.content.Context.MODE_PRIVATE)
    private val lyricsPrefs = app.getSharedPreferences("rustym_lyrics", android.content.Context.MODE_PRIVATE)
    private val statsPrefs = app.getSharedPreferences("rustym_stats", android.content.Context.MODE_PRIVATE)

    // ---- crossfade UI state: an on/off switch plus a fade-length slider ----
    var crossfadeEnabled by mutableStateOf(
        settings.getBoolean("crossfade_enabled", settings.getInt("crossfade_seconds", 0) > 0)
    ); private set
    var crossfadeValue by mutableIntStateOf(
        settings.getInt("crossfade_value", maxOf(settings.getInt("crossfade_seconds", 0), 4))
    ); private set

    private fun applyCrossfade() {
        crossfadeSeconds = if (crossfadeEnabled) crossfadeValue.coerceIn(1, 8) else 0
        settings.edit().putInt("crossfade_seconds", crossfadeSeconds).apply()
    }
    fun updateCrossfadeEnabled(on: Boolean) {
        crossfadeEnabled = on
        settings.edit().putBoolean("crossfade_enabled", on).apply()
        applyCrossfade()
    }
    fun setCrossfadeValue(seconds: Int) {
        crossfadeValue = seconds.coerceIn(1, 8)
        settings.edit().putInt("crossfade_value", crossfadeValue).apply()
        if (crossfadeEnabled) applyCrossfade()
    }

    /** Lyrics on/off: when off, nothing is fetched or shown on the Now Playing screen. */
    var lyricsEnabled by mutableStateOf(settings.getBoolean("lyrics_enabled", true)); private set
    fun updateLyricsEnabled(enabled: Boolean) {
        lyricsEnabled = enabled
        settings.edit().putBoolean("lyrics_enabled", enabled).apply()
        if (!enabled) {
            lyricsJob?.cancel()
            lyricsText = ""
            lyricsSource = ""
            lyricsLoading = false
            lyricsNotFound = false
        } else {
            current?.let { loadLyrics(it) }
        }
    }
    private val playCounts = mutableMapOf<String, Int>()
    private val statsTracks = mutableMapOf<String, Track>()
    var totalListeningMs by mutableStateOf(statsPrefs.getLong("listening_ms", 0L)); private set
    var topPlayed by mutableStateOf<List<Pair<Track, Int>>>(emptyList()); private set
    var trackedSongCount by mutableIntStateOf(0); private set
    private var smartQueueForTrack: String? = null

    // ---- Google account / YouTube Music library ----
    var account by mutableStateOf<Account?>(null); private set
    var liked by mutableStateOf<List<Track>?>(null); private set
    var playlists by mutableStateOf<List<PlaylistCard>?>(null); private set
    var openedPlaylist by mutableStateOf<PlaylistCard?>(null); private set
    var openedTracks by mutableStateOf<List<Track>?>(null); private set
    var ytLoading by mutableStateOf(false); private set
    var ytError by mutableStateOf<String?>(null); private set

    private val listener = object : Player.Listener {
        override fun onEvents(player: Player, events: Player.Events) {
            refresh(rebuildQueue = events.contains(Player.EVENT_TIMELINE_CHANGED))
        }

        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            mediaItem?.let {
                val track = it.toTrack()
                recent = library.addRecent(track)
                recordTrackPlayed(track)
                loadArtworkAccent(track.thumb)
                maybeExtendSmartQueue(track)
            }
        }

        override fun onPlayerError(error: PlaybackException) {
            message = "Couldn't play that track - skipping"
        }
    }

    init {
        libraryPrefs.registerOnSharedPreferenceChangeListener(favoritesListener)
        // The session loads off-thread; this may fire during startup or a moment later.
        Auth.onReady {
            if (Auth.loggedIn) {
                account = Account("Signed in", "")
                viewModelScope.launch {
                    runCatching { withContext(Dispatchers.IO) { Ytm.accountInfo() } }
                        .onSuccess { account = it }
                        .onFailure { if (it.message.orEmpty().contains("sign in again")) signOut() }
                }
            }
        }
        playbackSpeed = settings.getFloat("playback_speed", 1f)
        crossfadeSeconds = settings.getInt("crossfade_seconds", 0)
        runCatching {
            val saved = JSONArray(statsPrefs.getString("play_counts", "[]"))
            for (i in 0 until saved.length()) {
                val item = saved.getJSONObject(i)
                val id = item.getString("id")
                playCounts[id] = item.optInt("count")
                if (item.has("title")) {
                    statsTracks[id] = Track(id, item.optString("title"), item.optString("artist"), item.optString("thumb"), item.optLong("dur"), item.optString("local"))
                }
            }
            val known = (recent + queue + listOfNotNull(current)).associateBy { it.id }
            statsTracks.putAll(known)
            refreshTopPlayed()
        }
        viewModelScope.launch {
            while (true) {
                delay(15_000)
                if (isPlaying) {
                    totalListeningMs += 15_000L
                    statsPrefs.edit().putLong("listening_ms", totalListeningMs).apply()
                }
            }
        }
        val ctx = getApplication<Application>()
        val token = SessionToken(ctx, ComponentName(ctx, PlaybackService::class.java))
        val future = MediaController.Builder(ctx, token).buildAsync()
        future.addListener({
            val c = runCatching { future.get() }.getOrNull() ?: return@addListener
            controller = c
            c.addListener(listener)
            c.setPlaybackSpeed(playbackSpeed)
            refresh(rebuildQueue = true)
            pending?.invoke()
            pending = null
        }, ContextCompat.getMainExecutor(ctx))
    }

    private fun refresh(rebuildQueue: Boolean) {
        val c = controller ?: return
        isPlaying = c.isPlaying
        isLoading = c.playbackState == Player.STATE_BUFFERING
        shuffle = c.shuffleModeEnabled
        repeatMode = c.repeatMode
        val oldTrackId = current?.id
        current = c.currentMediaItem?.toTrack()
        current?.let {
            if (it.id != oldTrackId) {
                loadArtworkAccent(it.thumb)
                loadLyrics(it)
            }
        }
        queueIndex = c.currentMediaItemIndex
        if (rebuildQueue) queue = (0 until c.mediaItemCount).map { c.getMediaItemAt(it).toTrack() }
    }

    private fun recordTrackPlayed(track: Track) {
        statsTracks[track.id] = track
        playCounts[track.id] = (playCounts[track.id] ?: 0) + 1
        val saved = JSONArray()
        playCounts.forEach { (id, count) ->
            val item = statsTracks[id]
            saved.put(JSONObject().put("id", id).put("count", count)
                .put("title", item?.title.orEmpty()).put("artist", item?.artist.orEmpty())
                .put("thumb", item?.thumb.orEmpty()).put("dur", item?.durationSec ?: 0L)
                .put("local", item?.local.orEmpty()))
        }
        statsPrefs.edit().putString("play_counts", saved.toString()).apply()
        refreshTopPlayed()
    }

    private fun refreshTopPlayed() {
        trackedSongCount = playCounts.size
        val known = (statsTracks.values + recent + queue + listOfNotNull(current)).associateBy { it.id }
        topPlayed = playCounts.entries.sortedByDescending { it.value }.take(10).mapNotNull { (id, count) ->
            known[id]?.let { it to count }
        }
    }

    /** Add a few artist-related tracks when the current queue is nearly exhausted. */
    private fun maybeExtendSmartQueue(track: Track) {
        val c = controller ?: return
        if (track.isLocal || track.artist.isBlank() || c.mediaItemCount - c.currentMediaItemIndex > 2) return
        if (smartQueueForTrack == track.id) return
        smartQueueForTrack = track.id
        viewModelScope.launch {
            val suggestions = withContext(Dispatchers.IO) {
                runCatching { Yt.search("${track.artist} songs") }.getOrDefault(emptyList())
            }
            val active = controller ?: return@launch
            val existing = (0 until active.mediaItemCount).map { active.getMediaItemAt(it).mediaId }.toSet()
            val additions = suggestions.filter { !it.isLocal && it.id !in existing && it.id != track.id }.take(5)
            if (additions.isNotEmpty()) {
                active.addMediaItems(additions.map { it.toMediaItem() })
                message = "Added ${additions.size} similar songs to your queue"
                queue = (0 until active.mediaItemCount).map { active.getMediaItemAt(it).toTrack() }
            }
        }
    }

    /** Apply the artwork colour of the last played song at cold start. */
    fun applyLastPlayedArtworkColor(url: String?) {
        if (!url.isNullOrBlank()) loadArtworkAccent(url)
    }

    private fun loadArtworkAccent(url: String) {
        if (url.isBlank()) return
        viewModelScope.launch {
            val color = withContext(Dispatchers.IO) {
                runCatching {
                    val connection = URL(url).openConnection().apply {
                        connectTimeout = 5000
                        readTimeout = 5000
                    }
                    connection.getInputStream().use { input ->
                        val bitmap = BitmapFactory.decodeStream(input) ?: return@runCatching null
                        val palette = AndroidPalette.from(bitmap).generate()
                        palette.vibrantSwatch?.rgb ?: palette.dominantSwatch?.rgb
                    }
                }.getOrNull()
            }
            if (color != null) ThemeStore.applyArtworkAccent(Color(color))
        }
    }

    // ---- commands ----

    /** Replace the queue with [tracks] and start at [index]. */
    fun play(tracks: List<Track>, index: Int) {
        // Warm the next seven tracks immediately; the service also maintains this window as the queue moves.
        prefetchTracks(tracks.drop((index + 1).coerceAtMost(tracks.size)).take(7))
        val go = {
            controller?.run {
                setMediaItems(tracks.map { it.toMediaItem() }, index, 0L)
                prepare()
                play()
            }
            Unit
        }
        if (controller == null) pending = go else go()
    }

    fun playOne(t: Track) = play(listOf(t), 0)

    fun prefetchTracks(tracks: List<Track>) {
        if (tracks.isEmpty()) return
        viewModelScope.launch(Dispatchers.IO) { tracks.distinctBy { it.id }.forEach { AudioCache.prefetch(it) } }
    }

    fun addToQueue(t: Track) {
        controller?.addMediaItem(t.toMediaItem())
        prefetchTracks(listOf(t))
        message = "Added to queue"
    }

    fun togglePlay() {
        val c = controller ?: return
        if (c.isPlaying) c.pause() else c.play()
    }

        /** Route audio to a specific output device (null = follow the system). */
    fun routeAudioTo(device: android.media.AudioDeviceInfo?) {
        val c = controller ?: return
        val args = android.os.Bundle()
        args.putInt("id", device?.id ?: -1)
        args.putInt("type", device?.type ?: -1)
        runCatching {
            c.sendCustomCommand(
                androidx.media3.session.SessionCommand(
                    "com.rustym.mobile.action.ROUTE_TO_DEVICE",
                    android.os.Bundle.EMPTY,
                ),
                args,
            )
        }
    }

    fun next() {
        val c = controller ?: return
        // With crossfade enabled, "next" is a true overlap handled by the service;
        // the outgoing tail keeps playing while the new track fades in.
        if (crossfadeSeconds > 0 && c.hasNextMediaItem()) {
            val sent = runCatching {
                c.sendCustomCommand(
                    androidx.media3.session.SessionCommand("com.rustym.mobile.action.CROSSFADE_NEXT", android.os.Bundle.EMPTY),
                    android.os.Bundle.EMPTY,
                )
            }.isSuccess
            if (sent) return
        }
        c.seekToNextMediaItem()
    }

    fun updatePlaybackSpeed(speed: Float) {
        playbackSpeed = speed.coerceIn(0.5f, 2f)
        settings.edit().putFloat("playback_speed", playbackSpeed).apply()
        controller?.setPlaybackSpeed(playbackSpeed)
    }

    /** Skip quiet intros / outros / long gaps between sections. Default on. */
    var skipSilence by mutableStateOf(settings.getBoolean("skip_silence", true)); private set
    fun updateSkipSilence(on: Boolean) {
        skipSilence = on
        settings.edit().putBoolean("skip_silence", on).apply()
        controller?.let {
            runCatching {
                it.sendCustomCommand(
                    androidx.media3.session.SessionCommand("com.rustym.mobile.action.SKIP_SILENCE", android.os.Bundle.EMPTY),
                    android.os.Bundle().apply { putBoolean("enabled", on) },
                )
            }
        }
    }

    fun setSleepTimer(minutes: Int) {
        sleepJob?.cancel()
        sleepTimerMinutes = minutes.coerceAtLeast(0)
        if (sleepTimerMinutes > 0) {
            sleepJob = viewModelScope.launch {
                delay(sleepTimerMinutes * 60_000L)
                controller?.pause()
                sleepTimerMinutes = 0
            }
        }
    }

    /** Which screen Rustym opens on: 0 Home, 1 Search, 2 Device, 3 Library. */
    var startTab by mutableIntStateOf(settings.getInt("start_tab", 0)); private set
    fun chooseStartTab(index: Int) {
        startTab = index.coerceIn(0, 3)
        settings.edit().putInt("start_tab", startTab).apply()
    }

    /** Wipe every cached lyric; the current track is refetched so the screen stays populated. */
    fun clearLyricsCache() {
        lyricsJob?.cancel()
        lyricsPrefs.edit().clear().apply()
        lyricsText = ""
        lyricsSource = ""
        lyricsNotFound = false
        current?.let { loadLyrics(it) }
    }

    /** How many lyrics are cached on this device and roughly how much space they use. */
    fun lyricsStats(): Pair<Int, Long> {
        val all = lyricsPrefs.all
        val bytes = all.entries.sumOf { (it.key.toByteArray().size + ((it.value as? String)?.toByteArray()?.size ?: 0)).toLong() }
        return all.size to bytes
    }

    /** Erase per-song play counts and total listening time. */
    fun resetPlayStats() {
        playCounts.clear()
        statsTracks.clear()
        topPlayed = emptyList()
        trackedSongCount = 0
        totalListeningMs = 0L
        statsPrefs.edit().clear().apply()
    }

    fun saveLyrics(text: String) {
        lyricsText = text
        lyricsNotFound = text.isBlank()
        current?.let { lyricsPrefs.edit().putString(it.id, text).apply() }
    }

    /** Lyrics are normalized to LRC where possible so every source shares one renderer. */
    private fun loadLyrics(track: Track) {
        lyricsJob?.cancel()
        if (!lyricsEnabled) return
        val cached = lyricsPrefs.getString(track.id, null)
        lyricsText = cached?.takeUnless { it == noLyricsMarker }?.orEmpty() ?: ""
        lyricsNotFound = cached == noLyricsMarker
        lyricsSource = if (lyricsText.isNotBlank()) "Saved lyrics" else ""
        lyricsLoading = false
        if (!cached.isNullOrBlank()) return
        if (track.title.isBlank() || track.isLocal) { lyricsNotFound = true; return }

        lyricsLoading = true
        val requestedTrackId = track.id
        lyricsJob = viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                var completed = false
                var best: Pair<String, String>? = null
                fun accept(source: String, text: String?, format: String = "lrc") {
                    val value = text?.trim().orEmpty()
                    if (value.isBlank()) return
                    val normalized = when (format.lowercase()) {
                        "ttml", "xml" -> ttmlToLrc(value)
                        "plain" -> value
                        else -> value
                    }
                    if (normalized.isNotBlank() && (best == null || scoreLyrics(normalized) > scoreLyrics(best!!.first))) {
                        best = normalized to source
                    }
                }
                fun request(url: HttpUrl): JSONObject? = runCatching {
                    val req = Request.Builder().url(url).header("User-Agent", "Rustym Android/2.1 (lyrics)").get().build()
                    lyricsHttp.newCall(req).execute().use { response ->
                        if (response.isSuccessful || response.code == 404) completed = true
                        if (response.isSuccessful) JSONObject(response.body?.string().orEmpty()) else null
                    }
                }.getOrNull()

                // 1. Unison community catalogue: exact YouTube video ID, no key required.
                request(HttpUrl.Builder().scheme("https").host("unison.betterlyrics.org").addPathSegment("lyrics")
                    .addQueryParameter("v", track.id).build())?.let { json ->
                    if (json.optBoolean("success")) {
                        val data = json.optJSONObject("data")
                        accept("Unison", data?.optString("lyrics"), data?.optString("format", "lrc") ?: "lrc")
                    }
                }
                // 2. Better Lyrics / Kugou: public LRC endpoint, broad line-synced coverage.
                request(HttpUrl.Builder().scheme("https").host("api.betterlyrics.org").addPathSegments("kugou/getLyrics")
                    .addQueryParameter("s", track.title).addQueryParameter("a", track.artist)
                    .addQueryParameter("d", track.durationSec.toString()).build())?.let { json ->
                    accept("Better Lyrics · KuGou", json.optString("lyrics"))
                }
                // 3. LRCLIB: exact metadata match, synced preferred over plain.
                request(HttpUrl.Builder().scheme("https").host("lrclib.net").addPathSegments("api/get")
                    .addQueryParameter("track_name", track.title).addQueryParameter("artist_name", track.artist)
                    .apply { track.durationSec.takeIf { it in 1L..3600L }?.let { addQueryParameter("duration", it.toString()) } }.build())?.let { json ->
                    accept("LRCLIB", json.optString("syncedLyrics"))
                    accept("LRCLIB", json.optString("plainLyrics"), "plain")
                }
                // 4. NetEase Cloud Music: strong Asian catalogue, decent Western coverage.
                val neteaseSearch = request(HttpUrl.Builder().scheme("https").host("music.163.com").addPathSegments("api/search/get/web")
                    .addQueryParameter("s", "${track.title} ${track.artist}".trim()).addQueryParameter("type", "1").addQueryParameter("limit", "8").build())
                val neteaseSongs = neteaseSearch?.optJSONObject("result")?.optJSONArray("songs")
                if (neteaseSongs != null && neteaseSongs.length() > 0) {
                    var song: JSONObject? = null
                    var bestScore = Long.MIN_VALUE
                    for (i in 0 until neteaseSongs.length()) {
                        val c = neteaseSongs.optJSONObject(i) ?: continue
                        var score = 0L
                        val name = c.optString("name")
                        if (name.contains(track.title, ignoreCase = true) || track.title.contains(name, ignoreCase = true)) score += 1_000_000L
                        val d = c.optLong("duration", 0L) / 1000L
                        if (track.durationSec in 1L..3600L && d in 1L..3600L) score += 1000L - kotlin.math.abs(d - track.durationSec).coerceAtMost(999L)
                        val artists = c.optJSONArray("artists")
                        if (artists != null) {
                            for (j in 0 until artists.length()) {
                                val an = artists.optJSONObject(j)?.optString("name").orEmpty()
                                if (an.isNotBlank() && track.artist.contains(an, ignoreCase = true)) score += 400_000L
                            }
                        }
                        if (score > bestScore) { bestScore = score; song = c }
                    }
                    song?.optLong("id", 0L)?.takeIf { it > 0L }?.let { nid ->
                        request(HttpUrl.Builder().scheme("https").host("music.163.com").addPathSegments("api/song/lyric")
                            .addQueryParameter("id", nid.toString()).addQueryParameter("lv", "-1").addQueryParameter("tv", "-1").build())
                            ?.let { json -> accept("NetEase", json.optJSONObject("lrc")?.optString("lyric")) }
                    }
                }
                // 5. KuGou (direct krcs endpoint): the service behind Better Lyrics, queried ourselves.
                val kugouSearch = request(HttpUrl.Builder().scheme("https").host("krcs.kugou.com").addPathSegment("search")
                    .addQueryParameter("keyword", "${track.artist} ${track.title}".trim())
                    .apply { track.durationSec.takeIf { it in 1L..3600L }?.let { addQueryParameter("duration", (it * 1000L).toString()) } }
                    .addQueryParameter("man", "yes").build())
                val kugouList = kugouSearch?.optJSONObject("data")?.optJSONArray("lists")
                if (kugouList != null && kugouList.length() > 0) {
                    var hit: JSONObject? = null
                    var bestScore = Long.MIN_VALUE
                    for (i in 0 until kugouList.length()) {
                        val c = kugouList.optJSONObject(i) ?: continue
                        var score = 0L
                        val name = c.optString("songname", c.optString("song"))
                        if (name.contains(track.title, ignoreCase = true) || track.title.contains(name, ignoreCase = true)) score += 1_000_000L
                        val d = c.optLong("duration", 0L) / 1000L
                        if (track.durationSec in 1L..3600L && d in 1L..3600L) score += 1000L - kotlin.math.abs(d - track.durationSec).coerceAtMost(999L)
                        val singer = c.optString("singername", c.optString("singer"))
                        if (singer.isNotBlank() && track.artist.contains(singer.substringBefore("/"), ignoreCase = true)) score += 400_000L
                        if (score > bestScore) { bestScore = score; hit = c }
                    }
                    hit?.let { h ->
                        val hash = h.optString("hash").takeIf { it.isNotBlank() } ?: return@let
                        request(HttpUrl.Builder().scheme("https").host("krcs.kugou.com").addPathSegment("download")
                            .addQueryParameter("man", "yes").addQueryParameter("fmt", "lrc").addQueryParameter("hash", hash)
                            .addQueryParameter("album_id", h.optString("album_id")).addQueryParameter("crc", h.optString("crc")).build())
                            ?.let { json -> accept("KuGou", json.optJSONObject("content")?.optString("content")) }
                    }
                }
                // 6. lyrics.ovh is plain text only; last-resort fallback.
                if (track.artist.isNotBlank()) {
                    request(HttpUrl.Builder().scheme("https").host("api.lyrics.ovh").addPathSegments("v1")
                        .addPathSegment(track.artist).addPathSegment(track.title).build())?.let { json ->
                        accept("Lyrics.ovh", json.optString("lyrics"), "plain")
                    }
                }
                Triple(best?.first, best?.second.orEmpty(), completed)
            }
            if (current?.id != requestedTrackId) return@launch
            lyricsLoading = false
            val best = result.first
            if (best.isNullOrBlank()) {
                lyricsText = ""; lyricsSource = ""; lyricsNotFound = true
                if (result.third) lyricsPrefs.edit().putString(requestedTrackId, noLyricsMarker).apply()
            } else {
                lyricsText = best; lyricsSource = result.second; lyricsNotFound = false
                lyricsPrefs.edit().putString(requestedTrackId, best).apply()
            }
        }
    }

    private fun scoreLyrics(text: String): Int {
        val synced = Regex("\\[\\d{1,2}:\\d{2}(?:[.:]\\d{1,3})?]").containsMatchIn(text)
        return (if (synced) 1_000_000 else 0) + text.length.coerceAtMost(100_000)
    }

    private fun ttmlToLrc(xml: String): String {
        val p = Regex("(?is)<p\\b([^>]*)>(.*?)</p>")
        val begin = Regex("(?i)begin\\s*=\\s*[\\\"']([^\\\"']+)")
        val tag = Regex("<[^>]+>")
        return p.findAll(xml).mapNotNull { match ->
            val time = begin.find(match.groupValues[1])?.groupValues?.get(1) ?: return@mapNotNull null
            val millis = ttmlTimeMs(time) ?: return@mapNotNull null
            val text = match.groupValues[2].replace(Regex("(?i)<br\\s*/?>"), " ").replace(tag, "")
                .replace("&amp;", "&").replace("&lt;", "<").replace("&gt;", ">").replace("&quot;", "\"").trim()
            if (text.isBlank()) null else "[${millis / 60000}:${((millis % 60000) / 1000).toString().padStart(2, '0')}.${(millis % 1000).toString().padStart(3, '0')}] $text"
        }.joinToString("\n")
    }

    private fun ttmlTimeMs(value: String): Long? = runCatching {
        val parts = value.split(":")
        when (parts.size) {
            3 -> ((parts[0].toDouble() * 3600 + parts[1].toDouble() * 60 + parts[2].toDouble()) * 1000).toLong()
            2 -> ((parts[0].toDouble() * 60 + parts[1].toDouble()) * 1000).toLong()
            else -> null
        }
    }.getOrNull()

    fun syncedLyricLines(): List<Pair<Long, String>> {
        val offset = Regex("(?im)^\\[offset:([+-]?\\d+)]").find(lyricsText)?.groupValues?.get(1)?.toLongOrNull() ?: 0L
        val timestamp = Regex("\\[(\\d{1,2}):(\\d{2})(?:[.:](\\d{1,3}))?]")
        return lyricsText.lineSequence().flatMap { line ->
            val matches = timestamp.findAll(line).toList()
            if (matches.isEmpty()) return@flatMap emptySequence()
            val lyric = timestamp.replace(line, "").trim()
            if (lyric.isBlank()) return@flatMap emptySequence()
            matches.asSequence().map { match ->
                val fraction = match.groupValues[3].padEnd(3, '0').ifEmpty { "0" }.toLong()
                val ms = (match.groupValues[1].toLong() * 60 + match.groupValues[2].toLong()) * 1000 + fraction + offset
                ms.coerceAtLeast(0L) to lyric
            }
        }.sortedBy { it.first }.toList()
    }

    fun activeLyricIndex(positionMs: Long): Int {
        val lines = syncedLyricLines()
        val index = lines.indexOfLast { it.first <= positionMs }
        if (index < 0) return -1
        val next = lines.getOrNull(index + 1)?.first
        // A long instrumental break (including an outro) should not keep the previous lyric on screen.
        val longGap = if (next != null) next - lines[index].first >= 6500L
            else current?.durationSec?.let { it * 1000L - lines[index].first >= 6500L } == true
        if (longGap && positionMs - lines[index].first >= 4200L) return -1
        return index
    }

    fun syncedLyricLine(positionMs: Long): String? = syncedLyricLines().getOrNull(activeLyricIndex(positionMs))?.second
    fun hasSyncedLyrics(): Boolean = syncedLyricLines().isNotEmpty()
    fun plainLyrics(): String = lyricsText.lines().mapNotNull { line ->
        if (line.trim().startsWith("[")) Regex("^\\[[^\\]]+]\\s*").replace(line, "").takeIf { it.isNotBlank() }
        else line.takeIf { it.isNotBlank() }
    }.joinToString("\n")

    // ---- volume via vertical swipe on the album art ----

    /** -1f = never captured yet (the crossfade routine also writes controller volume). */
    var playerVolume by mutableStateOf(-1f); private set

    /** Nudge the player volume by [delta] (0..1 span) and return the new volume. */
    fun nudgeVolume(delta: Float): Float {
        val c = controller ?: return 0f
        if (playerVolume < 0f) playerVolume = c.volume
        playerVolume = (playerVolume + delta).coerceIn(0f, 1f)
        c.volume = playerVolume
        return playerVolume
    }

    fun previous() {
        val c = controller ?: return
        if (c.currentPosition > 3000 || !c.hasPreviousMediaItem()) c.seekTo(0) else c.seekToPreviousMediaItem()
    }

    fun seekTo(ms: Long) { controller?.seekTo(ms) }

    fun skipTo(index: Int) { controller?.seekTo(index, 0L) }

    fun toggleShuffle() { controller?.let { it.shuffleModeEnabled = !it.shuffleModeEnabled } }

    fun cycleRepeat() {
        controller?.let {
            it.repeatMode = when (it.repeatMode) {
                Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
                Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
                else -> Player.REPEAT_MODE_OFF
            }
        }
    }

    fun toggleFavorite(t: Track) {
        val nowLiked = !isFavorite(t.id)
        if (t.isLocal) {
            favorites = library.toggleFavorite(t)
            return
        }
        if (account == null) {
            message = "Sign in to YouTube Music to like songs"
            return
        }
        // Online-song likes are written to YouTube Music only. The local list is a cache
        // of YouTube's Liked Music plus device-local favourites, never a second like store.
        // The heart fills instantly (optimistic update); YouTube is synced in the background
        // and the local cache is restored from storage if the network write fails.
        favorites = if (nowLiked) favorites + t else favorites.filterNot { it.id == t.id }
        viewModelScope.launch {
            runCatching {
                withContext(Dispatchers.IO) {
                    Ytm.setLike(t.id, nowLiked)
                    Ytm.liked()
                }
            }.onSuccess { remoteLiked ->
                liked = remoteLiked
                favorites = library.replaceFavorites(remoteLiked + favorites.filter { it.isLocal })
                if (nowLiked) prefetchTracks(listOf(t))
            }.onFailure {
                favorites = library.favorites()
                message = "Couldn't update YouTube Music likes: ${it.message ?: "network error"}"
            }
        }
    }
    fun isFavorite(id: String) = favorites.any { it.id == id } || liked?.any { it.id == id } == true
    fun clearRecent() { library.clearRecent(); recent = emptyList() }

    fun positionMs(): Long = controller?.currentPosition ?: 0L
    fun durationMs(): Long {
        val d = controller?.duration ?: C.TIME_UNSET
        return if (d == C.TIME_UNSET || d <= 0) (current?.durationSec ?: 0L) * 1000 else d
    }

    // ---- account ----

    fun onSignedIn(a: Account) {
        account = a
        liked = null; playlists = null; openedPlaylist = null; openedTracks = null; ytError = null
        message = "Signed in as ${a.name}"
    }

    fun signOut() {
        Auth.clear()
        runCatching { CookieManager.getInstance().removeAllCookies(null) }
        account = null
        liked = null; playlists = null; openedPlaylist = null; openedTracks = null; ytError = null
    }

    private fun <T> loadYt(block: () -> T, onOk: (T) -> Unit) {
        ytLoading = true
        ytError = null
        viewModelScope.launch {
            runCatching { withContext(Dispatchers.IO) { block() } }
                .onSuccess(onOk)
                .onFailure {
                    ytError = it.message ?: "Couldn't load your library"
                    if (ytError.orEmpty().contains("sign in again")) signOut()
                }
            ytLoading = false
        }
    }

    fun loadLiked(force: Boolean = false) {
        if (account == null || ytLoading || (liked != null && !force)) return
        loadYt({
            var remote = Ytm.liked()
            // One-time migration: older Rustym versions stored online hearts only on-device.
            // Push those cached likes into this signed-in YouTube Music account once.
            val migrationKey = "legacy_likes_migrated_" + (account?.name ?: "account").lowercase().replace(Regex("[^a-z0-9]+"), "_")
            if (!settings.getBoolean(migrationKey, false)) {
                val missing = favorites.filterNot { it.isLocal }.filter { old -> remote.none { it.id == old.id } }
                missing.forEach { Ytm.setLike(it.id, true) }
                settings.edit().putBoolean(migrationKey, true).apply()
                if (missing.isNotEmpty()) remote = Ytm.liked()
            }
            remote
        }) { remote ->
            liked = remote
            favorites = library.replaceFavorites(remote + favorites.filter { it.isLocal })
            // Cache liked tracks in the background. The disk cache is capped and evicts least-recently-used audio.
            prefetchTracks(remote)
        }
    }

    fun loadPlaylists(force: Boolean = false) {
        if (account == null || ytLoading || (playlists != null && !force)) return
        loadYt({ Ytm.libraryPlaylists() }) { playlists = it }
    }

    fun openPlaylist(p: PlaylistCard) {
        if (ytLoading) return
        openedPlaylist = p
        openedTracks = null
        loadYt({ Ytm.playlistTracks(p.browseId) }) { openedTracks = it }
    }

    fun closePlaylist() { openedPlaylist = null; openedTracks = null; ytError = null }

    // ---- Spotify playlist import (local, no sign-in needed) ----

    var localPlaylists by mutableStateOf<List<Pair<String, List<Track>>>>(emptyList())
        private set
    var localOpenedName by mutableStateOf<String?>(null)
        private set
    var localOpenedTracks by mutableStateOf<List<Track>>(emptyList())
        private set
    var importProgress by mutableStateOf<String?>(null)
        private set

    fun loadLocalPlaylists() {
        localPlaylists = library.localPlaylists()
    }

    fun openLocalPlaylist(name: String) {
        localOpenedName = name
        localOpenedTracks = localPlaylists.firstOrNull { it.first == name }?.second.orEmpty()
    }

    fun closeLocalPlaylist() { localOpenedName = null; localOpenedTracks = emptyList() }

    fun deleteLocalPlaylist(name: String) {
        library.deleteLocalPlaylist(name)
        if (localOpenedName == name) closeLocalPlaylist()
        loadLocalPlaylists()
    }

    /** Adds one track to a locally saved playlist, creating it on the fly if needed. */
    fun addToLocalPlaylist(name: String, t: Track) {
        val existing = localPlaylists.firstOrNull { it.first == name }?.second ?: emptyList()
        if (existing.any { it.id == t.id }) { message = "Already in \"$name\""; return }
        library.saveLocalPlaylist(name, existing + listOf(t))
        loadLocalPlaylists()
        message = "Added to \"$name\""
    }

    /**
     * Fetch the Spotify playlist's public track list and match every song on
     * YouTube Music (4 concurrent lookups), then save it as a local playlist.
     */
    fun importSpotifyPlaylist(link: String) {
        if (importProgress != null) return
        viewModelScope.launch {
            try {
                importProgress = "Resolving playlist…"
                val id = SpotifyImport.extractPlaylistId(link)
                    ?: throw IllegalArgumentException("That doesn't look like a Spotify playlist link.")
                val sp = SpotifyImport.fetch(id)
                if (sp.tracks.isEmpty()) throw IllegalStateException("That playlist has no tracks.")

                val wanted = sp.tracks.take(100)
                val total = wanted.size
                val done = java.util.concurrent.atomic.AtomicInteger(0)
                val permits = Semaphore(4)
                val matched: List<Track?> = coroutineScope {
                    wanted.map { t ->
                        async(Dispatchers.IO) {
                            permits.withPermit {
                                val found = runCatching {
                                    Yt.search("${t.title} ${t.artists}").firstOrNull()
                                }.getOrNull()
                                importProgress = "Matching ${done.incrementAndGet()}/$total - ${t.title.take(28)}…"
                                found
                            }
                        }
                    }.awaitAll()
                }
                val tracks: List<Track> = matched.filterNotNull()
                importProgress = null
                if (tracks.isEmpty()) {
                    message = "Couldn't match any songs from that playlist - try another."
                } else {
                    library.saveLocalPlaylist(sp.name, tracks)
                    loadLocalPlaylists()
                    message = "Imported ${tracks.size} of ${sp.tracks.size} songs from '${sp.name}'"
                }
            } catch (e: IllegalArgumentException) {
                importProgress = null
                message = e.message
            } catch (e: Exception) {
                importProgress = null
                message = "Import failed - ${e.message ?: "Spotify could not be reached"}"
            }
        }
    }

    override fun onCleared() {
        controller?.removeListener(listener)
        sleepJob?.cancel()
        controller?.release()
        controller = null
    }
}
