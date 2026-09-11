package com.tableadplayer.app.core.diagnostics

import kotlinx.serialization.Serializable

@Serializable
data class DiagnosticsSnapshot(
    val capturedAt: String,
    val deviceId: String,
    val androidId: String?,
    val app: AppDiagnostics,
    val os: OsDiagnostics,
    val hardware: HardwareDiagnostics,
    val display: DisplayDiagnostics,
    val memory: MemoryDiagnostics,
    val storage: StorageDiagnostics,
    val battery: BatteryDiagnostics,
    val network: NetworkDiagnostics,
    val registration: RegistrationDiagnostics,
    val safeMode: Boolean,
)

@Serializable
data class AppDiagnostics(
    val versionName: String,
    val versionCode: Long,
    val applicationId: String,
    val buildType: String,
    val demoMode: Boolean,
    val apiBaseUrl: String,
)

@Serializable
data class OsDiagnostics(
    val androidVersion: String,
    val apiLevel: Int,
    val securityPatch: String?,
    val fingerprint: String,
)

@Serializable
data class HardwareDiagnostics(
    val manufacturer: String,
    val brand: String,
    val model: String,
    val device: String,
    val product: String,
    val board: String,
    val hardware: String,
    val deviceName: String?,
    val cpuAbis: List<String>,
)

@Serializable
data class DisplayDiagnostics(
    val widthPx: Int,
    val heightPx: Int,
    val density: Float,
    val densityDpi: Int,
    val scaledDensity: Float,
    val refreshRateHz: Float?,
)

@Serializable
data class MemoryDiagnostics(
    val totalRamBytes: Long,
    val availableRamBytes: Long,
    val lowMemory: Boolean,
    val runtimeMaxBytes: Long,
    val runtimeUsedBytes: Long,
)

@Serializable
data class StorageDiagnostics(
    val dataTotalBytes: Long,
    val dataFreeBytes: Long,
    val appFilesDirBytes: Long?,
)

@Serializable
data class BatteryDiagnostics(
    val percent: Int?,
    val charging: Boolean,
    val plugged: String?,
    val status: String?,
    val health: String?,
    val temperatureC: Float?,
)

@Serializable
data class NetworkDiagnostics(
    val connected: Boolean,
    val wifi: Boolean,
    val ethernet: Boolean,
    val cellular: Boolean,
    val wifiSsid: String?,
    val linkDownstreamKbps: Int?,
    val interfaces: List<NetworkInterfaceInfo>,
)

@Serializable
data class NetworkInterfaceInfo(
    val name: String,
    val up: Boolean,
    val addresses: List<String>,
)

@Serializable
data class RegistrationDiagnostics(
    val status: String,
    val serverUrl: String,
    val liveApi: Boolean,
    val lastHeartbeatAt: String? = null,
)
