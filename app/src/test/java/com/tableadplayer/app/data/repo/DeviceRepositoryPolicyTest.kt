package com.tableadplayer.app.data.repo

import com.tableadplayer.app.data.local.DeviceStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DeviceRepositoryPolicyTest {
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
    fun failureStaysUnregisteredOnFirstLaunch() {
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
    fun failureKeepsExistingRegisteredStatus() {
        assertEquals(
            DeviceStatus.REGISTERED,
            RegistrationPolicy.nextStatus(
                current = DeviceStatus.REGISTERED,
                success = false,
                responseStatus = null,
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
        assertEquals(
            DeviceStatus.REGISTERED,
            RegistrationPolicy.nextStatus(
                current = DeviceStatus.UNREGISTERED,
                success = true,
                responseStatus = "  ",
            ),
        )
    }

    @Test
    fun unknownRemoteStatusIsPreservedUppercased() {
        assertEquals(
            "PENDING_APPROVAL",
            RegistrationPolicy.nextStatus(
                current = DeviceStatus.UNREGISTERED,
                success = true,
                responseStatus = "pending_approval",
            ),
        )
    }

    @Test
    fun lowercaseRegisteredNormalizes() {
        assertEquals(
            DeviceStatus.REGISTERED,
            RegistrationPolicy.nextStatus(
                current = DeviceStatus.UNREGISTERED,
                success = true,
                responseStatus = "registered",
            ),
        )
    }

    @Test
    fun utcNowIsZuluWithoutMillis() {
        val iso = DeviceRepository.utcNow(0L)
        assertEquals("1970-01-01T00:00:00Z", iso)
        val later = DeviceRepository.utcNow(1_704_067_200_000L) // 2024-01-01T00:00:00Z
        assertTrue(later.matches(Regex("\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}Z")))
        assertTrue(later.endsWith("Z"))
    }

    @Test
    fun registrationFlagOnlyTrueForRegistered() {
        val base = DeviceRegistration(
            deviceId = "TABLE-abcd1234",
            serverUrl = "https://api.example.invalid/",
            liveApi = false,
        )
        assertFalse(base.copy(status = DeviceStatus.UNREGISTERED).isRegistered)
        assertTrue(base.copy(status = DeviceStatus.REGISTERED).isRegistered)
        assertFalse(base.copy(status = "PENDING_APPROVAL").isRegistered)
    }
}
