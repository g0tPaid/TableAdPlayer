package com.tableadplayer.app.reporting

import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.receiveAsFlow

/**
 * In-memory outbox until Room (Phase 7). Playback posts events and continues;
 * a background consumer drains the queue. Full = drop oldest diagnostic noise,
 * never stall the player.
 */
class ReportingQueue(capacity: Int = 512) {
    private val channel = Channel<QueuedEvent>(capacity = capacity)

    suspend fun offer(event: QueuedEvent): Boolean {
        return channel.trySend(event).isSuccess
    }

    fun events() = channel.receiveAsFlow()
}

data class QueuedEvent(
    val type: String,
    val itemId: String? = null,
    val atEpochMs: Long = System.currentTimeMillis(),
    val detail: String? = null,
)
