package com.tableadplayer.app.sync

/**
 * Exponential backoff for sync/heartbeat. Capped so a device recovers after
 * outages without hammering the API.
 */
object Backoff {
    fun delayMs(attempt: Int, baseMs: Long = 2_000L, maxMs: Long = 15 * 60 * 1000L): Long {
        val exp = attempt.coerceAtLeast(0).coerceAtMost(12)
        val raw = baseMs * (1L shl exp)
        return raw.coerceAtMost(maxMs)
    }
}
