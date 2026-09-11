package com.tableadplayer.app.reporting

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.tableadplayer.app.TableAdPlayerApp

/**
 * Drains heartbeat + playback-event outboxes. Failures stay queued; this
 * worker never interacts with [com.tableadplayer.app.playback.PlaylistEngine].
 */
class ReportingWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val app = applicationContext as? TableAdPlayerApp ?: return Result.success()
        app.initRuntime()
        if (!app.isRuntimeReady()) return Result.retry()
        val outcome = runCatching { app.container.reportingRepository.drainAll() }
            .getOrElse { return if (runAttemptCount < 3) Result.retry() else Result.success() }
        return if (outcome.failed > 0 && runAttemptCount < 3) {
            Result.retry()
        } else {
            Result.success()
        }
    }
}
