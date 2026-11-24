package com.rustym.mobile.data

import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.URLEncoder
import java.security.MessageDigest
import java.time.LocalDate
import java.time.ZoneOffset
import java.util.Locale
import java.util.concurrent.TimeUnit

data class Account(val name: String, val photo: String)
data class PlaylistCard(val browseId: String, val title: String, val sub: String, val thumb: String)

/**
 * Signed-in YouTube Music (InnerTube) client. Requests are signed exactly like the music.youtube.com
 * web app signs them: the session cookies plus a SAPISIDHASH header. Blocking - call from a background thread.
 */
object Ytm {
    private const val ORIGIN = "https://music.youtube.com"

    private val http = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    private fun clientVersion(): String {
        val d = LocalDate.now(ZoneOffset.UTC).minusDays(1)
        return "1.%04d%02d%02d.01.00".format(d.year, d.monthValue, d.dayOfMonth)
    }

    private fun region(): String = Locale.getDefault().country.ifEmpty { "US" }

    private fun sha1(s: String): String =
        MessageDigest.getInstance("SHA-1").digest(s.toByteArray()).joinToString("") { "%02x".format(it) }

    private fun authorization(vals: Map<String, String>, ts: Long): String {
        val parts = ArrayList<String>()
        for ((scheme, key) in listOf(
            "SAPISIDHASH" to "SAPISID",
            "SAPISID1PHASH" to "__Secure-1PAPISID",
            "SAPISID3PHASH" to "__Secure-3PAPISID",
        )) {
            var v = vals[key]
            if (v == null && scheme == "SAPISIDHASH") v = vals["__Secure-3PAPISID"]
            if (v != null) parts += "$scheme ${ts}_${sha1("$ts $v $ORIGIN")}"
        }
        return parts.joinToString(" ")
    }

    // ------------------------------------------------------------ transport

    @Throws(IOException::class)
    fun call(endpoint: String, extra: JSONObject = JSONObject(), query: String = "", authRequired: Boolean = false): JSONObject {
        val vals = Auth.cookies()
        val logged = Auth.loggedIn
        if (authRequired && !logged) throw IOException("Not signed in - please sign in again")
        val ver = clientVersion()
        val body = JSONObject().put(
            "context",
            JSONObject().put(
                "client",
                JSONObject().put("clientName", "WEB_REMIX").put("clientVersion", ver).put("hl", "en").put("gl", region()),
            ),
        )
        extra.keys().forEach { body.put(it, extra.get(it)) }

        val rb = Request.Builder()
            .url("$ORIGIN/youtubei/v1/$endpoint?prettyPrint=false$query")
            .post(body.toString().toRequestBody("application/json".toMediaType()))
            .header("User-Agent", Yt.USER_AGENT)
            .header("Accept", "*/*")
            .header("Origin", ORIGIN)
            .header("X-Origin", ORIGIN)
            .header("Referer", "$ORIGIN/")
            .header("X-Youtube-Client-Name", "67")
            .header("X-Youtube-Client-Version", ver)
        if (logged) {
            val cookie = vals.entries.joinToString("; ") { "${it.key}=${it.value}" } +
                if (vals.containsKey("SOCS")) "" else "; SOCS=CAI"
            rb.header("Cookie", cookie)
                .header("Authorization", authorization(vals, System.currentTimeMillis() / 1000))
                .header("X-Goog-AuthUser", "0")
        } else {
            rb.header("Cookie", "SOCS=CAI")
        }

        return http.newCall(rb.build()).execute().use { resp ->
            if (!resp.isSuccessful) {
                if ((resp.code == 401 || resp.code == 403) && logged) {
                    throw IOException("YouTube rejected the saved session (HTTP ${resp.code}) - please sign in again")
                }
                throw IOException("YouTube Music replied HTTP ${resp.code}")
            }
            if (logged) mergeSetCookies(resp.headers("Set-Cookie"))
            val text = resp.body?.string().orEmpty()
            try { JSONObject(text) } catch (e: Exception) { throw IOException("YouTube Music sent an unreadable reply") }
        }
    }

    /** YouTube rotates *PSIDTS / *PSIDCC cookies through Set-Cookie; keep the stored session fresh. */
    private fun mergeSetCookies(headers: List<String>) {
        if (headers.isEmpty()) return
        val cur = LinkedHashMap(Auth.cookies())
        var changed = false
        for (h in headers) {
            val parts = h.split(";")
            val kv = parts[0].trim()
            val i = kv.indexOf('=')
            if (i <= 0) continue
            val name = kv.substring(0, i)
            val value = kv.substring(i + 1)
            val expired = value.isEmpty() || parts.any { it.trim().equals("Max-Age=0", ignoreCase = true) }
            if (expired) {
                if (cur.remove(name) != null) changed = true
            } else if (cur[name] != value) {
                cur[name] = value
                changed = true
            }
        }
        if (changed) Auth.save(cur)
    }

