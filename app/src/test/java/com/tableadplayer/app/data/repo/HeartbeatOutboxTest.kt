package com.tableadplayer.app.data.repo

import com.tableadplayer.app.sync.Backoff
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HeartbeatOutboxTest {
    @Test
    fun dropsOldestWhenAtCapacity() {
        assertFalse(HeartbeatOutbox.shouldDropOldest(0))
        assertFalse(HeartbeatOutbox.shouldDropOldest(HeartbeatOutbox.MAX_PENDING - 1))
        assertTrue(HeartbeatOutbox.shouldDropOldest(HeartbeatOutbox.MAX_PENDING))
        assertTrue(HeartbeatOutbox.shouldDropOldest(HeartbeatOutbox.MAX_PENDING + 10))
    }

    @Test
    fun retryUsesBackoff() {
        val now = 1_000_000L
        assertEquals(now + Backoff.delayMs(1), HeartbeatOutbox.nextAttemptAt(1, now))
        assertEquals(now + Backoff.delayMs(5), HeartbeatOutbox.nextAttemptAt(5, now))
    }
}
