package com.tableadplayer.app.ui.admin

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.tableadplayer.app.BuildConfig
import com.tableadplayer.app.TableAdPlayerApp
import com.tableadplayer.app.core.diagnostics.DeviceDiagnosticsCollector
import com.tableadplayer.app.core.diagnostics.DiagnosticsExporter
import com.tableadplayer.app.core.json.AppJson
import com.tableadplayer.app.data.local.AppConfigEntity
import com.tableadplayer.app.data.local.AppConfigKeys
import com.tableadplayer.app.data.local.DeviceStatus
import com.tableadplayer.app.data.local.SyncJobKind
import com.tableadplayer.app.data.local.SyncJobStatus
import com.tableadplayer.app.data.repo.DeviceRegistration
import com.tableadplayer.app.data.repo.ReportingPendingSnapshot
import com.tableadplayer.app.data.repo.SyncOutcome
import com.tableadplayer.app.data.seed.DemoPlaylistSeeder
import java.io.File
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable

data class AdminUiState(
    val registration: DeviceRegistration = DeviceRegistration(
        deviceId = "…",
        status = DeviceStatus.UNREGISTERED,
        serverUrl = BuildConfig.API_BASE_URL,
        liveApi = false,
    ),
    val playlistId: String = DemoPlaylistSeeder.PLAYLIST_ID,
    val playlistRevision: Long = 0L,
    val demoPlaylist: Boolean = true,
    val pendingDownloads: Int = 0,
    val failedDownloads: Int = 0,
    val runningDownloads: Int = 0,
    val downloadErrors: List<String> = emptyList(),
    val reporting: ReportingPendingSnapshot = ReportingPendingSnapshot(),
    val lastSync: SyncOutcome? = null,
    val syncing: Boolean = false,
    val brightness: Float = 1f,
    val message: String? = null,
    val busy: Boolean = false,
)

class AdminViewModel(application: Application) : AndroidViewModel(application) {

    private val _state = MutableStateFlow(AdminUiState())
    val state: StateFlow<AdminUiState> = _state.asStateFlow()

    private val exporter = DiagnosticsExporter(application)
    private val collector = DeviceDiagnosticsCollector(application)

    private val app: TableAdPlayerApp? get() = getApplication<Application>() as? TableAdPlayerApp

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            val container = app?.container ?: return@launch
            runCatching {
                val reg = container.deviceRepository.registration()
                val active = container.database.playlistDao().getActive()
                val pending = container.database.syncJobDao()
                    .countByKindAndStatus(SyncJobKind.MEDIA_DOWNLOAD, SyncJobStatus.PENDING)
                val failed = container.database.syncJobDao()
                    .countByKindAndStatus(SyncJobKind.MEDIA_DOWNLOAD, SyncJobStatus.FAILED)
                val running = container.database.syncJobDao()
                    .countByKindAndStatus(SyncJobKind.MEDIA_DOWNLOAD, SyncJobStatus.RUNNING)
                val errors = container.database.syncJobDao().recentErrors(8)
                val reporting = container.reportingRepository.pendingSnapshot()
                val brightness = container.database.appConfigDao()
                    .getValue(AppConfigKeys.SCREEN_BRIGHTNESS)
                    ?.toFloatOrNull()
                    ?.coerceIn(0.1f, 1f)
                    ?: 1f
                _state.value = _state.value.copy(
                    registration = reg,
                    playlistId = active?.id ?: DemoPlaylistSeeder.PLAYLIST_ID,
                    playlistRevision = active?.revision ?: 0L,
                    demoPlaylist = active?.id == null || active.id == DemoPlaylistSeeder.PLAYLIST_ID,
                    pendingDownloads = pending,
                    failedDownloads = failed,
                    runningDownloads = running,
                    downloadErrors = errors,
                    reporting = reporting,
                    brightness = brightness,
                )
            }
        }
    }

    fun syncNow() {
        val container = app?.container ?: return
        viewModelScope.launch {
            _state.value = _state.value.copy(syncing = true, message = null)
            val outcome = runCatching { container.syncRepository.sync() }
                .getOrElse { err ->
                    SyncOutcome(registered = false, playlistFetched = false, error = err.message)
                }
            _state.value = _state.value.copy(syncing = false, lastSync = outcome, message = syncMessage(outcome))
            refresh()
        }
    }

    fun clearCache() {
        val container = app?.container ?: return
        viewModelScope.launch {
            _state.value = _state.value.copy(busy = true)
            runCatching {
                container.mediaCache.discardStalePartFiles()
                container.mediaCache.cleanup(maxReadyBytesOverride = 0L)
            }
            _state.value = _state.value.copy(
                busy = false,
                message = "Cache cleared (active playlist media kept)",
            )
            refresh()
        }
    }

    fun setBrightness(value: Float) {
        val clamped = value.coerceIn(0.1f, 1f)
        _state.value = _state.value.copy(brightness = clamped)
        viewModelScope.launch {
            val db = app?.container?.database ?: return@launch
            db.appConfigDao().upsert(
                AppConfigEntity(
                    key = AppConfigKeys.SCREEN_BRIGHTNESS,
                    value = clamped.toString(),
                    updatedAt = System.currentTimeMillis(),
                ),
            )
        }
    }

    fun exportServiceBundle(onFile: (File) -> Unit) {
        viewModelScope.launch {
            val container = app?.container ?: return@launch
            runCatching {
                val snap = collector.collect()
                val bundle = ServiceBundle(
                    diagnostics = snap,
                    reporting = _state.value.reporting,
                    downloads = DownloadDiagnostics(
                        pending = _state.value.pendingDownloads,
                        failed = _state.value.failedDownloads,
                        running = _state.value.runningDownloads,
                        lastErrors = _state.value.downloadErrors,
                    ),
                    playlistId = _state.value.playlistId,
                    playlistRevision = _state.value.playlistRevision,
                )
                val json = AppJson.pretty.encodeToString(ServiceBundle.serializer(), bundle)
                exporter.writeNamed("service-bundle", snap.deviceId, json)
            }.onSuccess { file ->
                _state.value = _state.value.copy(message = "Saved ${file.name}")
                onFile(file)
            }.onFailure { err ->
                _state.value = _state.value.copy(message = "Export failed: ${err.message}")
            }
        }
    }

    fun consumeMessage() {
        _state.value = _state.value.copy(message = null)
    }

    private fun syncMessage(outcome: SyncOutcome): String {
        if (outcome.error != null) return "Sync finished with error: ${outcome.error}"
        return "Sync ok — downloads ${outcome.downloadsSucceeded}/${outcome.downloadsAttempted}, " +
            "events ${outcome.eventsDrained}, heartbeats ${outcome.heartbeatsDrained}"
    }
}

@Serializable
data class ServiceBundle(
    val diagnostics: com.tableadplayer.app.core.diagnostics.DiagnosticsSnapshot,
    val reporting: ReportingPendingSnapshot,
    val downloads: DownloadDiagnostics,
    val playlistId: String,
    val playlistRevision: Long,
)

@Serializable
data class DownloadDiagnostics(
    val pending: Int,
    val failed: Int,
    val running: Int,
    val lastErrors: List<String>,
)
