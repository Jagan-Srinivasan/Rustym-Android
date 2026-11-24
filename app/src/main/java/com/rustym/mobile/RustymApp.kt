package com.rustym.mobile

import android.app.Application
import com.rustym.mobile.data.Auth
import com.rustym.mobile.data.Yt
import com.rustym.mobile.ui.ThemeStore
import kotlin.concurrent.thread

class RustymApp : Application() {
    override fun onCreate() {
        super.onCreate()
        CrashReporter.install(this)
        Auth.init(this)
        ThemeStore.init(this)
        thread(name = "yt-init") { Yt.init() }
    }
}
