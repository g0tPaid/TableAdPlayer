package com.tableadplayer.app.sync

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters

/**
 * Phase 6 stub. Must never block the playlist engine. Failures retry with
 * WorkManager exponential backoff (see DEVELOPMENT.md).
 */
class SyncWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        // TODO: fetch playlist/config, atomic download, pin revision, then swap.
        return Result.success()
    }
}
