package com.tableadplayer.app.data.cache

import com.tableadplayer.app.data.local.MediaCleanupSnapshot
import com.tableadplayer.app.data.local.MediaState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MediaCleanupPolicyTest {
    private val now = 1_000_000_000_000L
    private val day = 24L * 60L * 60L * 1000L

    private fun media(
        id: String,
        state: MediaState,
        fileSize: Long = 100L,
        lastAccessedAt: Long? = now,
        downloadedAt: Long? = now,
    ) = MediaCleanupSnapshot(
        id = id,
        state = state,
        fileSize = fileSize,
        lastAccessedAt = lastAccessedAt,
        downloadedAt = downloadedAt,
        localPath = "media/${id}_v1",
    )

    @Test
    fun neverDeletesActivePlaylistMediaEvenIfExpiredOrFailed() {
        val active = setOf("keep-ready", "keep-expired", "keep-failed", "keep-deleted")
        val plan = MediaCleanupPolicy.plan(
            media = listOf(
                media("keep-ready", MediaState.READY),
                media("keep-expired", MediaState.EXPIRED),
                media("keep-failed", MediaState.FAILED),
                media("keep-deleted", MediaState.DELETED),
                media("orphan-expired", MediaState.EXPIRED),
            ),
            activePlaylistMediaIds = active,
            nowMs = now,
        )
        assertFalse(plan.mediaIdsToDelete.any { it in active })
        assertTrue("orphan-expired" in plan.mediaIdsToDelete)
        assertEquals(active, plan.protectedIds)
    }

    @Test
    fun deletesExpiredFailedAndDeletedWhenNotActive() {
        val plan = MediaCleanupPolicy.plan(
            media = listOf(
                media("a", MediaState.EXPIRED),
                media("b", MediaState.FAILED),
                media("c", MediaState.DELETED),
                media("d", MediaState.READY),
                media("e", MediaState.DOWNLOADING),
                media("f", MediaState.REMOTE),
            ),
            activePlaylistMediaIds = emptySet(),
            nowMs = now,
        )
        assertEquals(setOf("a", "b", "c"), plan.mediaIdsToDelete.toSet())
    }

    @Test
    fun unusedReadyPastTtlIsDeletedWhenNotActive() {
        val stale = media(
            id = "old",
            state = MediaState.READY,
            lastAccessedAt = now - MediaCleanupPolicy.DEFAULT_UNUSED_TTL_MS - day,
        )
        val fresh = media("new", MediaState.READY, lastAccessedAt = now)
        val plan = MediaCleanupPolicy.plan(
            media = listOf(stale, fresh),
            activePlaylistMediaIds = emptySet(),
            nowMs = now,
        )
        assertEquals(listOf("old"), plan.mediaIdsToDelete)
    }

    @Test
    fun lruEvictsNonActiveReadyWhenOverMaxBytes() {
        val active = media("pin", MediaState.READY, fileSize = 500L)
        val older = media("old", MediaState.READY, fileSize = 300L, lastAccessedAt = now - 5_000L)
        val newer = media("new", MediaState.READY, fileSize = 300L, lastAccessedAt = now)
        val plan = MediaCleanupPolicy.plan(
            media = listOf(active, older, newer),
            activePlaylistMediaIds = setOf("pin"),
            nowMs = now,
            maxReadyBytes = 900L,
        )
        assertFalse("pin" in plan.mediaIdsToDelete)
        assertTrue("old" in plan.mediaIdsToDelete)
        assertFalse("new" in plan.mediaIdsToDelete)
    }

    @Test
    fun emptyActivePlaylistStillDoesNotInventDeletesForRemoteOnlyRows() {
        val plan = MediaCleanupPolicy.plan(
            media = listOf(media("r", MediaState.REMOTE, fileSize = 0L, lastAccessedAt = null, downloadedAt = null)),
            activePlaylistMediaIds = emptySet(),
            nowMs = now,
            maxReadyBytes = 0L,
        )
        assertTrue(plan.mediaIdsToDelete.isEmpty())
    }

    @Test
    fun aggressiveClearStillProtectsActivePlaylist() {
        val plan = MediaCleanupPolicy.plan(
            media = listOf(
                media("pin", MediaState.READY, fileSize = 9_000L),
                media("orphan", MediaState.READY, fileSize = 100L, lastAccessedAt = now),
            ),
            activePlaylistMediaIds = setOf("pin"),
            nowMs = now,
            maxReadyBytes = 0L,
            unusedTtlMs = 0L,
        )
        assertFalse("pin" in plan.mediaIdsToDelete)
        assertTrue("orphan" in plan.mediaIdsToDelete)
    }
}
