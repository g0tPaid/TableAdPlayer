package com.tableadplayer.app.sync

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FreeSpacePolicyTest {
    @Test
    fun nonessentialStopsBelowReserve() {
        val reserve = FreeSpacePolicy.DEFAULT_RESERVE_BYTES
        assertTrue(FreeSpacePolicy.allowDownload(reserve + 1, essential = false, reserveBytes = reserve))
        assertFalse(FreeSpacePolicy.allowDownload(reserve, essential = false, reserveBytes = reserve))
        assertFalse(FreeSpacePolicy.allowDownload(1_000L, essential = false, reserveBytes = reserve))
    }

    @Test
    fun essentialContinuesUntilCritical() {
        val reserve = FreeSpacePolicy.DEFAULT_RESERVE_BYTES
        assertTrue(FreeSpacePolicy.allowDownload(reserve / 2, essential = true, reserveBytes = reserve))
        assertFalse(
            FreeSpacePolicy.allowDownload(
                FreeSpacePolicy.CRITICAL_BYTES,
                essential = true,
                reserveBytes = reserve,
            ),
        )
    }
}
