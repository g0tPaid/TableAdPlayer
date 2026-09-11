package com.tableadplayer.app.data.cache

import com.tableadplayer.app.data.local.MediaOrigin
import com.tableadplayer.app.data.local.MediaState
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MediaReadinessTest {
    @Test
    fun playableRequiresReadyExistingNonEmptyFile() {
        assertTrue(MediaReadiness.isPlayable(MediaState.READY, "media/a_v1", fileExists = true, length = 12L))
        assertFalse(MediaReadiness.isPlayable(MediaState.DOWNLOADING, "media/a_v1", fileExists = true, length = 12L))
        assertFalse(MediaReadiness.isPlayable(MediaState.READY, null, fileExists = true, length = 12L))
        assertFalse(MediaReadiness.isPlayable(MediaState.READY, "media/a_v1", fileExists = false, length = 12L))
        assertFalse(MediaReadiness.isPlayable(MediaState.READY, "media/a_v1", fileExists = true, length = 0L))
        assertFalse(MediaReadiness.isPlayable(MediaState.FAILED, "media/a_v1", fileExists = true, length = 12L))
    }

    @Test
    fun assetDemoRowsArePinSwapReadyWithoutCacheFile() {
        assertTrue(
            MediaReadiness.isReadyForPinSwap(
                origin = MediaOrigin.ASSET,
                state = MediaState.READY,
                playable = false,
                fileName = null,
                expectedFileName = "welcome_v1",
            ),
        )
        assertFalse(
            MediaReadiness.isReadyForPinSwap(
                origin = MediaOrigin.ASSET,
                state = MediaState.REMOTE,
                playable = false,
                fileName = null,
                expectedFileName = "welcome_v1",
            ),
        )
    }

    @Test
    fun remotePinSwapRequiresMatchingVersionFilename() {
        assertTrue(
            MediaReadiness.isReadyForPinSwap(
                origin = MediaOrigin.REMOTE,
                state = MediaState.READY,
                playable = true,
                fileName = "slide_v2",
                expectedFileName = "slide_v2",
            ),
        )
        assertFalse(
            MediaReadiness.isReadyForPinSwap(
                origin = MediaOrigin.REMOTE,
                state = MediaState.READY,
                playable = true,
                fileName = "slide_v1",
                expectedFileName = "slide_v2",
            ),
        )
        assertFalse(
            MediaReadiness.isReadyForPinSwap(
                origin = MediaOrigin.REMOTE,
                state = MediaState.READY,
                playable = false,
                fileName = "slide_v2",
                expectedFileName = "slide_v2",
            ),
        )
    }

    @Test
    fun enqueueDoesNotClobberReadyOrDownloading() {
        assertFalse(MediaReadiness.shouldMarkRemoteOnEnqueue(MediaState.READY))
        assertFalse(MediaReadiness.shouldMarkRemoteOnEnqueue(MediaState.DOWNLOADING))
        assertTrue(MediaReadiness.shouldMarkRemoteOnEnqueue(MediaState.FAILED))
        assertTrue(MediaReadiness.shouldMarkRemoteOnEnqueue(MediaState.EXPIRED))
        assertTrue(MediaReadiness.shouldMarkRemoteOnEnqueue(MediaState.DELETED))
        assertTrue(MediaReadiness.shouldMarkRemoteOnEnqueue(MediaState.REMOTE))
    }

    @Test
    fun stalePartKeptOnlyForActiveDownloadingOrReadyName() {
        assertTrue(MediaReadiness.keepPartFile("a_v1", keepFinalNames = setOf("a_v1"), activeDownloadingMatches = false))
        assertTrue(MediaReadiness.keepPartFile("b_v3", keepFinalNames = emptySet(), activeDownloadingMatches = true))
        assertFalse(MediaReadiness.keepPartFile("orphan_v1", keepFinalNames = setOf("a_v1"), activeDownloadingMatches = false))
    }

    @Test
    fun downloadingOwnerMatchesActivePlaylistOnly() {
        assertTrue(
            MediaReadiness.downloadingOwnerMatches(
                mediaId = "clip",
                state = MediaState.DOWNLOADING,
                localPath = null,
                finalName = "clip_v2",
                activeIds = setOf("clip"),
            ),
        )
        assertTrue(
            MediaReadiness.downloadingOwnerMatches(
                mediaId = "clip",
                state = MediaState.DOWNLOADING,
                localPath = "media/clip_v2",
                finalName = "clip_v2",
                activeIds = setOf("clip"),
            ),
        )
        assertFalse(
            MediaReadiness.downloadingOwnerMatches(
                mediaId = "clip",
                state = MediaState.DOWNLOADING,
                localPath = "media/clip_v2",
                finalName = "clip_v2",
                activeIds = setOf("other"),
            ),
        )
        assertFalse(
            MediaReadiness.downloadingOwnerMatches(
                mediaId = "clip",
                state = MediaState.READY,
                localPath = null,
                finalName = "clip_v2",
                activeIds = setOf("clip"),
            ),
        )
    }
}
