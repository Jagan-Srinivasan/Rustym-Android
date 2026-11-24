package com.rustym.mobile.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.URL
import org.json.JSONArray
import org.json.JSONObject

/**
 * Reads a Spotify playlist through its public embed page
 * (https://open.spotify.com/embed/playlist/<id>) which exposes the track list -
 * title, artists and duration - without any login or API key.
 * Tracks are then matched on YouTube Music by title + artist.
 */
object SpotifyImport {
    data class SpotifyTrack(val title: String, val artists: String, val durationMs: Long)
    data class SpotifyPlaylist(val name: String, val subtitle: String, val tracks: List<SpotifyTrack>)

    /** Accepts full share links, spotify: URIs, or a bare playlist ID. */
    fun extractPlaylistId(input: String): String? {
        val s = input.trim()
        Regex("playlist/([A-Za-z0-9]{16,})").find(s)?.let { return it.groupValues[1] }
        Regex("spotify:playlist:([A-Za-z0-9]{16,})").find(s)?.let { return it.groupValues[1] }
        return if (s.matches(Regex("[A-Za-z0-9]{16,}"))) s else null
    }

    suspend fun fetch(playlistId: String): SpotifyPlaylist = withContext(Dispatchers.IO) {
        val connection = URL("https://open.spotify.com/embed/playlist/$playlistId").openConnection()
        connection.setRequestProperty(
            "User-Agent",
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0 Safari/537.36",
        )
        connection.connectTimeout = 10_000
        connection.readTimeout = 10_000
        val html = connection.getInputStream().bufferedReader().use { it.readText() }

        val marker = "<script id=\"__NEXT_DATA__\" type=\"application/json\">"
        val json = html.substringAfter(marker).substringBefore("</script>")
        if (json.isBlank() || json == html) throw IllegalStateException("Spotify embed page could not be read")
        val entity = JSONObject(json)
            .getJSONObject("props")
            .getJSONObject("pageProps")
            .getJSONObject("state")
            .getJSONObject("data")
            .getJSONObject("entity")

        val raw = entity.optJSONArray("trackList") ?: JSONArray()
        val tracks = (0 until raw.length()).mapNotNull { i ->
            val t = raw.optJSONObject(i) ?: return@mapNotNull null
            val title = t.optString("title").trim()
            if (title.isEmpty()) null
            else SpotifyTrack(title, t.optString("subtitle").replace(0x00A0.toChar(), ' ').trim(), t.optLong("duration"))
        }
        SpotifyPlaylist(
            name = entity.optString("title").ifBlank { "Spotify playlist" },
            subtitle = entity.optString("subtitle"),
            tracks = tracks,
        )
    }
}
