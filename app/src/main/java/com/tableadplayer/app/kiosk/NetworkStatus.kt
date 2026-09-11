package com.tableadplayer.app.kiosk

/**
 * Interprets [android.net.NetworkCapabilities] flags without Android types so
 * unit tests can cover "drain when online".
 */
object NetworkStatus {
    fun isOnline(hasInternet: Boolean, validated: Boolean? = null): Boolean {
        if (!hasInternet) return false
        return validated != false
    }
}
