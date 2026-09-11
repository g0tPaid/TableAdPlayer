package com.tableadplayer.app.data.repo

import com.tableadplayer.app.data.local.DeviceStatus
import org.junit.Assert.assertEquals
import org.junit.Test

class RegistrationPolicyTest {
    @Test
    fun successMovesUnregisteredToRegistered() {
        assertEquals(
            DeviceStatus.REGISTERED,
            RegistrationPolicy.nextStatus(
                current = DeviceStatus.UNREGISTERED,
                success = true,
                responseStatus = "REGISTERED",
            ),
        )
    }

    @Test
    fun failureStaysUnregistered() {
        assertEquals(
            DeviceStatus.UNREGISTERED,
            RegistrationPolicy.nextStatus(
                current = DeviceStatus.UNREGISTERED,
                success = false,
                responseStatus = "REGISTERED",
            ),
        )
    }

    @Test
    fun blankRemoteStatusStillRegistersOnSuccess() {
        assertEquals(
            DeviceStatus.REGISTERED,
            RegistrationPolicy.nextStatus(
                current = DeviceStatus.UNREGISTERED,
                success = true,
                responseStatus = null,
            ),
        )
    }
}
