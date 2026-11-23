package com.rustym.mobile.data

import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.provider.MediaStore
import android.provider.OpenableColumns

/** Songs stored on the phone. */
object LocalMusic {
    private val albumArt: Uri = Uri.parse("content://media/external/audio/albumart")

    /** Every music file the media scanner knows about (needs the audio permission). */
    fun query(ctx: Context): List<Track> {
        val out = ArrayList<Track>()
        val projection = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.ALBUM_ID,
            MediaStore.Audio.Media.DURATION,
        )
        val selection = "${MediaStore.Audio.Media.IS_MUSIC} != 0 AND ${MediaStore.Audio.Media.DURATION} >= 30000"
        runCatching {
            ctx.contentResolver.query(
                MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, projection, selection, null,
                "${MediaStore.Audio.Media.TITLE} COLLATE NOCASE ASC",
            )?.use { c ->
                val iId = c.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
                val iTitle = c.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
                val iArtist = c.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
                val iAlbum = c.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM_ID)
                val iDur = c.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
                while (c.moveToNext()) {
                    val id = c.getLong(iId)
                    val uri = ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, id)
                    val artist = c.getString(iArtist).orEmpty().let { if (it.isBlank() || it == "<unknown>") "Unknown artist" else it }
                    out += Track(
                        id = "local:$id",
                        title = c.getString(iTitle).orEmpty().ifBlank { "Unknown title" },
                        artist = artist,
                        thumb = ContentUris.withAppendedId(albumArt, c.getLong(iAlbum)).toString(),
                        durationSec = c.getLong(iDur) / 1000,
                        local = uri.toString(),
                    )
                }
            }
        }
        return out
    }

    /** A single file picked by the user or opened from a file manager ("Open with Rustym"). */
    fun fromUri(ctx: Context, uri: Uri): Track {
        var name = uri.lastPathSegment.orEmpty()
        runCatching {
            ctx.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
                if (c.moveToFirst()) name = c.getString(0).orEmpty().ifBlank { name }
            }
        }
        val title = name.substringBeforeLast('.').ifBlank { "Audio file" }
        return Track("local:${uri.toString().hashCode()}", title, "On this device", "", 0, uri.toString())
    }
}
