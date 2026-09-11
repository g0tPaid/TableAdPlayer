package com.tableadplayer.app.debug

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
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.tableadplayer.app.BuildConfig
import com.tableadplayer.app.ui.admin.AdminActivity
import com.tableadplayer.app.ui.diagnostics.DiagnosticsActivity
import com.tableadplayer.app.ui.player.PlayerActivity
import com.tableadplayer.app.ui.theme.TableAdTheme

/** Debug-only launcher / `adb shell am start -a com.tableadplayer.app.DEBUG` */
class DebugEntryActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            TableAdTheme {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text("Debug entry", style = MaterialTheme.typography.headlineMedium)
                    Text(
                        "applicationId=${BuildConfig.APPLICATION_ID}\n" +
                            "version=${BuildConfig.VERSION_NAME}\n" +
                            "API_BASE_URL=${BuildConfig.API_BASE_URL}",
                        style = MaterialTheme.typography.bodySmall,
                    )
                    Button(
                        onClick = { startActivity(Intent(this@DebugEntryActivity, DiagnosticsActivity::class.java)) },
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text("Diagnostics") }
                    Button(
                        onClick = { startActivity(Intent(this@DebugEntryActivity, PlayerActivity::class.java)) },
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text("Demo player") }
                    Button(
                        onClick = { startActivity(Intent(this@DebugEntryActivity, AdminActivity::class.java)) },
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text("Admin shell") }
                }
            }
        }
    }
}
