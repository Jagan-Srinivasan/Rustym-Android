package com.rustym.mobile.data

import android.net.Uri
import android.os.Bundle
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata

/** One playable song. [id] is the 11-character YouTube video id. */
data class Track(
    val id: String,
    val title: String,
    val artist: String,
    val thumb: String,
    val durationSec: Long,
    /** content:// or file:// URI for a song stored on the phone; empty for YouTube songs. */
    val local: String = "",
) {
    val isLocal: Boolean get() = local.isNotEmpty()
}

const val WATCH_URL = "https://www.youtube.com/watch?v="

/**
 * The URI is only a placeholder ("watch?v=ID"): PlaybackService swaps it for the real, short-lived
 * audio stream URL at the moment ExoPlayer needs it, so queued songs never hold an expired link.
 */
fun Track.toMediaItem(): MediaItem =
    MediaItem.Builder()
        .setMediaId(id)
        .setUri(if (local.isNotEmpty()) local else WATCH_URL + id)
        .setMediaMetadata(
            MediaMetadata.Builder()
                .setTitle(title)
                .setArtist(artist)
                .setArtworkUri(if (thumb.isNotEmpty()) Uri.parse(thumb) else null)
                .setExtras(Bundle().apply { putLong("dur", durationSec); putString("local", local) })
                .build()
        )
        .build()

fun MediaItem.toTrack(): Track =
    Track(
        id = mediaId,
        title = mediaMetadata.title?.toString().orEmpty(),
        artist = mediaMetadata.artist?.toString().orEmpty(),
        thumb = mediaMetadata.artworkUri?.toString().orEmpty(),
        durationSec = mediaMetadata.extras?.getLong("dur") ?: 0L,
        local = mediaMetadata.extras?.getString("local").orEmpty(),
    )