    // ------------------------------------------------------------ JSON helpers

    private fun walk(n: Any?, out: MutableList<JSONObject>) {
        when (n) {
            is JSONObject -> { out.add(n); n.keys().forEach { walk(n.opt(it), out) } }
            is JSONArray -> for (i in 0 until n.length()) walk(n.opt(i), out)
        }
    }

    private fun findAll(n: Any?, key: String): List<Any> {
        val all = ArrayList<JSONObject>()
        walk(n, all)
        return all.mapNotNull { o -> o.opt(key)?.takeIf { it != JSONObject.NULL } }
    }

    private fun ptr(n: Any?, vararg path: Any): Any? {
        var cur = n
        for (p in path) {
            cur = when {
                p is String && cur is JSONObject -> cur.opt(p)
                p is Int && cur is JSONArray -> cur.opt(p)
                else -> return null
            }
            if (cur == null || cur == JSONObject.NULL) return null
        }
        return cur
    }

    private fun runsText(node: Any?): String {
        val o = node as? JSONObject ?: return ""
        val runs = o.optJSONArray("runs")
        if (runs != null) return (0 until runs.length()).joinToString("") { runs.optJSONObject(it)?.optString("text").orEmpty() }
        return o.optString("simpleText")
    }

    private fun thumbOf(n: Any?): String {
        for (t in findAll(n, "thumbnails")) {
            val a = t as? JSONArray ?: continue
            if (a.length() > 0) a.optJSONObject(a.length() - 1)?.optString("url")?.takeIf { it.isNotEmpty() }?.let { return it }
        }
        return ""
    }

    private fun parseDur(t: String): Long? {
        val s = t.trim()
        if (!s.contains(':')) return null
        var sec = 0L
        for (p in s.split(':')) sec = sec * 60 + (p.trim().toLongOrNull() ?: return null)
        return sec
    }

    private val labels = setOf("Song", "Video", "Single", "Album", "EP", "Playlist", "Artist", "Podcast", "Episode")

    private fun artistFromPlain(plain: String): String {
        for (seg in plain.split(" • ")) {
            val s = seg.trim()
            if (s.isEmpty() || s in labels || parseDur(s) != null) continue
            if (s.endsWith(" plays") || s.endsWith(" views") || s.endsWith("subscribers")) continue
            return s
        }
        return ""
    }

    private fun colRuns(r: JSONObject, i: Int): List<JSONObject> {
        val a = ptr(r, "flexColumns", i, "musicResponsiveListItemFlexColumnRenderer", "text", "runs") as? JSONArray ?: return emptyList()
        return (0 until a.length()).mapNotNull { a.optJSONObject(it) }
    }

    private fun navBrowse(r: JSONObject): String? = ptr(r, "navigationEndpoint", "browseEndpoint", "browseId") as? String

    private fun firstStr(n: Any?, key: String): String? {
        val all = ArrayList<JSONObject>()
        walk(n, all)
        for (o in all) o.optString(key).takeIf { it.isNotEmpty() }?.let { return it }
        return null
    }

    private fun trackFromRow(r: JSONObject): Track? {
        if (r.opt("musicItemRendererDisplayPolicy")?.toString()?.contains("GREY_OUT") == true) return null
        if (navBrowse(r) != null) return null // album / artist / playlist row
        val vid = (ptr(r, "playlistItemData", "videoId") as? String) ?: firstStr(r, "videoId") ?: return null
        val title = colRuns(r, 0).joinToString("") { it.optString("text") }
        if (title.isEmpty()) return null

        val artists = ArrayList<String>()
        var dur: Long? = null
        val plain = StringBuilder()
        for (run in colRuns(r, 1)) {
            val text = run.optString("text")
            plain.append(text)
            val bid = ptr(run, "navigationEndpoint", "browseEndpoint", "browseId") as? String ?: ""
            if (bid.startsWith("UC")) artists += text else parseDur(text)?.let { dur = it }
        }
        val artist = if (artists.isEmpty()) artistFromPlain(plain.toString()) else artists.joinToString(", ")
        if (dur == null) {
            dur = parseDur(runsText(ptr(r, "fixedColumns", 0, "musicResponsiveListItemFixedColumnRenderer", "text")))
        }
        return Track(vid, title, artist, thumbOf(r), dur ?: 0L)
    }

    private fun tracksIn(n: Any?): List<Track> =
        findAll(n, "musicResponsiveListItemRenderer").mapNotNull { (it as? JSONObject)?.let(::trackFromRow) }

    private fun nextToken(resp: Any?): String? {
        val all = ArrayList<JSONObject>()
        walk(resp, all)
        for (d in all) {
            (ptr(d, "nextContinuationData", "continuation") as? String)?.let { return it }
            (ptr(d, "continuationCommand", "token") as? String)?.let { return it }
        }
        return null
    }

