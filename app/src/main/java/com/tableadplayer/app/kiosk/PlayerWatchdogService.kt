package com.tableadplayer.app.kiosk

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.UserManager
import androidx.core.app.NotificationCompat
import com.tableadplayer.app.R
import com.tableadplayer.app.core.crash.CrashGuard
import com.tableadplayer.app.core.crash.Watchdog
import com.tableadplayer.app.sync.SyncScheduler
import com.tableadplayer.app.ui.diagnostics.DiagnosticsActivity
import com.tableadplayer.app.ui.player.PlayerActivity

/**
 * OEM boot keep-alive. Started from [BootCompletedReceiver] so playback can
 * resume from the **cached** playlist immediately while sync runs in the
 * background. This is not a lock-screen bypass: if the user is locked, the
 * activity start may still be blocked until unlock (see KIOSK_SETUP.md).
 *
 * Rapid START_STICKY restart loops are stopped by [Watchdog] / [CrashGuard].
 */
class PlayerWatchdogService : Service() {

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        ensureChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startInForeground()
        (application as? com.tableadplayer.app.TableAdPlayerApp)?.initRuntime()
        if (!isUserUnlocked()) {
            return START_STICKY
        }
        if (CrashGuard.inSafeMode(this) || !Watchdog.allowPlayerStart(this)) {
            openDiagnostics()
            return START_STICKY
        }
        val fromBoot = intent?.getBooleanExtra(EXTRA_FROM_BOOT, false) == true
        if (fromBoot) {
            val allowed = Watchdog.noteWatchdogStart(this)
            if (!allowed) {
                openDiagnostics()
                return START_STICKY
            }
        }
        openPlayer()
        SyncScheduler.enqueueNow(this)
        return START_STICKY
    }

    private fun isUserUnlocked(): Boolean {
        val um = getSystemService(UserManager::class.java) ?: return true
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            um.isUserUnlocked
        } else {
            true
        }
    }

    private fun openPlayer() {
        val launch = Intent(this, PlayerActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        }
        runCatching { startActivity(launch) }
    }

    private fun openDiagnostics() {
        val launch = Intent(this, DiagnosticsActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        }
        runCatching { startActivity(launch) }
    }

    private fun startInForeground() {
        val notification = buildNotification()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK,
            )
        } else {
            @Suppress("DEPRECATION")
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun buildNotification(): Notification {
        val open = Intent(this, PlayerActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        }
        val pending = PendingIntent.getActivity(
            this,
            0,
            open,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setContentTitle(getString(R.string.app_name))
            .setContentText(getString(R.string.watchdog_notification))
            .setContentIntent(pending)
            .setOngoing(true)
            .setSilent(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .build()
    }

    private fun ensureChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = getSystemService(NotificationManager::class.java) ?: return
        val channel = NotificationChannel(
            CHANNEL_ID,
            getString(R.string.watchdog_channel),
            NotificationManager.IMPORTANCE_LOW,
        )
        channel.setShowBadge(false)
        manager.createNotificationChannel(channel)
    }

    companion object {
        const val CHANNEL_ID = "tablead-player-watchdog"
        const val NOTIFICATION_ID = 47
        const val EXTRA_FROM_BOOT = "from_boot"

        fun start(context: Context, fromBoot: Boolean = false) {
            val intent = Intent(context, PlayerWatchdogService::class.java).apply {
                putExtra(EXTRA_FROM_BOOT, fromBoot)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                runCatching { context.startForegroundService(intent) }
                    .onFailure { runCatching { context.startService(intent) } }
            } else {
                runCatching { context.startService(intent) }
            }
        }
    }
}
