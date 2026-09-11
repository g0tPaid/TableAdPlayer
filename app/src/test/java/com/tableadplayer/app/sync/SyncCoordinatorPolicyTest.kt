package com.tableadplayer.app.sync

import com.tableadplayer.app.data.local.SyncJobStatus
import com.tableadplayer.app.data.seed.DemoPlaylistSeeder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SyncCoordinatorPolicyTest {
    @Test
    fun demoPinRemainsUntilRemotePinSwap() {
        assertTrue(SyncCoordinator.shouldKeepDemoPin(DemoPlaylistSeeder.PLAYLIST_ID, pinSwapped = false))
        assertTrue(SyncCoordinator.shouldKeepDemoPin(null, pinSwapped = false))
        assertFalse(SyncCoordinator.shouldKeepDemoPin(DemoPlaylistSeeder.PLAYLIST_ID, pinSwapped = true))
        assertFalse(SyncCoordinator.shouldKeepDemoPin("venue-lobby", pinSwapped = false))
    }

    @Test
    fun placeholderOriginNeverDownloadsEvenWhenPlaylistArrives() {
        assertFalse(SyncCoordinator.shouldDownloadFromOrigin(live = false, playlistFetched = true))
        assertFalse(SyncCoordinator.shouldDownloadFromOrigin(live = true, playlistFetched = false))
        assertTrue(SyncCoordinator.shouldDownloadFromOrigin(live = true, playlistFetched = true))
    }

    @Test
    fun liveUnregisteredSurfacesErrorWithoutPlaylistException() {
        assertEquals("unregistered", SyncCoordinator.outcomeError(null, registered = false, live = true))
        assertNull(SyncCoordinator.outcomeError(null, registered = true, live = true))
        assertNull(SyncCoordinator.outcomeError(null, registered = false, live = false))
        assertEquals("timeout", SyncCoordinator.outcomeError("timeout", registered = true, live = true))
    }

    @Test
    fun downloadSuccessClearsRetry() {
        val next = DownloadJobPolicy.after(
            MediaDownloader.Result.Success,
            attempts = 3,
            nowMs = 1_000L,
            reserveBytes = 200L,
        )
        assertEquals(SyncJobStatus.SUCCESS, next.status)
        assertNull(next.error)
        assertNull(next.nextAttemptAt)
        assertEquals(1, next.successDelta)
        assertEquals(0, next.lowSpaceDelta)
    }

    @Test
    fun lowSpaceRetriesWithBackoffAndDoesNotFailTheJob() {
        val now = 5_000L
        val next = DownloadJobPolicy.after(
            MediaDownloader.Result.SkippedLowSpace,
            attempts = 2,
            nowMs = now,
            reserveBytes = 123L,
        )
        assertEquals(SyncJobStatus.PENDING, next.status)
        assertTrue(next.error!!.contains("123"))
        assertEquals(now + Backoff.delayMs(2), next.nextAttemptAt)
        assertEquals(1, next.lowSpaceDelta)
        assertEquals(0, next.successDelta)
    }

    @Test
    fun placeholderOriginFailsJobWithoutRetrySoDemoPinStays() {
        val next = DownloadJobPolicy.after(
            MediaDownloader.Result.SkippedPlaceholder,
            attempts = 1,
            nowMs = 0L,
            reserveBytes = 1L,
        )
        assertEquals(SyncJobStatus.FAILED, next.status)
        assertEquals("placeholder origin", next.error)
        assertNull(next.nextAttemptAt)
        assertEquals(0, next.successDelta)
    }

    @Test
    fun httpFailureRetriesWithReason() {
        val now = 10L
        val next = DownloadJobPolicy.after(
            MediaDownloader.Result.Failed("http 503"),
            attempts = 4,
            nowMs = now,
            reserveBytes = 1L,
        )
        assertEquals(SyncJobStatus.PENDING, next.status)
        assertEquals("http 503", next.error)
        assertEquals(now + Backoff.delayMs(4), next.nextAttemptAt)
    }

    @Test
    fun essentialWhenRequiredOrSamePlaylist() {
        assertTrue(DownloadJobPolicy.isEssential("a", setOf("a"), jobPlaylistId = "other", playlistId = "p"))
        assertTrue(DownloadJobPolicy.isEssential("z", emptySet(), jobPlaylistId = "p", playlistId = "p"))
        assertFalse(DownloadJobPolicy.isEssential("z", setOf("a"), jobPlaylistId = "other", playlistId = "p"))
        assertTrue(DownloadJobPolicy.skipMissingMediaId(null))
        assertTrue(DownloadJobPolicy.skipMissingMediaId(""))
        assertFalse(DownloadJobPolicy.skipMissingMediaId("clip"))
    }
}
