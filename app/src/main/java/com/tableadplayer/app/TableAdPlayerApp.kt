package com.tableadplayer.app

import android.app.Application
import com.tableadplayer.app.core.crash.CrashGuard

class TableAdPlayerApp : Application() {

    override fun onCreate() {
        super.onCreate()
        CrashGuard.install(this)
    }
}
