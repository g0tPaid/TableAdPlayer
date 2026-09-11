package com.tableadplayer.app.sync

/**
 * Playlist ingest helpers that stay off the Room transaction path for tests.
 */
object PlaylistIngestPolicy {
    /**
     * Version bump only when both sides have a checksum and they differ.
     * Missing checksums keep the previous version so DEMO / partial rows
     * are not invalidated by an incomplete server payload.
     */
    fun checksumRequiresNewVersion(existing: String?, incoming: String?): Boolean {
        if (incoming.isNullOrBlank() || existing.isNullOrBlank()) return false
        return !existing.equals(incoming, ignoreCase = true)
    }

    fun nextVersion(previousVersion: Long?, playlistRevision: Long, checksumChanged: Boolean): Long {
        return when {
            checksumChanged -> (previousVersion ?: 1L) + 1L
            else -> previousVersion ?: playlistRevision.coerceAtLeast(1L)
        }
    }

    fun shouldEnqueueDownload(currentRevisionReady: Boolean, hasActiveJob: Boolean): Boolean {
        return !currentRevisionReady && !hasActiveJob
    }
}
