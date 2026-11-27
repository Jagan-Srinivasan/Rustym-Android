package com.rustym.mobile

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray

/**
 * GitHub release lookup shared by the startup update check, the What's-new dialog
 * and the Settings "Check for updates" card. No auth: the repo is public.
 */
object Updater {
    private const val API = "https://api.github.com/repos/Jagan-Srinivasan/Rustym-Android/releases"

    data class Release(val tag: String, val url: String, val notes: String, val apkUrl: String = "")

    /** The newest releases, newest first. Empty list on any network/parse failure. */
    suspend fun releases(count: Int = 8): List<Release> = withContext(Dispatchers.IO) {
        runCatching {
            val c = (java.net.URL("$API?per_page=$count").openConnection() as java.net.HttpURLConnection).apply {
                connectTimeout = 8000
                readTimeout = 8000
                setRequestProperty("Accept", "application/vnd.github+json")
            }
            val body = if (c.responseCode in 200..299) c.inputStream.bufferedReader().use { it.readText() } else ""
            c.disconnect()
            val arr = JSONArray(body)
            (0 until arr.length()).map { i ->
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
            }
        }.getOrDefault(emptyList())
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
