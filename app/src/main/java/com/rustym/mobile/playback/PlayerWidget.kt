package com.rustym.mobile.playback

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.widget.RemoteViews
import com.rustym.mobile.MainActivity
import com.rustym.mobile.R
import com.rustym.mobile.data.Track

/**
 * Home-screen widget: artwork + song info + prev / play-pause / next.
 * State is painted by [PlayerWidget.update], which the PlaybackService calls
 * on every track change and play-state change, so it is always fresh.
 * Button taps go straight to the service with an intent action.
 */
object PlayerWidget {
    const val ACTION_PLAY_PAUSE = "com.rustym.mobile.widget.PLAY_PAUSE"
    const val ACTION_NEXT = "com.rustym.mobile.widget.NEXT"
    const val ACTION_PREV = "com.rustym.mobile.widget.PREV"
    const val ACTION_REFRESH = "com.rustym.mobile.widget.REFRESH"

    // Artwork is decoded on the service's IO scope; keep the last bitmap to avoid re-decoding.
    private var lastUrl: String? = null
    private var lastArt: Bitmap? = null

    fun update(context: Context, track: Track?, isPlaying: Boolean) {
        val manager = AppWidgetManager.getInstance(context)
        val views = RemoteViews(context.packageName, R.layout.player_widget)

        if (track == null) {
            views.setTextViewText(R.id.wTitle, context.getString(R.string.app_name))
            views.setTextViewText(R.id.wArtist, context.getString(R.string.widget_idle))
            views.setImageViewResource(R.id.wArt, R.mipmap.ic_launcher)
            views.setImageViewResource(R.id.wPlayPause, R.drawable.ic_w_play)
        } else {
            views.setTextViewText(R.id.wTitle, track.title)
            views.setTextViewText(R.id.wArtist, track.artist)
            views.setImageViewResource(R.id.wPlayPause, if (isPlaying) R.drawable.ic_w_pause else R.drawable.ic_w_play)
            val art = if (track.thumb == lastUrl) lastArt else decodeArt(track.thumb)
            if (track.thumb != lastUrl) {
                lastUrl = track.thumb
                lastArt = art
            }
            if (art != null) views.setImageViewBitmap(R.id.wArt, art)
            else views.setImageViewResource(R.id.wArt, R.mipmap.ic_launcher)
        }

        // Tapping the artwork / title opens the app.
        val open = PendingIntent.getActivity(
            context, 0,
            Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        views.setOnClickPendingIntent(R.id.wArt, open)
        views.setOnClickPendingIntent(R.id.wTitleBox, open)

        // Transport buttons talk to the PlaybackService directly.
        mapOf(
            R.id.wPrev to ACTION_PREV,
            R.id.wPlayPause to ACTION_PLAY_PAUSE,
            R.id.wNext to ACTION_NEXT,
        ).forEach { (id, action) ->
            val pi = PendingIntent.getService(
                context, id,
                Intent(context, PlaybackService::class.java).setAction(action),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            views.setOnClickPendingIntent(id, pi)
        }

        manager.updateAppWidget(
            manager.getAppWidgetIds(ComponentName(context, PlayerWidgetProvider::class.java)),
            views,
        )
    }

    private fun decodeArt(url: String): Bitmap? = runCatching {
        val hi = Regex("=w\\d+-h\\d+")
        java.net.URL(url.replace(hi, "=w200-h200")).openStream().use { BitmapFactory.decodeStream(it) }
    }.getOrNull()
}

/** Receiver the system calls when a widget is placed, restored, or the pack requests an update. */
class PlayerWidgetProvider : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, appWidgetIds: IntArray) {
        // The service owns the truth; ask it to paint the widgets if it is around.
        runCatching {
            context.startService(
                Intent(context, PlaybackService::class.java).setAction(PlayerWidget.ACTION_REFRESH)
            )
        }
        // If the service cannot start (background limits), the last painted state stays visible.
    }
}
