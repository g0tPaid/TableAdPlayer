package com.tableadplayer.app.reporting

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReportingQueueTest {
    @Test
    fun dropsOldestWhenFullAndNeverExceedsCapacity() {
        val queue = ReportingQueue(capacity = 3)
        assertTrue(queue.offer(QueuedEvent("play", "a")))
        assertTrue(queue.offer(QueuedEvent("play", "b")))
        assertTrue(queue.offer(QueuedEvent("play", "c")))
        assertTrue(queue.offer(QueuedEvent("play", "d")))
        assertEquals(3, queue.size)
        val batch = queue.pollBatch(10)
        assertEquals(listOf("b", "c", "d"), batch.map { it.itemId })
    }

    @Test
    fun offerDoesNotThrowWhenSinkWouldFail() {
        val queue = ReportingQueue(capacity = 2)
        repeat(50) { i ->
            assertTrue(queue.offer(QueuedEvent("error", "item-$i", detail = "boom")))
        }
        assertEquals(2, queue.size)
    }

    @Test
    fun pollBatchIsEmptyWhenIdle() {
        val queue = ReportingQueue()
        assertTrue(queue.pollBatch(8).isEmpty())
        assertTrue(queue.snapshot().isEmpty())
    }
}
