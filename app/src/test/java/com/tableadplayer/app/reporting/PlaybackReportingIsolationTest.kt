package com.tableadplayer.app.reporting

import org.junit.Assert.assertFalse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaybackReportingIsolationTest {
    @Test
    fun emitIsolatedSwallowsSinkExceptions() {
        val ok = PlaybackReporting.emitIsolated(
            sink = { error("reporting exploded") },
            event = QueuedEvent("play", "slide-1"),
        )
        assertFalse(ok)
    }

    @Test
    fun emitIsolatedDeliversToHealthySink() {
        val seen = mutableListOf<QueuedEvent>()
        val ok = PlaybackReporting.emitIsolated(
            sink = { seen += it },
            type = "skip",
            itemId = "x",
            detail = "unreadable",
        )
        assertTrue(ok)
        assertEquals("skip", seen.single().type)
        assertEquals("x", seen.single().itemId)
        assertEquals("unreadable", seen.single().detail)
    }

    @Test
    fun throwingSinkDoesNotPreventSubsequentOffers() {
        val queue = ReportingQueue(capacity = 8)
        PlaybackReporting.emitIsolated(sink = { error("nope") }, event = QueuedEvent("error", "a"))
        assertTrue(PlaybackReporting.emitIsolated(sink = { queue.offer(it) }, event = QueuedEvent("play", "b")))
        assertEquals(1, queue.size)
        assertEquals("b", queue.pollBatch(1).single().itemId)
    }
}
