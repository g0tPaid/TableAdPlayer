package com.tableadplayer.app.ui.admin

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.tableadplayer.app.BuildConfig
import com.tableadplayer.app.core.device.DeviceIdentity
import com.tableadplayer.app.ui.diagnostics.DiagnosticsActivity
import com.tableadplayer.app.ui.player.PlayerActivity
import com.tableadplayer.app.ui.theme.TableAdTheme

class AdminActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val identity = DeviceIdentity(applicationContext)
        setContent {
            TableAdTheme {
                var deviceId by remember { mutableStateOf("…") }
                LaunchedEffect(Unit) {
                    deviceId = identity.getOrCreate()
                }
                AdminScreen(
                    deviceId = deviceId,
                    apiBaseUrl = BuildConfig.API_BASE_URL,
                    onDiagnostics = {
                        startActivity(Intent(this, DiagnosticsActivity::class.java))
                    },
                    onPlayer = {
                        startActivity(Intent(this, PlayerActivity::class.java))
                        finish()
                    },
                )
            }
        }
    }
}

@Composable
fun AdminScreen(
    deviceId: String,
    apiBaseUrl: String,
    onDiagnostics: () -> Unit,
    onPlayer: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text("Admin shell", style = MaterialTheme.typography.headlineMedium)
        Text(
            "Long-press the top-left corner on the player to open this screen.",
            style = MaterialTheme.typography.bodyMedium,
        )
        Text(deviceId, style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.primary)
        Text("API_BASE_URL\n$apiBaseUrl", style = MaterialTheme.typography.bodySmall)
        Text(
            "Lock Task / device-owner is documented, not bypassed. See KIOSK_SETUP.md.",
            style = MaterialTheme.typography.bodySmall,
        )
        Button(onClick = onDiagnostics, modifier = Modifier.fillMaxWidth()) {
            Text("Device diagnostics")
        }
        OutlinedButton(onClick = onPlayer, modifier = Modifier.fillMaxWidth()) {
            Text("Back to player")
        }
        Text(
            "Later: sync now, playlist pin, brightness, exit kiosk (device-owner only).",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
