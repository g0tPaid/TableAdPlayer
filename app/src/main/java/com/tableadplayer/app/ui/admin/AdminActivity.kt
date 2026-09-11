package com.tableadplayer.app.ui.admin

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tableadplayer.app.TableAdPlayerApp
import com.tableadplayer.app.kiosk.LockTaskGate
import com.tableadplayer.app.ui.diagnostics.DiagnosticsActivity
import com.tableadplayer.app.ui.player.PlayerActivity
import com.tableadplayer.app.ui.theme.TableAdTheme
import java.io.File

class AdminActivity : ComponentActivity() {

    private val viewModel: AdminViewModel by viewModels()
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
        (application as? TableAdPlayerApp)?.initRuntime()
        setContent {
            TableAdTheme {
                val state = viewModel.state.collectAsStateWithLifecycle().value
                applyBrightness(state.brightness)
                AdminScreen(
                    state = state,
                    canExitLockTask = LockTaskGate.canRequestStopLockTask(this),
                    onRefresh = viewModel::refresh,
                    onSyncNow = viewModel::syncNow,
                    onClearCache = viewModel::clearCache,
                    onBrightness = viewModel::setBrightness,
                    onDiagnostics = {
                        startActivity(Intent(this, DiagnosticsActivity::class.java))
                    },
                    onExport = {
                        viewModel.exportServiceBundle { file ->
                            pendingExport = file
                            createDocument.launch(file.name)
                        }
                    },
                    onReloadPlaylist = {
                        startActivity(
                            Intent(this, PlayerActivity::class.java)
                                .putExtra(PlayerActivity.EXTRA_FORCE_RELOAD, true),
                        )
                        finish()
                    },
                    onRestartPlayer = {
                        startActivity(
                            Intent(this, PlayerActivity::class.java)
                                .putExtra(PlayerActivity.EXTRA_FORCE_RELOAD, true)
                                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP),
                        )
                        finish()
                    },
                    onRestartApp = { restartApp() },
                    onExitLockTask = { LockTaskGate.stopLockTaskIfPermitted(this) },
                    onBackToPlayer = {
                        startActivity(Intent(this, PlayerActivity::class.java))
                        finish()
                    },
                    onMessageShown = viewModel::consumeMessage,
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        viewModel.refresh()
    }

    private fun applyBrightness(value: Float) {
        val params = window.attributes
        params.screenBrightness = value.coerceIn(0.1f, 1f)
        window.attributes = params
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }

    private fun restartApp() {
        val intent = Intent(this, PlayerActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
            putExtra(PlayerActivity.EXTRA_FORCE_RELOAD, true)
        }
        startActivity(intent)
        Runtime.getRuntime().exit(0)
    }
}
