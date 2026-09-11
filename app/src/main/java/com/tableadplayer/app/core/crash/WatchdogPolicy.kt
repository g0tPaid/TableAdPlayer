package com.tableadplayer.app.core.crash

/**
 * Pure policy for watchdog / crash-loop protection. Shared by [CrashGuard]
 * (uncaught exceptions) and [Watchdog] (process / service restarts).
 */
object WatchdogPolicy {
    const val THRESHOLD = 3
    const val WINDOW_MS = 120_000L
    const val DEBOUNCE_MS = 5_000L

    /**
     * Next restart/crash count given the previous count and timestamps.
     * Redeliveries within [DEBOUNCE_MS] are ignored so a sticky service
     * `onStartCommand` does not look like a crash loop.
     */
    fun nextCount(previousCount: Int, lastAtMs: Long, nowMs: Long, debounceMs: Long = DEBOUNCE_MS, windowMs: Long = WINDOW_MS): Int {
        if (lastAtMs > 0L && nowMs - lastAtMs <= debounceMs) return previousCount.coerceAtLeast(0)
        if (lastAtMs <= 0L || nowMs - lastAtMs > windowMs) return 1
        return previousCount.coerceAtLeast(0) + 1
    }

    fun enterSafeMode(count: Int, threshold: Int = THRESHOLD): Boolean = count >= threshold

    fun allowPlayerStart(safeMode: Boolean, count: Int, threshold: Int = THRESHOLD): Boolean {
        return !safeMode && count < threshold
    }
}
