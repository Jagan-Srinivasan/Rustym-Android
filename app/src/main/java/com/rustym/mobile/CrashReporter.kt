package com.rustym.mobile

import android.app.Application
import android.content.ContentValues
import android.os.Build
import android.provider.MediaStore
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Saves every uncaught crash to a readable file before the process dies:
 * - public Downloads folder (Android 10+, visible in any file manager)
 * - plus a copy in the app's own external files dir as a fallback
 * so we can see exactly why the app stopped instead of guessing.
 */
object CrashReporter {
    fun install(app: Application) {
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            runCatching { save(app, describe(thread, error)) }
            previous?.uncaughtException(thread, error)
        }
    }

    private fun describe(thread: Thread, error: Throwable): String = buildString {
        appendLine("Rustym crash report")
        appendLine("time:   ${SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())}")
        appendLine("thread: ${thread.name}")
        appendLine("device: ${Build.MANUFACTURER} ${Build.MODEL} · Android ${Build.VERSION.SDK_INT}")
        appendLine()
        appendLine(stack(error))
    }

    private fun stack(error: Throwable): String {
        val sw = StringWriter()
        error.printStackTrace(PrintWriter(sw))
        return sw.toString()
    }

    private fun save(app: Application, text: String) {
        val name = "rustym-crash-${System.currentTimeMillis()}.txt"
        if (Build.VERSION.SDK_INT >= 29) {
            runCatching {
                val resolver = app.contentResolver
                val values = ContentValues().apply {
                    put(MediaStore.Downloads.DISPLAY_NAME, name)
                    put(MediaStore.Downloads.MIME_TYPE, "text/plain")
                }
                val inserted = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                inserted?.let {
                    resolver.openOutputStream(it)?.use { os -> os.write(text.toByteArray()) }
                }
            }
        }
        runCatching {
            val dir = File(app.getExternalFilesDir(null) ?: app.filesDir, "crash").apply { mkdirs() }
            File(dir, name).writeText(text)
            File(dir, "last-crash.txt").writeText(text)
        }
    }
}
