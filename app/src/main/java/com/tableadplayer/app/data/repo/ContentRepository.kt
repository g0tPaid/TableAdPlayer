package com.tableadplayer.app.data.repo

import com.tableadplayer.app.data.remote.PlaylistDto
import com.tableadplayer.app.data.remote.TableAdApi

class ContentRepository(
    private val api: TableAdApi,
    private val devices: DeviceRepository,
) {
    suspend fun currentPlaylist(): Result<PlaylistDto> {
        val id = devices.deviceId()
        return runCatching { api.currentPlaylist(id) }
    }
}
