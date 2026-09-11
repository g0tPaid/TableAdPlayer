package com.tableadplayer.app.kiosk

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.tableadplayer.app.ui.player.PlayerActivity

/**
 * Starts the player after boot. Background activity starts are restricted on many
 * OEM images after Android 10; device-owner / Lock Task is the supported kiosk path.
 * See KIOSK_SETUP.md — this receiver does not attempt to bypass those restrictions.
 */
class BootCompletedReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        val action = intent?.action ?: return
        if (action != Intent.ACTION_BOOT_COMPLETED &&
            action != Intent.ACTION_LOCKED_BOOT_COMPLETED
        ) {
            return
        }
        val launch = Intent(context, PlayerActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        }
        runCatching { context.startActivity(launch) }
        runCatching { com.tableadplayer.app.sync.SyncScheduler.enqueue(context) }
    }
}
