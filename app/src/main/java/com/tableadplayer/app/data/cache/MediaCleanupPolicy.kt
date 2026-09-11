package com.tableadplayer.app.data.cache

import com.tableadplayer.app.data.local.MediaCleanupSnapshot
import com.tableadplayer.app.data.local.MediaState

/**
 * Decides which cached media rows may lose their files.
 *
 * Invariant: IDs in [activePlaylistMediaIds] are **never** returned, regardless
 * of [MediaState] (including EXPIRED / DELETED / FAILED).
 */
object MediaCleanupPolicy {
    const val DEFAULT_UNUSED_TTL_MS: Long = 14L * 24L * 60L * 60L * 1000L
    const val DEFAULT_MAX_READY_BYTES: Long = 2L * 1024L * 1024L * 1024L

    fun plan(
        media: List<MediaCleanupSnapshot>,
        activePlaylistMediaIds: Set<String>,
        nowMs: Long,
        maxReadyBytes: Long = DEFAULT_MAX_READY_BYTES,
        unusedTtlMs: Long = DEFAULT_UNUSED_TTL_MS,
    ): CleanupPlan {
        val protectedIds = activePlaylistMediaIds
        val eligible = media.filter { it.id !in protectedIds }

        val expiredOrDeleted = eligible
            .filter { it.state == MediaState.EXPIRED || it.state == MediaState.DELETED }
            .map { it.id }

        val failed = eligible
            .filter { it.state == MediaState.FAILED }
            .map { it.id }

        val unusedReady = eligible
            .filter { snapshot ->
                snapshot.state == MediaState.READY &&
                    unusedSince(snapshot) + unusedTtlMs < nowMs
            }
            .map { it.id }

        val scheduled = LinkedHashSet<String>().apply {
            addAll(expiredOrDeleted)
            addAll(failed)
            addAll(unusedReady)
        }

        val remainingReady = media
            .filter {
                it.state == MediaState.READY &&
                    it.id !in protectedIds &&
                    it.id !in scheduled
            }
            .sortedBy { unusedSince(it) }

        var usedBytes = media
            .filter { it.state == MediaState.READY && it.id !in scheduled }
            .sumOf { it.fileSize.coerceAtLeast(0L) }

        for (snapshot in remainingReady) {
            if (usedBytes <= maxReadyBytes) break
            scheduled += snapshot.id
            usedBytes -= snapshot.fileSize.coerceAtLeast(0L)
        }

        return CleanupPlan(
            mediaIdsToDelete = scheduled.toList(),
            protectedIds = protectedIds,
        )
    }

    private fun unusedSince(snapshot: MediaCleanupSnapshot): Long {
        return snapshot.lastAccessedAt ?: snapshot.downloadedAt ?: 0L
    }
}

data class CleanupPlan(
    val mediaIdsToDelete: List<String>,
    val protectedIds: Set<String>,
)
