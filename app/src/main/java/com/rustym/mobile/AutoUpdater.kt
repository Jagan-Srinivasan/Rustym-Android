package com.rustym.mobile

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageInstaller
import android.os.Build
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Hands-free updates: check GitHub, download the release APK and stage its
 * install through PackageInstaller. The system then shows the one-tap install
 * confirmation ("Install" / "Update"), which is Android's requirement for
 * sideloaded packages. The user grants "Install unknown apps" for Rustym once.
 */
object AutoUpdater {
    private const val PREFS = "rustym_settings"
    private const val KEY = "auto_update_enabled"
    private const val ACTION_INSTALL_STATUS = "com.rustym.mobile.action.INSTALL_STATUS"

    /** Preferences + live status; both are Compose-observable. */
    var enabled by mutableStateOf(false)
        private set
    var busy by mutableStateOf(false)
        private set
    var status by mutableStateOf<String?>(null)
        private set

    fun init(context: Context) {
        enabled = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(KEY, false)
    }

    fun setEnabled(context: Context, value: Boolean) {
        enabled = value
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean(KEY, value).apply()
        if (!value) status = null
    }

    /**
     * The full background flow: check for a newer release, download its APK and
     * stage the install. Returns a short user-facing result message.
     */
    suspend fun run(context: Context): String {
        if (busy) return "An update is already in progress…"
        busy = true
        try {
            val versionName = context.packageManager.getPackageInfo(context.packageName, 0).versionName.orEmpty()
            status = "Checking for updates…"
            val latest = Updater.releases(1).firstOrNull()
            when {
                latest == null -> return "Couldn't reach GitHub - check your connection.".also { status = it }
                !Updater.isNewer(latest.tag, versionName) ->
                    return "No new release - you're on the latest version ($versionName).".also { status = it }
                latest.apkUrl.isBlank() ->
                    return "New version ${latest.tag} is out, but it has no APK attached yet.".also { status = it }
            }

            status = "Downloading Rustym ${latest.tag.removePrefix("v")}…"
            val apk = File(context.cacheDir, "rustym-update.apk")
            apk.delete() // clear any leftover from a previous attempt
            val ok = downloadApk(latest.apkUrl, apk) { pct ->
                if (pct != null) status = "Downloading… $pct%"
            }
            if (!ok) return "Download failed - check your connection.".also { status = it }

            status = "Ready to install - confirm in the dialog…"
            try {
                stageInstall(context, apk)
                "Rustym ${latest.tag.removePrefix("v")} is ready - tap Install in the dialog."
            } catch (e: Exception) {
                apk.delete()
                ("Couldn't stage the install - ${e.message ?: "unknown error"}").also { status = it }
            }
        } finally {
            busy = false
        }
    }

    /** Streams the release APK to [out], reporting percent progress. */
    private suspend fun downloadApk(url: String, out: File, onProgress: (Int?) -> Unit): Boolean =
        withContext(Dispatchers.IO) {
            runCatching {
                val c = (java.net.URL(url).openConnection() as java.net.HttpURLConnection).apply {
                    connectTimeout = 10000
                    readTimeout = 30000
                }
                if (c.responseCode !in 200..299) return@runCatching false
                val total = c.contentLengthLong.takeIf { it > 0 }
                c.inputStream.use { input ->
                    out.outputStream().use { output ->
                        if (total == null) input.copyTo(output)
                        else {
                            var copied = 0L
                            var lastPct = -1
                            val buf = ByteArray(64 * 1024)
                            while (true) {
                                val n = input.read(buf)
                                if (n < 0) break
                                output.write(buf, 0, n)
                                copied += n
                                val pct = ((copied * 100) / total).toInt()
                                if (pct != lastPct) { lastPct = pct; onProgress(pct) }
                            }
                        }
                    }
                }
                c.disconnect()
                onProgress(null)
                true
            }.getOrDefault(false)
        }

    /**
     * Streams the APK into a PackageInstaller session and commits it. The system
     * posts the install confirmation; our broadcast receiver relaunches it if the
     * user leaves it (STATUS_PENDING_USER_ACTION).
     */
    private fun stageInstall(context: Context, apk: File) {
        val appContext = context.applicationContext
        val installer = appContext.packageManager.packageInstaller
        val sessionId = installer.createSession(
            PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL)
        )
        val session = installer.openSession(sessionId)
        apk.inputStream().use { input ->
            session.openWrite("rustym_apk", 0, apk.length()).use { out ->
                input.copyTo(out)
                session.fsync(out)
            }
        }
        val statusIntent = Intent(ACTION_INSTALL_STATUS).setPackage(appContext.packageName)
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or
            (if (Build.VERSION.SDK_INT >= 31) PendingIntent.FLAG_MUTABLE else 0)
        session.commit(PendingIntent.getBroadcast(appContext, sessionId, statusIntent, flags).intentSender)
        session.close()
        // The bytes already live inside the install session - the temp file is
        // dead weight from here on, whatever the user decides in the dialog.
        apk.delete()
        runCatching { File(context.cacheDir, "rustym-update.apk").delete() }

        val receiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context, intent: Intent) {
                val extras = intent.extras ?: return
                when (extras.getInt(PackageInstaller.EXTRA_STATUS, -1)) {
                    PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                        @Suppress("DEPRECATION")
                        val confirm = extras.get(Intent.EXTRA_INTENT) as? Intent
                        runCatching {
                            confirm?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            appContext.startActivity(confirm!!)
                        }
                    }
                    PackageInstaller.STATUS_SUCCESS -> {
                        status = "Install confirmed - the new version starts after it finishes."
                        runCatching { appContext.unregisterReceiver(this) }
                    }
                    else -> {
                        val msg = extras.getString(PackageInstaller.EXTRA_STATUS_MESSAGE).orEmpty()
                        status = "Install didn't finish${if (msg.isNotBlank()) " - $msg" else ""}."
                        runCatching { appContext.unregisterReceiver(this) }
                    }
                }
            }
        }
        val filter = IntentFilter(ACTION_INSTALL_STATUS)
        if (Build.VERSION.SDK_INT >= 33) {
            appContext.registerReceiver(receiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
        } else {
            appContext.registerReceiver(receiver, filter)
        }
    }
}
