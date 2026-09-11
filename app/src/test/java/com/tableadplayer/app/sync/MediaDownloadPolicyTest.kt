package com.tableadplayer.app.sync

import com.tableadplayer.app.data.remote.ApiOrigin
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MediaDownloadPolicyTest {
    @Test
    fun placeholderBaseUrlNeverOpensASocket() {
        val result = MediaDownloadPolicy.preflight(
            baseUrl = "https://api.example.invalid/",
            remoteUrl = "https://cdn.example.com/a.png",
            freeBytes = Long.MAX_VALUE,
            essential = true,
        )
        assertEquals(MediaDownloader.Result.SkippedPlaceholder, result)
        assertTrue(ApiOrigin.isPlaceholder("https://api.example.invalid/"))
    }

    @Test
    fun relativeUrlAgainstPlaceholderIsSkipped() {
        val result = MediaDownloadPolicy.preflight(
            baseUrl = "https://api.example.invalid/",
            remoteUrl = "/v1/media/welcome.png",
            freeBytes = Long.MAX_VALUE,
            essential = true,
        )
        assertEquals(MediaDownloader.Result.SkippedPlaceholder, result)
    }

    @Test
    fun blankUrlFailsWithoutHttp() {
        val result = MediaDownloadPolicy.preflight(
            baseUrl = "http://10.0.2.2:8787/",
            remoteUrl = "  ",
            freeBytes = Long.MAX_VALUE,
            essential = true,
        )
        assertEquals(MediaDownloader.Result.Failed("missing url"), result)
    }

    @Test
    fun nonessentialLowSpaceSkips() {
        val result = MediaDownloadPolicy.preflight(
            baseUrl = "http://10.0.2.2:8787/",
            remoteUrl = "/v1/media/welcome.png",
            freeBytes = 1_000L,
            essential = false,
            reserveBytes = FreeSpacePolicy.DEFAULT_RESERVE_BYTES,
        )
        assertEquals(MediaDownloader.Result.SkippedLowSpace, result)
    }

    @Test
    fun liveOriginWithSpaceProceedsToHttp() {
        val result = MediaDownloadPolicy.preflight(
            baseUrl = "http://10.0.2.2:8787/",
            remoteUrl = "/v1/media/welcome.png",
            freeBytes = Long.MAX_VALUE,
            essential = true,
        )
        assertNull(result)
    }

    @Test
    fun productionCdnHostIsNotThePlaceholder() {
        assertTrue(ApiOrigin.usesLiveNetwork("https://ops.example.com/"))
        assertTrue(!ApiOrigin.PLACEHOLDER_HOST.contains("cnszfyd"))
    }
}
