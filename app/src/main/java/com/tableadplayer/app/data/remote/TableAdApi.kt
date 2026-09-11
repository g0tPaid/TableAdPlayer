package com.tableadplayer.app.data.remote

import kotlinx.serialization.Serializable
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Query

/**
 * Remote contract for later phases. Paths are versioned under `/v1/` and must be
 * implemented by *your* backend. This client never hard-codes a production CDN.
 */
interface TableAdApi {
    @GET("v1/device/config")
    suspend fun deviceConfig(@Query("deviceId") deviceId: String): DeviceConfigDto

    @GET("v1/playlists/current")
    suspend fun currentPlaylist(@Query("deviceId") deviceId: String): PlaylistDto

    @POST("v1/device/heartbeat")
    suspend fun heartbeat(@Body body: HeartbeatDto): AckDto

    @POST("v1/device/events")
    suspend fun reportEvents(@Body body: EventBatchDto): AckDto
}

@Serializable
data class DeviceConfigDto(
    val deviceId: String,
    val timezone: String = "UTC",
    val heartbeatIntervalSec: Int = 60,
    val syncIntervalSec: Int = 300,
)

@Serializable
data class PlaylistDto(
    val playlistId: String,
    val revision: Long,
    val items: List<RemoteMediaDto> = emptyList(),
)

@Serializable
data class RemoteMediaDto(
    val id: String,
    val type: String,
    val url: String,
    val sha256: String? = null,
    val durationMs: Long? = null,
    val startAt: String? = null,
    val endAt: String? = null,
    val startDate: String? = null,
    val endDate: String? = null,
    val startTime: String? = null,
    val endTime: String? = null,
    val daysOfWeek: String? = null,
)

@Serializable
data class HeartbeatDto(
    val deviceId: String,
    val appVersion: String,
    val capturedAt: String,
    val playbackItemId: String? = null,
)

@Serializable
data class EventBatchDto(
    val deviceId: String,
    val events: List<PlayerEventDto>,
)

@Serializable
data class PlayerEventDto(
    val type: String,
    val itemId: String? = null,
    val at: String,
    val detail: String? = null,
)

@Serializable
data class AckDto(
    val ok: Boolean = true,
)
