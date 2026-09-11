package com.tableadplayer.app.reporting

import com.tableadplayer.app.data.local.PlaybackEventType
import com.tableadplayer.app.sync.Backoff

/**
 * Room outbox policy for play/skip/error/completed events. Mirrors
 * [com.tableadplayer.app.data.repo.HeartbeatOutbox]: drop-oldest when full,
 * exponential retry, never on the playlist critical path.
 */
object PlaybackEventOutbox {
    const val MAX_PENDING = 512
    const val DRAIN_BATCH = 32
    const val MAX_ATTEMPTS = 16

    val TYPES: Set<String> = setOf(
        PlaybackEventType.PLAY,
        PlaybackEventType.SKIP,
        PlaybackEventType.ERROR,
        PlaybackEventType.COMPLETED,
    )

    fun isPlaybackType(type: String): Boolean = type in TYPES

    fun shouldDropOldest(pendingCount: Int, max: Int = MAX_PENDING): Boolean = pendingCount >= max

    fun nextAttemptAt(attempts: Int, nowMs: Long): Long = nowMs + Backoff.delayMs(attempts)

    fun shouldDropAfterAttempts(attempts: Int, max: Int = MAX_ATTEMPTS): Boolean = attempts >= max

    fun overflowCount(pendingCount: Int, max: Int = MAX_PENDING): Int =
        (pendingCount - max + 1).coerceAtLeast(0)
}
