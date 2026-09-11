package com.tableadplayer.app.sync

import com.tableadplayer.app.data.remote.ApiOrigin

/**
 * Preflight for [MediaDownloader]: placeholder origins never open a socket,
 * low space skips nonessential work, blank URLs fail without HTTP.
 */
object MediaDownloadPolicy {
    fun preflight(
        baseUrl: String,
        remoteUrl: String,
        freeBytes: Long,
        essential: Boolean,
        reserveBytes: Long = FreeSpacePolicy.DEFAULT_RESERVE_BYTES,
        criticalBytes: Long = FreeSpacePolicy.CRITICAL_BYTES,
    ): MediaDownloader.Result? {
        if (ApiOrigin.isPlaceholder(baseUrl)) {
            return MediaDownloader.Result.SkippedPlaceholder
        }
        if (!FreeSpacePolicy.allowDownload(freeBytes, essential, reserveBytes, criticalBytes)) {
            return MediaDownloader.Result.SkippedLowSpace
        }
        if (remoteUrl.isBlank()) {
            return MediaDownloader.Result.Failed("missing url")
        }
        val url = ApiOrigin.resolveMediaUrl(baseUrl, remoteUrl)
        if (ApiOrigin.isPlaceholder(url)) {
            return MediaDownloader.Result.SkippedPlaceholder
        }
        return null
    }
}
