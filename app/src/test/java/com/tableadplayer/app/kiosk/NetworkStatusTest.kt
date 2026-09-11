package com.tableadplayer.app.kiosk

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NetworkStatusTest {
    @Test
    fun onlineRequiresInternet() {
        assertFalse(NetworkStatus.isOnline(hasInternet = false, validated = true))
        assertTrue(NetworkStatus.isOnline(hasInternet = true, validated = true))
        assertTrue(NetworkStatus.isOnline(hasInternet = true, validated = null))
        assertFalse(NetworkStatus.isOnline(hasInternet = true, validated = false))
    }
}

class LockTaskGateTest {
    @Test
    fun exitOnlyWhenPinnedAndDpcPermits() {
        assertFalse(LockTaskGate.canRequestStopLockTask(inLockTask = false, permittedByDpc = false))
        assertFalse(LockTaskGate.canRequestStopLockTask(inLockTask = true, permittedByDpc = false))
        assertFalse(LockTaskGate.canRequestStopLockTask(inLockTask = false, permittedByDpc = true))
        assertTrue(LockTaskGate.canRequestStopLockTask(inLockTask = true, permittedByDpc = true))
    }
}
