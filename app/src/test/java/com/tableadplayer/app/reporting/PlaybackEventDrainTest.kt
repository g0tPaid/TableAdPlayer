package com.tableadplayer.app.reporting

import com.tableadplayer.app.data.remote.AckDto
import com.tableadplayer.app.data.remote.EventBatchDto
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaybackEventDrainTest {
    private fun row(id: Long, type: String = "play", attempts: Int = 0) = PlaybackEventDrain.OutboxRow(
        id = id,
        type = type,
        mediaId = "slide-$id",
        playlistId = "demo-local",
        payloadJson = null,
        createdAt = 1_700_000_000_000L,
        attempts = attempts,
    )

    @Test
    fun successfulAckMarksAllUploaded() = runTest {
        val received = mutableListOf<EventBatchDto>()
        val outcome = PlaybackEventDrain.drain(
            pending = listOf(row(1), row(2, "skip")),
            deviceId = "TABLE-abcd1234",
            nowMs = 10L,
            post = { batch ->
                received += batch
                AckDto(ok = true)
            },
        )
        assertEquals(2, outcome.result.uploaded)
        assertEquals(0, outcome.result.failed)
        assertEquals(listOf(1L, 2L), outcome.uploadedIds)
        assertTrue(outcome.retries.isEmpty())
        assertEquals("TABLE-abcd1234", received.single().deviceId)
        assertEquals(listOf("play", "skip"), received.single().events.map { it.type })
    }

    @Test
    fun apiFailureKeepsEventsForRetryAndDoesNotThrow() = runTest {
        val outcome = PlaybackEventDrain.drain(
            pending = listOf(row(9, "error")),
            deviceId = "TABLE-ffff0000",
            nowMs = 50L,
            post = { error("offline") },
        )
        assertEquals(0, outcome.result.uploaded)
        assertEquals(1, outcome.result.failed)
        assertTrue(outcome.uploadedIds.isEmpty())
        assertEquals(1, outcome.retries.size)
        assertEquals(9L, outcome.retries.single().id)
        assertEquals(1, outcome.retries.single().attempts)
        assertFalse(outcome.retries.single().drop)
    }

    @Test
    fun ackNotOkIsIsolatedFailure() = runTest {
        val outcome = PlaybackEventDrain.drain(
            pending = listOf(row(3, "completed")),
            deviceId = "TABLE-deadbeef",
            nowMs = 1L,
            post = { AckDto(ok = false) },
        )
        assertEquals(0, outcome.result.uploaded)
        assertEquals(1, outcome.result.failed)
        assertEquals(1, outcome.retries.size)
    }

    @Test
    fun heartbeatRowsArePoisonAndNotPosted() = runTest {
        var posted = 0
        val outcome = PlaybackEventDrain.drain(
            pending = listOf(row(4, "heartbeat")),
            deviceId = "TABLE-abcd1234",
            nowMs = 1L,
            post = {
                posted += 1
                AckDto(ok = true)
            },
        )
        assertEquals(0, posted)
        assertEquals(listOf(4L), outcome.droppedPoisonIds)
        assertEquals(0, outcome.result.uploaded)
    }

    @Test
    fun exhaustedAttemptsAreDropped() = runTest {
        val outcome = PlaybackEventDrain.drain(
            pending = listOf(row(5, "play", attempts = PlaybackEventOutbox.MAX_ATTEMPTS - 1)),
            deviceId = "TABLE-abcd1234",
            nowMs = 1L,
            post = { error("still down") },
        )
        assertTrue(outcome.retries.single().drop)
    }

    @Test
    fun emptyPendingIsNoop() = runTest {
        val outcome = PlaybackEventDrain.drain(
            pending = emptyList(),
            deviceId = "TABLE-abcd1234",
            nowMs = 1L,
            post = { error("should not be called") },
        )
        assertEquals(0, outcome.result.attempted)
    }
}
