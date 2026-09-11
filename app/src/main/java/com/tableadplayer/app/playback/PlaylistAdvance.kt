package com.tableadplayer.app.playback

/**
 * Pure helpers so the playlist engine never busy-loops when every item is bad.
 */
object PlaylistAdvance {
    const val ALL_FAILED_BACKOFF_MS = 5_000L
    const val IMAGE_GRACE_MS = 2_000L
    const val VIDEO_PREPARE_TIMEOUT_MS = 12_000L
    const val VIDEO_MAX_MS = 15 * 60 * 1000L
    const val MISSING_ASSET_SKIP = true

    fun nextIndex(current: Int, size: Int): Int {
        if (size <= 0) return 0
        return (current + 1) % size
    }

    fun shouldBackoff(consecutiveFailures: Int, size: Int): Boolean {
        if (size <= 0) return true
        return consecutiveFailures >= size
    }
}
