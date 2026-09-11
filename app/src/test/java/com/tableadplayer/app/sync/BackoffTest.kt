package com.tableadplayer.app.sync

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BackoffTest {
    @Test
    fun growsThenCaps() {
        assertEquals(2_000L, Backoff.delayMs(0))
        assertEquals(4_000L, Backoff.delayMs(1))
        assertTrue(Backoff.delayMs(20) <= 15 * 60 * 1000L)
    }
}
