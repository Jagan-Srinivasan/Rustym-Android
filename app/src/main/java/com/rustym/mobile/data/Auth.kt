package com.rustym.mobile.data

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

/**
 * The signed-in Google/YouTube session: the cookies the YouTube Music website would hold.
 * Stored encrypted with a key in the Android Keystore; never leaves the device except in requests to YouTube.
 */
object Auth {
    private lateinit var app: Context
    @Volatile private var map: Map<String, String> = emptyMap()

    fun init(ctx: Context) {
        app = ctx.applicationContext
        map = parse(prefs().getString("cookies", "").orEmpty())
    }

    private fun prefs(): SharedPreferences = try {
        val key = MasterKey.Builder(app).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build()
        EncryptedSharedPreferences.create(
            app, "auth", key,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )
    } catch (e: Exception) {
        // Some devices have a broken Keystore; the app-private file is still sandboxed from other apps.
        app.getSharedPreferences("auth_plain", Context.MODE_PRIVATE)
    }

    fun parse(header: String): Map<String, String> {
        val out = LinkedHashMap<String, String>()
        for (part in header.split(";")) {
            val kv = part.trim()
            val i = kv.indexOf('=')
            if (i > 0) out[kv.substring(0, i)] = kv.substring(i + 1)
        }
        return out
    }

    fun cookies(): Map<String, String> = map

    val loggedIn: Boolean
        get() = map.containsKey("SAPISID") || map.containsKey("__Secure-3PAPISID")

    fun hasLoginCookie(c: Map<String, String>) = c.containsKey("SAPISID") || c.containsKey("__Secure-3PAPISID")

    fun save(cookies: Map<String, String>) {
        map = LinkedHashMap(cookies)
        prefs().edit().putString("cookies", cookies.entries.joinToString("; ") { "${it.key}=${it.value}" }).apply()
    }

    fun clear() {
        map = emptyMap()
        prefs().edit().remove("cookies").apply()
    }
}
