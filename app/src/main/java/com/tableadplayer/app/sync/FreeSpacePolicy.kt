package com.tableadplayer.app.sync

/**
 * Stops nonessential downloads when usable space drops below the reserve.
 * Essential items (required by the pending playlist being prepared) still
 * download unless the volume is nearly full.
 */
object FreeSpacePolicy {
    const val DEFAULT_RESERVE_BYTES: Long = 200L * 1024L * 1024L
    const val CRITICAL_BYTES: Long = 8L * 1024L * 1024L

    fun allowDownload(
        freeBytes: Long,
        essential: Boolean,
        reserveBytes: Long = DEFAULT_RESERVE_BYTES,
        criticalBytes: Long = CRITICAL_BYTES,
    ): Boolean {
        if (freeBytes <= criticalBytes) return false
        if (essential) return true
        return freeBytes > reserveBytes
    }
}

class FreeSpaceGuard(
    private val usableBytes: () -> Long,
) {
    fun usable(): Long = usableBytes().coerceAtLeast(0L)
}
