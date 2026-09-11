package com.tableadplayer.app.reporting

/**
 * In-memory front door for playback telemetry. [offer] never blocks, never
 * throws, and drops the oldest event when full so the playlist loop cannot
 * stall on reporting.
 *
 * A [ReportingPump] persists batches into Room; [com.tableadplayer.app.data.repo.ReportingRepository]
 * drains Room to the API. Process death may lose events still only in this
 * buffer — that is preferred over hitching video.
 */
class ReportingQueue(val capacity: Int = DEFAULT_CAPACITY) {
    private val lock = Any()
    private val deque = ArrayDeque<QueuedEvent>(capacity.coerceAtLeast(1))

    val size: Int
        get() = synchronized(lock) { deque.size }

    /**
     * Non-blocking enqueue. Returns false only when [event] is rejected
     * (invalid type); a full queue still accepts by dropping the oldest.
     */
    fun offer(event: QueuedEvent): Boolean {
        return runCatching {
            synchronized(lock) {
                while (deque.size >= capacity && deque.isNotEmpty()) {
                    deque.removeFirst()
                }
                if (capacity <= 0) return@runCatching false
                deque.addLast(event)
                true
            }
        }.getOrDefault(false)
    }

    fun pollBatch(limit: Int): List<QueuedEvent> {
        if (limit <= 0) return emptyList()
        return synchronized(lock) {
            val n = minOf(limit, deque.size)
            buildList(n) {
                repeat(n) { add(deque.removeFirst()) }
            }
        }
    }

    fun snapshot(): List<QueuedEvent> = synchronized(lock) { deque.toList() }

    fun clear() {
        synchronized(lock) { deque.clear() }
    }

    companion object {
        const val DEFAULT_CAPACITY = 512
    }
}

data class QueuedEvent(
    val type: String,
    val itemId: String? = null,
    val atEpochMs: Long = System.currentTimeMillis(),
    val detail: String? = null,
    val playlistId: String? = null,
)
