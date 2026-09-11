package com.tableadplayer.app.core.crash

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WatchdogPolicyTest {
    @Test
    fun firstStartCountsAsOne() {
        assertEquals(1, WatchdogPolicy.nextCount(0, lastAtMs = 0L, nowMs = 1_000L))
    }

    @Test
    fun debounceIgnoresStickyServiceRedelivery() {
        val t0 = 10_000L
        assertEquals(1, WatchdogPolicy.nextCount(1, lastAtMs = t0, nowMs = t0 + 1_000L))
        assertEquals(2, WatchdogPolicy.nextCount(1, lastAtMs = t0, nowMs = t0 + WatchdogPolicy.DEBOUNCE_MS + 1))
    }

    @Test
    fun windowExpiryResetsCount() {
        val t0 = 10_000L
        val later = t0 + WatchdogPolicy.WINDOW_MS + 1
        assertEquals(1, WatchdogPolicy.nextCount(3, lastAtMs = t0, nowMs = later))
    }

    @Test
    fun threeRestartsTripSafeMode() {
        var count = 0
        var last = 0L
        var now = 100_000L
        repeat(3) {
            count = WatchdogPolicy.nextCount(count, last, now)
            last = now
            now += 10_000L
        }
        assertEquals(3, count)
        assertTrue(WatchdogPolicy.enterSafeMode(count))
        assertFalse(WatchdogPolicy.allowPlayerStart(safeMode = false, count = count))
    }

    @Test
    fun twoRestartsStillAllowPlayer() {
        assertTrue(WatchdogPolicy.allowPlayerStart(safeMode = false, count = 2))
        assertFalse(WatchdogPolicy.allowPlayerStart(safeMode = true, count = 0))
    }
}
