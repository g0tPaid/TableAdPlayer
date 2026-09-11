package com.tableadplayer.app.kiosk

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.tableadplayer.app.core.crash.CrashGuard
import com.tableadplayer.app.sync.SyncScheduler
import com.tableadplayer.app.ui.player.PlayerActivity

/**
 * Starts the player after boot from the **cached** (or DEMO) playlist.
 * Sync is enqueued in the background and must not delay first paint.
 *
 * Background activity starts are restricted on many OEM images after
 * Android 10; [PlayerWatchdogService] is the foreground-service path used
 * for boot reliability. Device-owner / Lock Task remains the supported
 * 24/7 kiosk path — see KIOSK_SETUP.md. This receiver does not bypass
 * Keyguard or OEM background-activity limits.
 */
class BootCompletedReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        val action = intent?.action ?: return
        if (action != Intent.ACTION_BOOT_COMPLETED &&
            action != Intent.ACTION_LOCKED_BOOT_COMPLETED &&
            action != Intent.ACTION_USER_UNLOCKED
        ) {
            return
        }
        val appContext = context.applicationContext
        (appContext as? com.tableadplayer.app.TableAdPlayerApp)?.initRuntime()
        SyncScheduler.enqueueNow(appContext)
        PlayerWatchdogService.start(appContext, fromBoot = true)
        if (CrashGuard.inSafeMode(appContext)) return
        if (action == Intent.ACTION_LOCKED_BOOT_COMPLETED) return
        val launch = Intent(appContext, PlayerActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        }
        runCatching { appContext.startActivity(launch) }
    }
}
