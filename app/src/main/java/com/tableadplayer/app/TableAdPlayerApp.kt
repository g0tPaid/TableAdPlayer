package com.tableadplayer.app

import android.app.Application
import android.os.Build
import android.os.UserManager
import com.tableadplayer.app.core.crash.CrashGuard
import com.tableadplayer.app.data.seed.DemoPlaylistSeeder
import com.tableadplayer.app.kiosk.ConnectivityMonitor
import com.tableadplayer.app.reporting.ReportingPump
import com.tableadplayer.app.sync.SyncScheduler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class TableAdPlayerApp : Application() {

    lateinit var container: AppContainer
        private set

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val initLock = Any()

    override fun onCreate() {
        super.onCreate()
        CrashGuard.install(this)
        if (isUserUnlocked()) {
            initRuntime()
        }
    }

    fun isRuntimeReady(): Boolean = ::container.isInitialized

    /** Room / Retrofit need credential-encrypted storage; skip until unlock. */
    fun initRuntime() {
        synchronized(initLock) {
            if (::container.isInitialized) return
            if (!isUserUnlocked()) return
            container = AppContainer(this)
            ReportingPump(container.reportingQueue, container.reportingRepository, appScope)
            ConnectivityMonitor(this) {
                ConnectivityMonitor.enqueueReportingDrain(this)
            }.start()
            SyncScheduler.enqueue(this)
            appScope.launch {
                runCatching {
                    DemoPlaylistSeeder.seed(this@TableAdPlayerApp, container.database, container.deviceIdentity)
                    container.deviceRepository.ensureRegistered()
                    container.mediaCache.discardStalePartFiles()
                    container.mediaCache.cleanup()
                }
            }
        }
    }

    fun isUserUnlocked(): Boolean {
        val um = getSystemService(UserManager::class.java) ?: return true
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            um.isUserUnlocked
        } else {
            true
        }
    }
}
