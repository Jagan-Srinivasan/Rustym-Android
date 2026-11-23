package com.rustym.mobile.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/** Favourites and recently played, stored on the device in SharedPreferences. */
class Library(context: Context) {
    private val prefs = context.getSharedPreferences("library", Context.MODE_PRIVATE)

    fun favorites(): List<Track> = read("favorites")
    fun recent(): List<Track> = read("recent")

    fun replaceFavorites(tracks: List<Track>): List<Track> {
        val unique = tracks.distinctBy { it.id }
        write("favorites", unique)
        return unique
    }

    fun toggleFavorite(t: Track): List<Track> {
        val cur = favorites().toMutableList()
        if (!cur.removeAll { it.id == t.id }) cur.add(0, t)
        write("favorites", cur)
        return cur
    }

    fun addRecent(t: Track): List<Track> {
        val cur = recent().toMutableList()
        cur.removeAll { it.id == t.id }
        cur.add(0, t)
        val out = cur.take(50)
        write("recent", out)
        return out
    }

    fun clearRecent() = write("recent", emptyList())

    private fun read(key: String): List<Track> = runCatching {
        val arr = JSONArray(prefs.getString(key, "[]"))
        (0 until arr.length()).map {
            val o = arr.getJSONObject(it)
            Track(o.getString("id"), o.optString("title"), o.optString("artist"), o.optString("thumb"), o.optLong("dur"), o.optString("local"))
        }
    }.getOrDefault(emptyList())

    private fun write(key: String, list: List<Track>) {
        val arr = JSONArray()
        list.forEach {
            arr.put(JSONObject().put("id", it.id).put("title", it.title).put("artist", it.artist).put("thumb", it.thumb).put("dur", it.durationSec).put("local", it.local))
        }
        prefs.edit().putString(key, arr.toString()).apply()
    }
}
