package com.rustym.mobile.data

import okhttp3.OkHttpClient
import okhttp3.RequestBody.Companion.toRequestBody
import org.schabi.newpipe.extractor.MediaFormat
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.downloader.Downloader
import org.schabi.newpipe.extractor.downloader.Request
import org.schabi.newpipe.extractor.downloader.Response
import org.schabi.newpipe.extractor.exceptions.ReCaptchaException
import org.schabi.newpipe.extractor.search.SearchInfo
import org.schabi.newpipe.extractor.services.youtube.linkHandler.YoutubeSearchQueryHandlerFactory
import org.schabi.newpipe.extractor.stream.AudioStream
import org.schabi.newpipe.extractor.stream.DeliveryMethod
import org.schabi.newpipe.extractor.stream.StreamInfo
import org.schabi.newpipe.extractor.stream.StreamInfoItem
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

/** Talks to YouTube / YouTube Music through NewPipeExtractor. Every function blocks: call off the main thread. */
object Yt {
    const val USER_AGENT =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:128.0) Gecko/20100101 Firefox/128.0"

    private val http = OkHttpClient.Builder()
        .readTimeout(30, TimeUnit.SECONDS)
        .connectTimeout(20, TimeUnit.SECONDS)
        .build()

    private class OkDownloader : Downloader() {
        override fun execute(request: Request): Response {
            val body = request.dataToSend()?.toRequestBody()
            val builder = okhttp3.Request.Builder()
                .method(request.httpMethod(), body)
                .url(request.url())
                .addHeader("User-Agent", USER_AGENT)
            request.headers()?.forEach { (name, values) ->
                builder.removeHeader(name)
                values.forEach { builder.addHeader(name, it) }
            }
            return http.newCall(builder.build()).execute().use { resp ->
                if (resp.code == 429) throw ReCaptchaException("YouTube asked for a captcha (HTTP 429)", request.url())
                Response(
                    resp.code,
                    resp.message,
                    resp.headers.toMultimap(),
                    resp.body?.string(),
                    resp.request.url.toString(),
                )
            }
        }
    }

    @Volatile private var ready = false

    fun init() {
        if (ready) return
        synchronized(this) {
            if (ready) return
            org.schabi.newpipe.extractor.NewPipe.init(OkDownloader())
            ready = true
        }
    }

    private val idRegex = Regex("[?&]v=([A-Za-z0-9_-]{11})")

    private fun toTrack(item: StreamInfoItem): Track? {
        val id = idRegex.find(item.url)?.groupValues?.get(1) ?: return null
        val thumb = item.thumbnails.maxByOrNull { it.height }?.url.orEmpty()
        return Track(id, item.name.orEmpty(), item.uploaderName.orEmpty().removeSuffix(" - Topic"), thumb, item.duration.coerceAtLeast(0))
    }

    private fun searchWith(query: String, filter: String): List<Track> {
        val service = ServiceList.YouTube
        val handler = service.searchQHFactory.fromQuery(query, listOf(filter), "")
        val info = SearchInfo.getInfo(service, handler)
        return info.relatedItems.filterIsInstance<StreamInfoItem>().mapNotNull(::toTrack)
    }

    /** Songs from YouTube Music; falls back to normal YouTube videos if the music filter finds nothing. */
    fun search(query: String): List<Track> {
        init()
        val q = query.trim()
        if (q.isEmpty()) return emptyList()
        val songs = runCatching { searchWith(q, YoutubeSearchQueryHandlerFactory.MUSIC_SONGS) }.getOrDefault(emptyList())
        if (songs.isNotEmpty()) return songs
        return searchWith(q, YoutubeSearchQueryHandlerFactory.VIDEOS).filter { it.durationSec in 30..900 }
    }

    // ---- stream URLs ----

    private class Cached(val url: String, val at: Long)

    private val streamCache = ConcurrentHashMap<String, Cached>()
    private const val TTL_MS = 3 * 60 * 60 * 1000L // YouTube links live ~6 h; refresh earlier

    /** Direct audio URL for a video id (prefers AAC/M4A, highest bitrate). */
    @Throws(IOException::class)
    fun audioUrl(videoId: String): String {
        init()
        val now = System.currentTimeMillis()
        streamCache[videoId]?.let { if (now - it.at < TTL_MS) return it.url }
        val info = try {
            StreamInfo.getInfo(WATCH_URL + videoId)
        } catch (e: Exception) {
            throw IOException("Could not load stream info: ${e.message}", e)
        }
        val usable = info.audioStreams.filter { it.isUrl && it.deliveryMethod == DeliveryMethod.PROGRESSIVE_HTTP }
        val best = usable.maxWithOrNull(compareBy<AudioStream>({ it.format == MediaFormat.M4A }, { it.averageBitrate }))
            ?: throw IOException("No playable audio stream for this track")
        streamCache[videoId] = Cached(best.content, now)
        return best.content
    }
}
