package com.tableadplayer.app.kiosk

import android.app.Activity
import android.app.ActivityManager
import android.app.admin.DevicePolicyManager

/**
 * Lock Task exit is a Device Policy Controller decision. This app never
 * bypasses Keyguard or calls hidden APIs to unpin itself.
 */
object LockTaskGate {
    fun inLockTask(activity: Activity): Boolean {
        val am = activity.getSystemService(Activity.ACTIVITY_SERVICE) as? ActivityManager
            ?: return false
        return am.lockTaskModeState != ActivityManager.LOCK_TASK_MODE_NONE
    }

    fun isLockTaskPermitted(activity: Activity): Boolean {
        val dpm = activity.getSystemService(DevicePolicyManager::class.java) ?: return false
        return runCatching { dpm.isLockTaskPermitted(activity.packageName) }.getOrDefault(false)
    }

    /**
     * Show "Exit Lock Task" only when we are actually pinned **and** the DPC
     * listed this package. Screen-pinning without device-owner stays hidden
     * (the system gesture unpins; we do not advertise a bypass).
     */
    fun canRequestStopLockTask(inLockTask: Boolean, permittedByDpc: Boolean): Boolean {
        return inLockTask && permittedByDpc
    }

    fun canRequestStopLockTask(activity: Activity): Boolean {
        return canRequestStopLockTask(inLockTask(activity), isLockTaskPermitted(activity))
    }

    fun stopLockTaskIfPermitted(activity: Activity): Boolean {
        if (!canRequestStopLockTask(activity)) return false
        return runCatching {
            activity.stopLockTask()
            true
        }.getOrDefault(false)
    }
}