    private fun continuation(tok: String): JSONObject {
        val q = URLEncoder.encode(tok, "UTF-8")
        return call("browse", query = "&ctoken=$q&continuation=$q&type=next")
    }

    private fun checkSignedIn(resp: JSONObject) {
        val params = ptr(resp, "responseContext", "serviceTrackingParams") as? JSONArray ?: return
        for (i in 0 until params.length()) {
            val kv = params.optJSONObject(i)?.optJSONArray("params") ?: continue
            for (j in 0 until kv.length()) {
                val e = kv.optJSONObject(j) ?: continue
                if (e.optString("key") == "logged_in" && e.optString("value") != "1") {
                    throw IOException("YouTube says this session is signed out - please sign in again")
                }
            }
        }
    }

    /** Live search suggestions from YouTube Music (music/get_search_suggestions). */
    fun searchSuggestions(input: String): List<String> {
        if (input.isBlank()) return emptyList()
        val json = call("music/get_search_suggestions", JSONObject().put("input", input.trim()))
        val out = ArrayList<String>()
        val contents = json.optJSONArray("contents") ?: return out
        for (i in 0 until contents.length()) {
            val section = contents.optJSONObject(i)?.optJSONObject("searchSuggestionsSectionRenderer") ?: continue
            val items = section.optJSONArray("contents") ?: continue
            for (j in 0 until items.length()) {
                val s = items.optJSONObject(j)?.optJSONObject("searchSuggestionRenderer") ?: continue
                val runs = s.optJSONObject("suggestion")?.optJSONObject("suggestionRenderer")
                    ?.optJSONObject("text")?.optJSONArray("runs") ?: continue
                val text = (0 until runs.length()).joinToString("") { k -> runs.optJSONObject(k)?.optString("text").orEmpty() }.trim()
                if (text.isNotBlank() && !out.contains(text)) out.add(text)
            }
        }
        return out
    }

    // ------------------------------------------------------------ public API

    /** Who is signed in. Also proves the cookies are accepted. */
    @Throws(IOException::class)
    fun accountInfo(): Account {
        val resp = call("account/account_menu", authRequired = true)
        checkSignedIn(resp)
        val h = findAll(resp, "activeAccountHeaderRenderer").firstOrNull() as? JSONObject
            ?: throw IOException("Could not read the account - sign-in may be incomplete")
        val name = runsText(h.optJSONObject("accountName")).ifEmpty { "Signed in" }
        return Account(name, thumbOf(h))
    }

    private fun fetchTracks(browseId: String, limit: Int): List<Track> {
        val resp = call("browse", JSONObject().put("browseId", browseId), authRequired = true)
        var tracks = tracksIn(resp)
        if (tracks.isEmpty()) checkSignedIn(resp)
        var cur = resp
        for (page in 0 until 12) {
            if (tracks.size >= limit) break
            val tok = nextToken(cur) ?: break
            val more = continuation(tok)
            val t = tracksIn(more)
            if (t.isEmpty()) break
            tracks = tracks + t
            cur = more
        }
        return tracks.distinctBy { it.id }
    }

    @Throws(IOException::class)
    fun liked(): List<Track> = fetchTracks("VLLM", 2000)

    @Throws(IOException::class)
    fun playlistTracks(browseId: String): List<Track> = fetchTracks(browseId, 2000)

    @Throws(IOException::class)
    fun libraryPlaylists(): List<PlaylistCard> {
        val resp = call("browse", JSONObject().put("browseId", "FEmusic_liked_playlists"), authRequired = true)
        fun cards(n: Any?): List<PlaylistCard> = findAll(n, "musicTwoRowItemRenderer").mapNotNull { any ->
            val r = any as? JSONObject ?: return@mapNotNull null
            val b = navBrowse(r) ?: return@mapNotNull null
            if (!b.startsWith("VL") || b == "VLLM" || b == "VLSE") return@mapNotNull null
            val title = runsText(r.optJSONObject("title"))
            if (title.isEmpty()) return@mapNotNull null
            PlaylistCard(b, title, runsText(r.optJSONObject("subtitle")), thumbOf(r))
        }
        var out = cards(resp)
        if (out.isEmpty()) checkSignedIn(resp)
        var cur = resp
        for (page in 0 until 5) {
            val tok = nextToken(cur) ?: break
            val more = continuation(tok)
            val known = out.map { it.browseId }.toSet()
            val fresh = cards(more).filter { it.browseId !in known }
            if (fresh.isEmpty()) break
            out = out + fresh
            cur = more
        }
        return out
    }

    /** Like / unlike a song on the signed-in account (keeps YouTube Music's "Liked songs" in sync). */
    @Throws(IOException::class)
    fun setLike(videoId: String, like: Boolean) {
        call(if (like) "like/like" else "like/removelike", JSONObject().put("target", JSONObject().put("videoId", videoId)), authRequired = true)
    }
}
