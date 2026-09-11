package com.tableadplayer.app.data.remote

import android.content.res.AssetManager
import com.tableadplayer.app.core.json.AppJson
import kotlinx.serialization.json.Json

/**
 * Offline stand-in for [TableAdApi]. Reads `assets/fixtures/` so DEMO MODE works
 * with no server and no DNS lookup of the placeholder origin.
 */
class FixtureTableAdApi(
    private val readFixture: (String) -> String,
    private val json: Json = AppJson.compact,
) : TableAdApi {

    override suspend fun register(body: RegisterRequestDto): RegisterResponseDto {
        val template = json.decodeFromString(RegisterResponseDto.serializer(), readFixture(REGISTER))
        return template.copy(
            deviceId = body.deviceId,
            status = "REGISTERED",
            token = template.token?.takeIf { it.isNotBlank() } ?: "fixture-${body.deviceId}",
        )
    }

    override suspend fun deviceConfig(deviceId: String): DeviceConfigDto {
        val template = json.decodeFromString(DeviceConfigDto.serializer(), readFixture(CONFIG))
        return template.copy(deviceId = deviceId)
    }

    override suspend fun currentPlaylist(deviceId: String): PlaylistDto {
        return json.decodeFromString(PlaylistDto.serializer(), readFixture(PLAYLIST))
    }

    override suspend fun heartbeat(body: HeartbeatDto): AckDto = AckDto(ok = true)

    override suspend fun reportEvents(body: EventBatchDto): AckDto = AckDto(ok = true)

    companion object {
        const val REGISTER = "register-response.json"
        const val CONFIG = "device-config.json"
        const val PLAYLIST = "playlist.json"

        fun fromAssets(assets: AssetManager): FixtureTableAdApi {
            return FixtureTableAdApi { name ->
                assets.open("fixtures/$name").bufferedReader().use { it.readText() }
            }
        }
    }
}
