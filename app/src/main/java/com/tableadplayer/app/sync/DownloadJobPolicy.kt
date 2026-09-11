package com.tableadplayer.app.sync

import com.tableadplayer.app.data.local.SyncJobStatus

/**
 * Maps a [MediaDownloader.Result] onto Room sync-job status. Isolated so
 * coordinator tests do not need WorkManager or a live HTTP client.
 */
object DownloadJobPolicy {
    data class Transition(
        val status: String,
        val error: String?,
        val nextAttemptAt: Long?,
        val successDelta: Int = 0,
        val lowSpaceDelta: Int = 0,
    )

    fun isEssential(
        mediaId: String,
        requiredIds: Set<String>,
        jobPlaylistId: String?,
        playlistId: String,
    ): Boolean {
        return mediaId in requiredIds || jobPlaylistId == playlistId
    }

    fun after(
        result: MediaDownloader.Result,
        attempts: Int,
        nowMs: Long,
        reserveBytes: Long,
    ): Transition {
        return when (result) {
            MediaDownloader.Result.Success -> Transition(
                status = SyncJobStatus.SUCCESS,
                error = null,
                nextAttemptAt = null,
                successDelta = 1,
            )
            MediaDownloader.Result.SkippedLowSpace -> Transition(
                status = SyncJobStatus.PENDING,
                error = "low space (reserve=$reserveBytes)",
                nextAttemptAt = nowMs + Backoff.delayMs(attempts),
                lowSpaceDelta = 1,
            )
            MediaDownloader.Result.SkippedPlaceholder -> Transition(
                status = SyncJobStatus.FAILED,
                error = "placeholder origin",
                nextAttemptAt = null,
            )
            is MediaDownloader.Result.Failed -> Transition(
                status = SyncJobStatus.PENDING,
                error = result.reason,
                nextAttemptAt = nowMs + Backoff.delayMs(attempts),
            )
        }
    }

    fun skipMissingMediaId(mediaId: String?): Boolean = mediaId.isNullOrBlank()
}
