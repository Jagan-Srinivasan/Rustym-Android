package com.rustym.mobile

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray

/**
 * GitHub release lookup shared by the startup update check, the What's-new dialog
 * and the Settings "Check for updates" card. No auth: the repo is public.
 */
object Updater {
    private const val REPO = "Jagan-Srinivasan/Rustym-Android"
    private const val API = "https://api.github.com/repos/$REPO/releases"

    data class Release(val tag: String, val url: String, val notes: String, val apkUrl: String = "")

    /** The newest releases, newest first. Empty list on any network/parse failure. */
    suspend fun releases(count: Int = 8): List<Release> = withContext(Dispatchers.IO) {
        // api.github.com is slow or outright unreachable on some mobile networks.
        // Try it twice, then fall back to a tiny redirect probe on github.com -
        // a different host that usually works when the API endpoint doesn't.
        val first = runCatching { apiReleases(count) }.getOrDefault(emptyList())
        if (first.isNotEmpty()) return@withContext first
        val second = runCatching { apiReleases(count) }.getOrDefault(emptyList())
        if (second.isNotEmpty()) return@withContext second
        runCatching { redirectLatest() }.getOrDefault(emptyList())
    }

    private fun apiReleases(count: Int): List<Release> {
        val c = (java.net.URL("$API?per_page=$count").openConnection() as java.net.HttpURLConnection).apply {
            connectTimeout = 15000
            readTimeout = 15000
            setRequestProperty("Accept", "application/vnd.github+json")
            setRequestProperty("User-Agent", "Rustym-Android")
        }
        val body = if (c.responseCode in 200..299) c.inputStream.bufferedReader().use { it.readText() } else ""
        c.disconnect()
        if (body.isBlank()) return emptyList()
        val arr = JSONArray(body)
        return (0 until arr.length()).map { i ->
            val r = arr.getJSONObject(i)
            val assets = r.optJSONArray("assets")
            var apk = ""
            if (assets != null) {
                for (a in 0 until assets.length()) {
                    val asset = assets.getJSONObject(a)
                    if (asset.optString("name").endsWith(".apk", ignoreCase = true)) {
                        apk = asset.optString("browser_download_url"); break
                    }
                }
            }
            Release(r.optString("tag_name"), r.optString("html_url"), r.optString("body"), apk)
        }.filter { !it.tag.contains("-rc", ignoreCase = true) } // pre-release tests are not updates
    }

    /**
     * github.com/<repo>/releases/latest 302-redirects to /tag/vX.Y.Z - parse the
     * tag straight from the Location header, no JSON and no API host needed.
     * The APK asset URL follows the repo's release convention (Rustym.apk).
     */
    private fun redirectLatest(): List<Release> {
        val c = (java.net.URL("https://github.com/$REPO/releases/latest").openConnection() as java.net.HttpURLConnection).apply {
            instanceFollowRedirects = false
            connectTimeout = 15000
            readTimeout = 15000
            setRequestProperty("User-Agent", "Rustym-Android")
        }
        val loc = if (c.responseCode in 300..399) c.getHeaderField("Location") else null
        c.disconnect()
        val tag = loc?.substringAfterLast('/')
            ?.takeIf { Regex("^v?[0-9]+([.][0-9]+)+$").matches(it) }
            ?: return emptyList()
        return listOf(
            Release(
                tag = tag,
                url = "https://github.com/$REPO/releases/tag/$tag",
                notes = "",
                apkUrl = "https://github.com/$REPO/releases/download/$tag/Rustym.apk",
            )
        )
    }

    /** True when [remoteTag] (e.g. "v1.1.0") is strictly newer than [currentVersion]. */
    fun isNewer(remoteTag: String, currentVersion: String): Boolean {
        fun parts(s: String) = s.removePrefix("v").removePrefix("V")
            .split('.').map { it.filter { ch -> ch.isDigit() }.toIntOrNull() ?: 0 }
        val r = parts(remoteTag)
        val c = parts(currentVersion)
        for (i in 0 until 3) {
            val rv = r.getOrNull(i) ?: 0
            val cv = c.getOrNull(i) ?: 0
            if (rv != cv) return rv > cv
        }
        return false
    }

    /** The release whose tag matches the installed version ("v1.0.2" / "1.0.2"). */
    fun forVersion(list: List<Release>, versionName: String): Release? =
        list.firstOrNull {
            it.tag.removePrefix("v").removePrefix("V").equals(versionName, ignoreCase = true)
        }
}
