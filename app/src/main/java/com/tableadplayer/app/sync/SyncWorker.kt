package com.tableadplayer.app.sync

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.tableadplayer.app.TableAdPlayerApp

/**
 * Periodic / on-demand sync. Fetches playlist, drains MEDIA_DOWNLOAD through
 * [com.tableadplayer.app.data.cache.MediaCache.ingest], pin-swaps only when every
 * required item is READY. Offline devices keep playing cached or DEMO media.
 */
class SyncWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val app = applicationContext as? TableAdPlayerApp ?: return Result.success()
        val outcome = runCatching { app.container.syncRepository.sync() }
            .getOrElse { return Result.retry() }
        return if (outcome.error != null && !outcome.registered && runAttemptCount < 3) {
            Result.retry()
        } else {
            Result.success()
        }
    }
}
