package com.tableadplayer.app.data.remote

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FixtureTableAdApiTest {
    private val fixtures = mapOf(
        "register-response.json" to """
            {"deviceId":"TABLE-abcd1234","status":"REGISTERED","token":"mock-device-token",
             "timezone":"UTC","heartbeatIntervalSec":60,"syncIntervalSec":300}
        """.trimIndent(),
        "device-config.json" to """
            {"deviceId":"TABLE-abcd1234","timezone":"UTC","heartbeatIntervalSec":60,"syncIntervalSec":300}
        """.trimIndent(),
        "playlist.json" to """
            {"playlistId":"venue-lobby","revision":2,"items":[
              {"id":"slide-welcome","type":"image","url":"/v1/media/welcome.png","durationMs":5000}
            ]}
        """.trimIndent(),
    )

    private val api = FixtureTableAdApi { name -> fixtures.getValue(name) }

    @Test
    fun registerEchoesDeviceIdAndMarksRegistered() = runTest {
        val response = api.register(
            RegisterRequestDto(
                deviceId = "TABLE-deadbeef",
                appVersion = "0.6.0",
                applicationId = "com.tableadplayer.app.debug",
                demoMode = true,
            ),
        )
        assertEquals("TABLE-deadbeef", response.deviceId)
        assertEquals("REGISTERED", response.status)
        assertEquals("mock-device-token", response.token)
    }

    @Test
    fun configAndPlaylistAndHeartbeat() = runTest {
        val cfg = api.deviceConfig("TABLE-ffff0000")
        assertEquals("TABLE-ffff0000", cfg.deviceId)
        val playlist = api.currentPlaylist("TABLE-ffff0000")
        assertEquals("venue-lobby", playlist.playlistId)
        assertEquals(2L, playlist.revision)
        assertTrue(api.heartbeat(HeartbeatDto("TABLE-ffff0000", "0.6.0", "2026-01-01T00:00:00Z")).ok)
    }
}
