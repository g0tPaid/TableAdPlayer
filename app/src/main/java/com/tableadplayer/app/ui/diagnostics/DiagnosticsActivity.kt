package com.tableadplayer.app.ui.diagnostics

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tableadplayer.app.ui.player.PlayerActivity
import com.tableadplayer.app.ui.theme.TableAdTheme
import java.io.File

class DiagnosticsActivity : ComponentActivity() {

    private val viewModel: DiagnosticsViewModel by viewModels()
    private var pendingExport: File? = null

    private val createDocument = registerForActivityResult(
        ActivityResultContracts.CreateDocument("application/json"),
    ) { uri: Uri? ->
        val file = pendingExport
        pendingExport = null
        if (uri == null || file == null) return@registerForActivityResult
        contentResolver.openOutputStream(uri)?.use { out ->
            file.inputStream().use { it.copyTo(out) }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            TableAdTheme {
                val state = viewModel.state.collectAsStateWithLifecycle().value
                DiagnosticsScreen(
                    state = state,
                    onRefresh = viewModel::refresh,
                    onExport = { export() },
                    onRetryPlayer = {
                        viewModel.retryPlayer()
                        startActivity(Intent(this, PlayerActivity::class.java))
                    },
                    onMessageShown = viewModel::consumeMessage,
                )
            }
        }
    }

    private fun export() {
        viewModel.exportToAppStorage { file ->
            pendingExport = file
            createDocument.launch(file.name)
        }
    }
}
