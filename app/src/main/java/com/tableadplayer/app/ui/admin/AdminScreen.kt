package com.tableadplayer.app.ui.admin

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import java.util.Locale

private val TouchMin = 64.dp

@Composable
fun AdminScreen(
    state: AdminUiState,
    canExitLockTask: Boolean,
    onRefresh: () -> Unit,
    onSyncNow: () -> Unit,
    onClearCache: () -> Unit,
    onBrightness: (Float) -> Unit,
    onDiagnostics: () -> Unit,
    onExport: () -> Unit,
    onReloadPlaylist: () -> Unit,
    onRestartPlayer: () -> Unit,
    onRestartApp: () -> Unit,
    onExitLockTask: () -> Unit,
    onBackToPlayer: () -> Unit,
    onMessageShown: () -> Unit,
) {
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(state.message) {
        val msg = state.message ?: return@LaunchedEffect
        snackbar.showSnackbar(msg)
        onMessageShown()
    }

    Column(Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            contentPadding = PaddingValues(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item {
                Text("Service menu", style = MaterialTheme.typography.headlineMedium)
                Text(
                    "Long-press the top-left corner of the player. Designed for 800×1280 portrait.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            item { DeviceSection(state) }
            item { PlayerSection(state, onRestartPlayer, onReloadPlaylist) }
            item { SyncSection(state, onSyncNow) }
            item { DiagnosticsSection(onDiagnostics, onExport, onRefresh) }
            item {
                ControlsSection(
                    state = state,
                    canExitLockTask = canExitLockTask,
                    onBrightness = onBrightness,
                    onClearCache = onClearCache,
                    onRestartApp = onRestartApp,
                    onExitLockTask = onExitLockTask,
                )
            }
            item {
                Text(
                    "Lock Task / device-owner is documented, not bypassed. See KIOSK_SETUP.md.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        SnackbarHost(snackbar, modifier = Modifier.fillMaxWidth())
        OutlinedButton(
            onClick = onBackToPlayer,
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp)
                .heightIn(min = TouchMin),
        ) {
            Text("Back to player")
        }
    }
}

@Composable
private fun DeviceSection(state: AdminUiState) {
    SectionCard("Device") {
        Text(
            state.registration.deviceId,
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.primary,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Bold,
        )
        Meta("Status", state.registration.status)
        Meta("Server", state.registration.serverUrl)
        Meta(
            "API",
            if (state.registration.liveApi) "Live (Retrofit)" else "DEMO / fixture (offline)",
        )
        Meta("Timezone", state.registration.timezone)
        Meta("Last heartbeat", state.registration.lastHeartbeatAt ?: "—")
    }
}

@Composable
private fun PlayerSection(
    state: AdminUiState,
    onRestartPlayer: () -> Unit,
    onReloadPlaylist: () -> Unit,
) {
    SectionCard("Player") {
        Meta("Playlist pin", state.playlistId)
        Meta("Revision", state.playlistRevision.toString())
        Meta("Mode", if (state.demoPlaylist) "DEMO assets" else "Cached remote")
        FatButton("Restart player", onRestartPlayer)
        FatButton("Reload playlist", onReloadPlaylist, outlined = true)
    }
}

@Composable
private fun SyncSection(state: AdminUiState, onSyncNow: () -> Unit) {
    SectionCard("Sync") {
        if (state.syncing) {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        }
        Meta("Pending downloads", state.pendingDownloads.toString())
        Meta("Failed downloads", state.failedDownloads.toString())
        Meta("Running downloads", state.runningDownloads.toString())
        Meta("Queued play events", state.reporting.playback.toString())
        Meta("Queued heartbeats", state.reporting.heartbeats.toString())
        state.lastSync?.let { sync ->
            Meta("Last pin-swap", sync.pinSwapped.toString())
            Meta("Events drained", sync.eventsDrained.toString())
            if (sync.error != null) Meta("Last error", sync.error)
        }
        if (state.downloadErrors.isNotEmpty()) {
            Text(
                "Recent download errors",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
            )
            state.downloadErrors.take(4).forEach { err ->
                Text(err, style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace)
            }
        }
        FatButton(if (state.syncing) "Syncing…" else "Sync now", onSyncNow, enabled = !state.syncing)
    }
}

@Composable
private fun DiagnosticsSection(
    onDiagnostics: () -> Unit,
    onExport: () -> Unit,
    onRefresh: () -> Unit,
) {
    SectionCard("Diagnostics") {
        FatButton("Open device diagnostics", onDiagnostics)
        FatButton("Export logs / diagnostics", onExport, outlined = true)
        FatButton("Refresh status", onRefresh, outlined = true)
    }
}

@Composable
private fun ControlsSection(
    state: AdminUiState,
    canExitLockTask: Boolean,
    onBrightness: (Float) -> Unit,
    onClearCache: () -> Unit,
    onRestartApp: () -> Unit,
    onExitLockTask: () -> Unit,
) {
    SectionCard("Controls") {
        Text(
            "Brightness  ${String.format(Locale.US, "%.0f%%", state.brightness * 100)}",
            style = MaterialTheme.typography.titleMedium,
        )
        Slider(
            value = state.brightness,
            onValueChange = onBrightness,
            valueRange = 0.1f..1f,
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp),
        )
        FatButton("Clear cache (keep active playlist)", onClearCache, enabled = !state.busy)
        FatButton("Restart app", onRestartApp, outlined = true)
        if (canExitLockTask) {
            FatButton("Exit Lock Task (device-owner)", onExitLockTask, outlined = true)
        } else {
            Text(
                "Exit Lock Task is hidden unless this package is a DPC lock-task package and currently pinned.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun SectionCard(title: String, content: @Composable () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(
            Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(title, style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.primary)
            content()
        }
    }
}

@Composable
private fun Meta(label: String, value: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(
            label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(0.38f),
        )
        Text(
            value,
            style = MaterialTheme.typography.bodyMedium,
            fontFamily = FontFamily.Monospace,
            modifier = Modifier.weight(0.62f),
        )
    }
}

@Composable
private fun FatButton(
    label: String,
    onClick: () -> Unit,
    outlined: Boolean = false,
    enabled: Boolean = true,
) {
    val modifier = Modifier
        .fillMaxWidth()
        .heightIn(min = TouchMin)
    if (outlined) {
        OutlinedButton(onClick = onClick, enabled = enabled, modifier = modifier) {
            Text(label)
        }
    } else {
        Button(onClick = onClick, enabled = enabled, modifier = modifier) {
            Text(label)
        }
    }
}
