package com.tableadplayer.app.sync

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.tableadplayer.app.TableAdPlayerApp

/**
 * Phase 6 network/pin-swap stays stubbed. Cache cleanup and future MEDIA_DOWNLOAD
 * jobs in Room are the only hooks invoked here.
 */
class SyncWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val app = applicationContext as? TableAdPlayerApp ?: return Result.success()
        return runCatching {
            app.container.mediaCache.discardStalePartFiles()
            app.container.mediaCache.cleanup()
            Result.success()
        }.getOrElse { Result.retry() }
    }
}
