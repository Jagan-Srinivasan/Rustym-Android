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

    // ---- locally imported playlists (e.g. imported from a Spotify share link)

    /** Imported playlists as (name, tracks) pairs, newest first. */
    fun localPlaylists(): List<Pair<String, List<Track>>> = runCatching {
        val arr = JSONArray(prefs.getString("local_playlists", "[]"))
        (0 until arr.length()).map { i ->
            val o = arr.getJSONObject(i)
            o.getString("name") to readTrackArray(o.getJSONArray("tracks"))
        }
    }.getOrDefault(emptyList())

    fun saveLocalPlaylist(name: String, tracks: List<Track>) {
        val list = localPlaylists().toMutableList()
        list.removeAll { it.first == name }
        list.add(0, name to tracks)
        writeLocalPlaylists(list)
    }

    fun deleteLocalPlaylist(name: String) {
        writeLocalPlaylists(localPlaylists().filterNot { it.first == name })
    }

    private fun writeLocalPlaylists(list: List<Pair<String, List<Track>>>) {
        val arr = JSONArray()
        list.forEach { (name, tracks) ->
            val o = JSONObject().put("name", name)
            val tarr = JSONArray()
            tracks.forEach { t ->
                tarr.put(JSONObject()
                    .put("id", t.id).put("title", t.title).put("artist", t.artist)
                    .put("thumb", t.thumb).put("dur", t.durationSec).put("local", t.local))
            }
            o.put("tracks", tarr)
            arr.put(o)
        }
        prefs.edit().putString("local_playlists", arr.toString()).apply()
    }

    private fun readTrackArray(arr: JSONArray): List<Track> = (0 until arr.length()).mapNotNull {
        val o = arr.optJSONObject(it) ?: return@mapNotNull null
        Track(o.getString("id"), o.optString("title"), o.optString("artist"), o.optString("thumb"), o.optLong("dur"), o.optString("local"))
    }

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
