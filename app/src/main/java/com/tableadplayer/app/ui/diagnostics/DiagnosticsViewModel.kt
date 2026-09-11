package com.tableadplayer.app.ui.diagnostics

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.tableadplayer.app.core.crash.CrashGuard
import com.tableadplayer.app.core.crash.Watchdog
import com.tableadplayer.app.core.diagnostics.DeviceDiagnosticsCollector
import com.tableadplayer.app.core.diagnostics.DiagnosticsExporter
import com.tableadplayer.app.core.diagnostics.DiagnosticsSnapshot
import java.io.File
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class DiagnosticsUiState(
    val loading: Boolean = true,
    val snapshot: DiagnosticsSnapshot? = null,
    val error: String? = null,
    val lastExportPath: String? = null,
    val message: String? = null,
)

class DiagnosticsViewModel(application: Application) : AndroidViewModel(application) {

    private val collector = DeviceDiagnosticsCollector(application)
    private val exporter = DiagnosticsExporter(application)

    private val _state = MutableStateFlow(DiagnosticsUiState())
    val state: StateFlow<DiagnosticsUiState> = _state.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            _state.value = _state.value.copy(loading = true, error = null, message = null)
            runCatching { collector.collect() }
                .onSuccess { snap ->
                    _state.value = _state.value.copy(loading = false, snapshot = snap, error = null)
                }
                .onFailure { err ->
                    _state.value = _state.value.copy(
                        loading = false,
                        error = err.message ?: err.javaClass.simpleName,
                    )
                }
        }
    }

    fun exportToAppStorage(onFile: (File) -> Unit) {
        val snapshot = _state.value.snapshot ?: return
        viewModelScope.launch {
            runCatching { exporter.writeJson(snapshot) }
                .onSuccess { file ->
                    _state.value = _state.value.copy(
                        lastExportPath = file.absolutePath,
                        message = "Saved ${file.name}",
                    )
                    onFile(file)
                }
                .onFailure { err ->
                    _state.value = _state.value.copy(
                        message = "Export failed: ${err.message}",
                    )
                }
        }
    }

    fun exportBytes(): ByteArray? {
        val snapshot = _state.value.snapshot ?: return null
        return exporter.let {
            com.tableadplayer.app.core.json.AppJson.pretty
                .encodeToString(DiagnosticsSnapshot.serializer(), snapshot)
                .toByteArray(Charsets.UTF_8)
        }
    }

    fun retryPlayer() {
        Watchdog.clear(getApplication())
        refresh()
    }

    fun consumeMessage() {
        _state.value = _state.value.copy(message = null)
    }
}
