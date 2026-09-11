package com.tableadplayer.app.core.crash

import android.app.Application
import android.content.Context
import kotlin.system.exitProcess

/**
 * Prevents a crash loop from pinning a kiosk in a rapid restart cycle.
 * After [THRESHOLD] uncaught exceptions within [WINDOW_MS], the next launch
 * opens diagnostics (safe mode) instead of the player.
 *
 * Service/boot restart storms are handled by [Watchdog] using the same
 * [WatchdogPolicy] window and threshold.
 */
object CrashGuard {
    internal const val PREFS = "crash_guard"
    private const val KEY_LAST_CRASH_AT = "last_crash_at"
    private const val KEY_CRASH_COUNT = "crash_count"
    internal const val KEY_SAFE_MODE = "safe_mode"
    const val THRESHOLD = WatchdogPolicy.THRESHOLD
    const val WINDOW_MS = WatchdogPolicy.WINDOW_MS

    fun install(app: Application) {
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            recordCrash(app)
            if (previous != null) {
                previous.uncaughtException(thread, error)
            } else {
                exitProcess(10)
            }
        }
        decayIfWindowExpired(app)
    }

    fun inSafeMode(context: Context): Boolean {
        val prefs = prefs(context)
        decayIfWindowExpired(context)
        return prefs.getBoolean(KEY_SAFE_MODE, false) ||
            prefs.getInt(KEY_CRASH_COUNT, 0) >= THRESHOLD
    }

    fun clearSafeMode(context: Context) {
        prefs(context).edit()
            .putInt(KEY_CRASH_COUNT, 0)
            .putBoolean(KEY_SAFE_MODE, false)
            .apply()
    }

    fun enterSafeMode(context: Context) {
        prefs(context).edit()
            .putBoolean(KEY_SAFE_MODE, true)
            .commit()
    }

    private fun recordCrash(context: Context) {
        val prefs = prefs(context)
        val now = System.currentTimeMillis()
        val last = prefs.getLong(KEY_LAST_CRASH_AT, 0L)
        val count = if (now - last <= WINDOW_MS) prefs.getInt(KEY_CRASH_COUNT, 0) + 1 else 1
        prefs.edit()
            .putLong(KEY_LAST_CRASH_AT, now)
            .putInt(KEY_CRASH_COUNT, count)
            .putBoolean(KEY_SAFE_MODE, count >= THRESHOLD)
            .commit()
    }

    internal fun decayIfWindowExpired(context: Context) {
        val prefs = prefs(context)
        val last = prefs.getLong(KEY_LAST_CRASH_AT, 0L)
        if (last == 0L) return
        if (System.currentTimeMillis() - last > WINDOW_MS && !prefs.getBoolean(KEY_SAFE_MODE, false)) {
            prefs.edit().putInt(KEY_CRASH_COUNT, 0).apply()
        }
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
