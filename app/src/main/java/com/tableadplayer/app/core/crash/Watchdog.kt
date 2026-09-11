package com.tableadplayer.app.core.crash

import android.content.Context

/**
 * Counts watchdog-initiated player starts. After [WatchdogPolicy.THRESHOLD]
 * restarts inside [WatchdogPolicy.WINDOW_MS], playback is suppressed and the
 * next launch opens diagnostics (safe mode) instead of spinning ExoPlayer.
 *
 * Uncaught exceptions are still recorded by [CrashGuard]. This type covers
 * START_STICKY service / OEM process kills that would otherwise bounce the
 * player without an exception handler firing.
 */
object Watchdog {
    private const val PREFS = "crash_guard"
    private const val KEY_LAST_WATCHDOG_AT = "watchdog_last_at"
    private const val KEY_WATCHDOG_COUNT = "watchdog_count"
    private const val KEY_HEALTHY_AT = "watchdog_healthy_at"

    /**
     * Record a watchdog/boot start. Returns false when the player must not
     * be launched (safe mode or rapid-restart ceiling).
     */
    fun noteWatchdogStart(context: Context, nowMs: Long = System.currentTimeMillis()): Boolean {
        val prefs = prefs(context)
        CrashGuard.decayIfWindowExpired(context)
        if (CrashGuard.inSafeMode(context)) return false
        val last = prefs.getLong(KEY_LAST_WATCHDOG_AT, 0L)
        val previous = prefs.getInt(KEY_WATCHDOG_COUNT, 0)
        val count = WatchdogPolicy.nextCount(previous, last, nowMs)
        val trip = WatchdogPolicy.enterSafeMode(count)
        if (trip) {
            CrashGuard.enterSafeMode(context)
        }
        prefs.edit()
            .putLong(KEY_LAST_WATCHDOG_AT, nowMs)
            .putInt(KEY_WATCHDOG_COUNT, count)
            .commit()
        return WatchdogPolicy.allowPlayerStart(CrashGuard.inSafeMode(context), count)
    }

    fun allowPlayerStart(context: Context): Boolean {
        if (CrashGuard.inSafeMode(context)) return false
        val prefs = prefs(context)
        return WatchdogPolicy.allowPlayerStart(
            safeMode = false,
            count = prefs.getInt(KEY_WATCHDOG_COUNT, 0),
        )
    }

    /** Successful item start — the player is healthy; decay the restart counter. */
    fun noteHealthyPlayback(context: Context, nowMs: Long = System.currentTimeMillis()) {
        prefs(context).edit()
            .putLong(KEY_HEALTHY_AT, nowMs)
            .putInt(KEY_WATCHDOG_COUNT, 0)
            .apply()
    }

    fun clear(context: Context) {
        prefs(context).edit()
            .putInt(KEY_WATCHDOG_COUNT, 0)
            .putBoolean(CrashGuard.KEY_SAFE_MODE, false)
            .apply()
        CrashGuard.clearSafeMode(context)
    }

    private fun prefs(context: Context) =
        storageContext(context).getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private fun storageContext(context: Context): Context {
        return if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N) {
            context.applicationContext.createDeviceProtectedStorageContext()
        } else {
            context.applicationContext
        }
    }
}
