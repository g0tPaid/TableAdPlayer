package com.tableadplayer.app.sync

/**
 * The active playlist pin swaps only when every required item is READY on disk.
 * Partial revisions stay pending so playback never blanks onto missing media.
 */
object PinSwapPolicy {
    fun canActivate(items: List<ItemReadiness>): Boolean {
        if (items.isEmpty()) return false
        return items.all { it.ready }
    }
}

data class ItemReadiness(
    val mediaId: String,
    val ready: Boolean,
)
