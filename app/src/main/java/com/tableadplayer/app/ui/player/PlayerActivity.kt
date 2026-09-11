package com.tableadplayer.app.ui.player

import android.content.Intent
import android.content.pm.ActivityInfo
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tableadplayer.app.core.crash.CrashGuard
import com.tableadplayer.app.core.immersive.ImmersiveKiosk
import com.tableadplayer.app.ui.admin.AdminActivity
import com.tableadplayer.app.ui.diagnostics.DiagnosticsActivity
import com.tableadplayer.app.ui.theme.TableAdTheme

class PlayerActivity : ComponentActivity() {

    private val viewModel: PlayerViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        if (CrashGuard.inSafeMode(this)) {
            startActivity(Intent(this, DiagnosticsActivity::class.java))
            finish()
            return
        }
        ImmersiveKiosk.apply(this)
        setContent {
            TableAdTheme {
                val content = viewModel.content.collectAsStateWithLifecycle().value
                val player = viewModel.exoPlayer.collectAsStateWithLifecycle().value
                val demo = viewModel.demo.collectAsStateWithLifecycle().value
                PlayerScreen(
                    content = content,
                    exoPlayer = player,
                    showDemoBadge = demo,
                    onOpenAdmin = {
                        startActivity(Intent(this, AdminActivity::class.java))
                    },
                )
            }
        }
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) ImmersiveKiosk.apply(this)
    }

    override fun onResume() {
        super.onResume()
        ImmersiveKiosk.apply(this)
        viewModel.reload()
    }
}
