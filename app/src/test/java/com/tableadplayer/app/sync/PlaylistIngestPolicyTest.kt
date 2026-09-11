package com.tableadplayer.app.sync

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaylistIngestPolicyTest {
    @Test
    fun missingChecksumDoesNotBumpVersion() {
        assertFalse(PlaylistIngestPolicy.checksumRequiresNewVersion(null, "abc"))
        assertFalse(PlaylistIngestPolicy.checksumRequiresNewVersion("abc", null))
        assertFalse(PlaylistIngestPolicy.checksumRequiresNewVersion("", "abc"))
        assertFalse(PlaylistIngestPolicy.checksumRequiresNewVersion("abc", "  "))
    }

    @Test
    fun checksumChangeIsCaseInsensitive() {
        assertFalse(PlaylistIngestPolicy.checksumRequiresNewVersion("ab", "AB"))
        assertTrue(PlaylistIngestPolicy.checksumRequiresNewVersion("aa", "bb"))
    }

    @Test
    fun nextVersionUsesRevisionWhenNewRow() {
        assertEquals(2L, PlaylistIngestPolicy.nextVersion(null, playlistRevision = 2L, checksumChanged = false))
        assertEquals(4L, PlaylistIngestPolicy.nextVersion(3L, playlistRevision = 9L, checksumChanged = true))
        assertEquals(3L, PlaylistIngestPolicy.nextVersion(3L, playlistRevision = 9L, checksumChanged = false))
        assertEquals(1L, PlaylistIngestPolicy.nextVersion(null, playlistRevision = 0L, checksumChanged = false))
    }

    @Test
    fun enqueueSkippedWhenReadyOrJobExists() {
        assertFalse(PlaylistIngestPolicy.shouldEnqueueDownload(currentRevisionReady = true, hasActiveJob = false))
        assertFalse(PlaylistIngestPolicy.shouldEnqueueDownload(currentRevisionReady = false, hasActiveJob = true))
        assertTrue(PlaylistIngestPolicy.shouldEnqueueDownload(currentRevisionReady = false, hasActiveJob = false))
    }
}
