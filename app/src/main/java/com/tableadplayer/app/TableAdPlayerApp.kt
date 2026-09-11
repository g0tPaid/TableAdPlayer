package com.tableadplayer.app

import android.app.Application
import com.tableadplayer.app.core.crash.CrashGuard
import com.tableadplayer.app.data.seed.DemoPlaylistSeeder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class TableAdPlayerApp : Application() {

    lateinit var container: AppContainer
        private set

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        CrashGuard.install(this)
        container = AppContainer(this)
        appScope.launch {
            runCatching {
                DemoPlaylistSeeder.seed(this@TableAdPlayerApp, container.database, container.deviceIdentity)
                container.mediaCache.discardStalePartFiles()
                container.mediaCache.cleanup()
            }
        }
    }
}
