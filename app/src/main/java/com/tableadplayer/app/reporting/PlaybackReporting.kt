package com.tableadplayer.app.reporting

/**
 * Fire-and-forget helpers so [com.tableadplayer.app.playback.PlaylistEngine]
 * never `join()`s reporting work.
 */
object PlaybackReporting {
    /**
     * Invoke [sink] without throwing. A throwing or slow-to-return sink is the
     * caller's problem only if they block inside [sink] — the engine must pass
     * a non-suspending, non-blocking sink (typically [ReportingQueue.offer]).
     */
    fun emitIsolated(sink: (QueuedEvent) -> Unit, event: QueuedEvent): Boolean {
        return runCatching {
            sink(event)
            true
        }.getOrDefault(false)
    }

    fun emitIsolated(sink: (QueuedEvent) -> Unit, type: String, itemId: String?, detail: String? = null, playlistId: String? = null, atEpochMs: Long = System.currentTimeMillis()): Boolean {
        return emitIsolated(
            sink,
            QueuedEvent(
                type = type,
                itemId = itemId,
                atEpochMs = atEpochMs,
                detail = detail,
                playlistId = playlistId,
            ),
        )
    }
}
