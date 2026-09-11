package com.tableadplayer.app.ui.diagnostics

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.tableadplayer.app.core.diagnostics.DiagnosticsSnapshot
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DiagnosticsScreen(
    state: DiagnosticsUiState,
    onRefresh: () -> Unit,
    onExport: () -> Unit,
    onRetryPlayer: () -> Unit,
    onMessageShown: () -> Unit,
) {
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(state.message) {
        val msg = state.message ?: return@LaunchedEffect
        snackbar.showSnackbar(msg)
        onMessageShown()
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = { Text("Device Diagnostics") },
                actions = {
                    IconButton(onClick = onRefresh) {
                        Icon(Icons.Outlined.Refresh, contentDescription = "Refresh")
                    }
                    IconButton(onClick = onExport, enabled = state.snapshot != null) {
                        Icon(Icons.Outlined.Share, contentDescription = "Export JSON")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
                ),
            )
        },
    ) { padding ->
        when {
            state.loading && state.snapshot == null -> {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    CircularProgressIndicator()
                }
            }
            state.error != null && state.snapshot == null -> {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding)
                        .padding(24.dp),
                    verticalArrangement = Arrangement.Center,
                ) {
                    Text(state.error, color = MaterialTheme.colorScheme.error)
                    Button(onClick = onRefresh, modifier = Modifier.padding(top = 16.dp)) {
                        Text("Retry")
                    }
                }
            }
            else -> {
                val snap = state.snapshot ?: return@Scaffold
                DiagnosticsBody(
                    snapshot = snap,
                    onExport = onExport,
                    onRetryPlayer = onRetryPlayer,
                    modifier = Modifier.padding(padding),
                )
            }
        }
    }
}

@Composable
private fun DiagnosticsBody(
    snapshot: DiagnosticsSnapshot,
    onExport: () -> Unit,
    onRetryPlayer: () -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Text(
                snapshot.deviceId,
                style = MaterialTheme.typography.headlineSmall,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
            )
            Text(
                "Captured ${snapshot.capturedAt}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (snapshot.safeMode) {
            item {
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Safe mode: playback paused after repeated crashes.")
                        Button(onClick = onRetryPlayer) { Text("Retry player") }
                    }
                }
            }
        }
        item {
            Button(onClick = onExport, modifier = Modifier.fillMaxWidth()) {
                Text("Export Diagnostics JSON")
            }
        }
        item {
            Section("Application") {
                RowItem("App version", snapshot.app.versionName)
                RowItem("Version code", snapshot.app.versionCode.toString())
                RowItem("Application ID", snapshot.app.applicationId)
                RowItem("Build type", snapshot.app.buildType)
                RowItem("Demo mode", snapshot.app.demoMode.toString())
                RowItem("API base URL", snapshot.app.apiBaseUrl)
            }
        }
        item {
            Section("Android") {
                RowItem("Android version", snapshot.os.androidVersion)
                RowItem("API level", snapshot.os.apiLevel.toString())
                RowItem("Security patch", snapshot.os.securityPatch ?: "—")
                RowItem("Android ID", snapshot.androidId ?: "—")
            }
        }
        item {
            Section("Device") {
                RowItem("Manufacturer", snapshot.hardware.manufacturer)
                RowItem("Brand", snapshot.hardware.brand)
                RowItem("Model", snapshot.hardware.model)
                RowItem("Device name", snapshot.hardware.deviceName ?: "—")
                RowItem("Device", snapshot.hardware.device)
                RowItem("Product", snapshot.hardware.product)
                RowItem("CPU ABI(s)", snapshot.hardware.cpuAbis.joinToString())
                RowItem("Board / HW", "${snapshot.hardware.board} / ${snapshot.hardware.hardware}")
            }
        }
        item {
            Section("Display") {
                RowItem("Resolution", "${snapshot.display.widthPx}×${snapshot.display.heightPx}")
                RowItem("Density", String.format(Locale.US, "%.2f (%d dpi)", snapshot.display.density, snapshot.display.densityDpi))
                RowItem("Scaled density", String.format(Locale.US, "%.2f", snapshot.display.scaledDensity))
                RowItem("Refresh rate", snapshot.display.refreshRateHz?.let { String.format(Locale.US, "%.1f Hz", it) } ?: "—")
            }
        }
        item {
            Section("Memory") {
                RowItem("RAM total", formatBytes(snapshot.memory.totalRamBytes))
                RowItem("RAM available", formatBytes(snapshot.memory.availableRamBytes))
                RowItem("Low memory", snapshot.memory.lowMemory.toString())
                RowItem("Runtime used / max", "${formatBytes(snapshot.memory.runtimeUsedBytes)} / ${formatBytes(snapshot.memory.runtimeMaxBytes)}")
            }
        }
        item {
            Section("Storage") {
                RowItem("Data total", formatBytes(snapshot.storage.dataTotalBytes))
                RowItem("Data free", formatBytes(snapshot.storage.dataFreeBytes))
                RowItem("App files usable", snapshot.storage.appFilesDirBytes?.let { formatBytes(it) } ?: "—")
            }
        }
        item {
            Section("Battery") {
                RowItem("Percent", snapshot.battery.percent?.let { "$it%" } ?: "—")
                RowItem("Charging", snapshot.battery.charging.toString())
                RowItem("Plugged", snapshot.battery.plugged ?: "—")
                RowItem("Status", snapshot.battery.status ?: "—")
                RowItem("Health", snapshot.battery.health ?: "—")
                RowItem("Temperature", snapshot.battery.temperatureC?.let { String.format(Locale.US, "%.1f °C", it) } ?: "—")
            }
        }
        item {
            Section("Network") {
                RowItem("Connected", snapshot.network.connected.toString())
                RowItem("Wi‑Fi", snapshot.network.wifi.toString())
                RowItem("Ethernet", snapshot.network.ethernet.toString())
                RowItem("Cellular", snapshot.network.cellular.toString())
                RowItem("Wi‑Fi SSID", snapshot.network.wifiSsid ?: "unavailable (no location perm)")
                RowItem("Downlink", snapshot.network.linkDownstreamKbps?.let { "$it kbps" } ?: "—")
                snapshot.network.interfaces.forEach { ni ->
                    RowItem(ni.name, buildString {
                        append(if (ni.up) "up" else "down")
                        if (ni.addresses.isNotEmpty()) {
                            append(" · ")
                            append(ni.addresses.joinToString())
                        }
                    })
                }
            }
        }
    }
}

@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
            content()
        }
    }
}

@Composable
private fun RowItem(label: String, value: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(
            label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(0.42f),
        )
        Text(
            value,
            style = MaterialTheme.typography.bodyMedium,
            fontFamily = FontFamily.Monospace,
            modifier = Modifier.weight(0.58f),
        )
    }
}

private fun formatBytes(bytes: Long): String {
    if (bytes < 0) return "—"
    val kb = 1024.0
    val mb = kb * 1024
    val gb = mb * 1024
    return when {
        bytes >= gb -> String.format(Locale.US, "%.2f GB", bytes / gb)
        bytes >= mb -> String.format(Locale.US, "%.2f MB", bytes / mb)
        bytes >= kb -> String.format(Locale.US, "%.1f KB", bytes / kb)
        else -> "$bytes B"
    }
}
