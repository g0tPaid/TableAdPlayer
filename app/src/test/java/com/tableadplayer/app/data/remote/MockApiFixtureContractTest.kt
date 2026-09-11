package com.tableadplayer.app.data.remote

import com.tableadplayer.app.core.json.AppJson
import com.tableadplayer.app.testutil.RepoRoot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test

/**
 * Contract: `server/fixtures` (mock_api.py) and `assets/fixtures` (DEMO / FixtureTableAdApi)
 * must deserialize to the same DTO shape the app ships.
 */
class MockApiFixtureContractTest {
    private val json = AppJson.compact

    @Test
    fun serverFixturesExist() {
        assumeTrue("server/fixtures missing", RepoRoot.serverFixture("playlist.json").isFile)
        assertTrue(RepoRoot.serverFixture("register-response.json").isFile)
        assertTrue(RepoRoot.serverFixture("device-config.json").isFile)
        assertTrue(RepoRoot.serverFixture("heartbeat.json").isFile)
        assertTrue(RepoRoot.assetFixture("playlist.json").isFile)
    }

    @Test
    fun registerFixtureParses() {
        val dto = json.decodeFromString(
            RegisterResponseDto.serializer(),
            RepoRoot.readServerFixture("register-response.json"),
        )
        assertEquals("TABLE-abcd1234", dto.deviceId)
        assertEquals("REGISTERED", dto.status)
        assertEquals("mock-device-token", dto.token)
        assertEquals("UTC", dto.timezone)
        assertEquals(60, dto.heartbeatIntervalSec)
        assertEquals(300, dto.syncIntervalSec)
    }

    @Test
    fun configFixtureParses() {
        val dto = json.decodeFromString(
            DeviceConfigDto.serializer(),
            RepoRoot.readServerFixture("device-config.json"),
        )
        assertEquals("TABLE-abcd1234", dto.deviceId)
        assertEquals(60, dto.heartbeatIntervalSec)
    }

    @Test
    fun playlistFixtureHasSha256AndRelativeMediaUrls() {
        val dto = json.decodeFromString(
            PlaylistDto.serializer(),
            RepoRoot.readServerFixture("playlist.json"),
        )
        assertEquals("venue-lobby", dto.playlistId)
        assertEquals(2L, dto.revision)
        assertEquals(3, dto.items.size)
        val welcome = dto.items.first { it.id == "slide-welcome" }
        assertEquals("image", welcome.type)
        assertEquals("/v1/media/welcome.png", welcome.url)
        assertEquals(5000L, welcome.durationMs)
        assertEquals(64, welcome.sha256!!.length)
        assertTrue(dto.items.any { it.type == "video" && it.url.startsWith("/v1/media/") })
        assertTrue(dto.items.any { !it.daysOfWeek.isNullOrBlank() })
        assertFalse(dto.items.any { it.url.contains("cnszfyd", ignoreCase = true) })
        assertFalse(dto.items.any { it.url.contains("ad.cnszfyd.cn", ignoreCase = true) })
    }

    @Test
    fun heartbeatFixtureParses() {
        val dto = json.decodeFromString(
            HeartbeatDto.serializer(),
            RepoRoot.readServerFixture("heartbeat.json"),
        )
        assertEquals("TABLE-abcd1234", dto.deviceId)
        assertEquals("venue-lobby", dto.playlistId)
        assertEquals(2L, dto.playlistRevision)
        assertEquals("wifi", dto.network)
    }

    @Test
    fun assetFixturesStayInSyncWithServerMock() {
        assertEquals(
            normalize(RepoRoot.readServerFixture("playlist.json")),
            normalize(RepoRoot.assetFixture("playlist.json").readText()),
        )
        assertEquals(
            normalize(RepoRoot.readServerFixture("register-response.json")),
            normalize(RepoRoot.assetFixture("register-response.json").readText()),
        )
        assertEquals(
            normalize(RepoRoot.readServerFixture("device-config.json")),
            normalize(RepoRoot.assetFixture("device-config.json").readText()),
        )
        assertEquals(
            normalize(RepoRoot.readServerFixture("heartbeat.json")),
            normalize(RepoRoot.assetFixture("heartbeat.json").readText()),
        )
    }

    @Test
    fun unknownKeysAreIgnoredSoServersCanAddFields() {
        val extra = """
            {"playlistId":"x","revision":1,"items":[],"extraServerField":true}
        """.trimIndent()
        val dto = json.decodeFromString(PlaylistDto.serializer(), extra)
        assertEquals("x", dto.playlistId)
        assertTrue(dto.items.isEmpty())
    }

    private fun normalize(raw: String): String = json.parseToJsonElement(raw).toString()
}
