package com.tableadplayer.app.data.repo

import com.tableadplayer.app.data.local.DeviceStatus

data class DeviceRegistration(
    val deviceId: String,
    val status: String = DeviceStatus.UNREGISTERED,
    val token: String? = null,
    val serverUrl: String,
    val liveApi: Boolean,
    val timezone: String = "UTC",
    val lastHeartbeatAt: String? = null,
) {
    val isRegistered: Boolean get() = status == DeviceStatus.REGISTERED
}

data class DrainResult(
    val attempted: Int,
    val uploaded: Int,
    val failed: Int,
)

data class SyncOutcome(
    val registered: Boolean,
    val playlistFetched: Boolean,
    val downloadsAttempted: Int = 0,
    val downloadsSucceeded: Int = 0,
    val downloadsSkippedLowSpace: Int = 0,
    val pinSwapped: Boolean = false,
    val heartbeatsDrained: Int = 0,
    val eventsDrained: Int = 0,
    val error: String? = null,
)
