package com.tableadplayer.app.reporting

import com.tableadplayer.app.data.repo.ReportingRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Moves [ReportingQueue] events into Room, then drains the outbox. Runs on an
 * application scope — never on the playlist job. Persist/API failures are
 * swallowed so they cannot cancel sibling coroutines.
 */
class ReportingPump(
    private val queue: ReportingQueue,
    private val repository: ReportingRepository,
    scope: CoroutineScope,
    private val idleDelayMs: Long = IDLE_DELAY_MS,
    private val persistFailDelayMs: Long = PERSIST_FAIL_DELAY_MS,
) {
    init {
        scope.launch {
            while (isActive) {
                val batch = queue.pollBatch(PlaybackEventOutbox.DRAIN_BATCH)
                if (batch.isEmpty()) {
                    delay(idleDelayMs)
                    continue
                }
                var persistFailed = false
                for (event in batch) {
                    val ok = runCatching {
                        repository.enqueuePlaybackEvent(event)
                        true
                    }.getOrDefault(false)
                    if (!ok) {
                        persistFailed = true
                        queue.offer(event)
                    }
                }
                runCatching { repository.drainPlaybackEvents() }
                if (persistFailed) delay(persistFailDelayMs)
            }
        }
    }

    companion object {
        const val IDLE_DELAY_MS = 1_000L
        const val PERSIST_FAIL_DELAY_MS = 5_000L
    }
}
