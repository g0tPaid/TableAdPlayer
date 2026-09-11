package com.tableadplayer.app.data.cache

import com.tableadplayer.app.data.local.MediaOrigin
import com.tableadplayer.app.data.local.MediaState

/**
 * Pure rules for [MediaCache] so ingest/pin-swap/cleanup stay testable without Room.
 */
object MediaReadiness {
    fun isPlayable(
        state: MediaState,
        localPath: String?,
        fileExists: Boolean,
        length: Long,
    ): Boolean {
        if (state != MediaState.READY) return false
        if (localPath.isNullOrBlank()) return false
        return fileExists && length > 0L
    }

    /**
     * Pin-swap readiness: ASSET DEMO rows are ready without a cache file;
     * remote rows need READY + a file named `{id}_v{version}`.
     */
    fun isReadyForPinSwap(
        origin: String,
        state: MediaState,
        playable: Boolean,
        fileName: String?,
        expectedFileName: String,
    ): Boolean {
        if (origin == MediaOrigin.ASSET && state == MediaState.READY) return true
        if (state != MediaState.READY) return false
        if (!playable) return false
        return fileName == expectedFileName
    }

    /** Enqueue should not clobber READY/DOWNLOADING; everything else returns to REMOTE. */
    fun shouldMarkRemoteOnEnqueue(state: MediaState): Boolean {
        return state != MediaState.READY && state != MediaState.DOWNLOADING
    }

    /**
     * Keep a `*.part` only when its final name is an active-playlist READY file,
     * or an active-playlist DOWNLOADING owner matches.
     */
    fun keepPartFile(
        finalName: String,
        keepFinalNames: Set<String>,
        activeDownloadingMatches: Boolean,
    ): Boolean {
        return finalName in keepFinalNames || activeDownloadingMatches
    }

    fun downloadingOwnerMatches(
        mediaId: String,
        state: MediaState,
        localPath: String?,
        finalName: String,
        activeIds: Set<String>,
    ): Boolean {
        if (mediaId !in activeIds) return false
        if (state != MediaState.DOWNLOADING) return false
        if (localPath.isNullOrBlank()) return true
        return java.io.File(localPath).name == finalName
    }
}
