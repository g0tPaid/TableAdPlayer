package com.tableadplayer.app.sync

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

object SyncScheduler {
    const val UNIQUE_PERIODIC = "tablead-sync-periodic"
    const val UNIQUE_ONCE = "tablead-sync-once"
    const val UNIQUE_REPORTING = "tablead-reporting-drain"

    fun enqueue(context: Context) {
        val manager = WorkManager.getInstance(context.applicationContext)
        // Do not require network: DEMO / fixture sync and cache cleanup must run offline.
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.NOT_REQUIRED)
            .build()
        val periodic = PeriodicWorkRequestBuilder<SyncWorker>(15, TimeUnit.MINUTES)
            .setConstraints(constraints)
            .setBackoffCriteria(
                androidx.work.BackoffPolicy.EXPONENTIAL,
                30,
                TimeUnit.SECONDS,
            )
            .build()
        manager.enqueueUniquePeriodicWork(
            UNIQUE_PERIODIC,
            ExistingPeriodicWorkPolicy.KEEP,
            periodic,
        )
        enqueueOnce(manager, constraints, ExistingWorkPolicy.KEEP)
    }

    /** Replace any completed one-shot so boot / admin "Sync now" actually runs. */
    fun enqueueNow(context: Context) {
        val manager = WorkManager.getInstance(context.applicationContext)
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.NOT_REQUIRED)
            .build()
        enqueueOnce(manager, constraints, ExistingWorkPolicy.REPLACE)
    }

    fun enqueueReporting(context: Context) {
        val manager = WorkManager.getInstance(context.applicationContext)
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()
        val once = OneTimeWorkRequestBuilder<com.tableadplayer.app.reporting.ReportingWorker>()
            .setConstraints(constraints)
            .setBackoffCriteria(
                androidx.work.BackoffPolicy.EXPONENTIAL,
                30,
                TimeUnit.SECONDS,
            )
            .build()
        manager.enqueueUniqueWork(UNIQUE_REPORTING, ExistingWorkPolicy.REPLACE, once)
    }

    private fun enqueueOnce(
        manager: WorkManager,
        constraints: Constraints,
        policy: ExistingWorkPolicy,
    ) {
        val once = OneTimeWorkRequestBuilder<SyncWorker>()
            .setConstraints(constraints)
            .build()
        manager.enqueueUniqueWork(UNIQUE_ONCE, policy, once)
    }
}
