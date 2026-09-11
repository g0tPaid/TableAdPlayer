package com.tableadplayer.app.kiosk

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.os.Build
import com.tableadplayer.app.sync.SyncScheduler

/**
 * When the device comes online, enqueue a reporting drain. Failures stay in
 * Room; the playlist loop is not involved.
 */
class ConnectivityMonitor(
    context: Context,
    private val onOnline: () -> Unit,
) {
    private val appContext = context.applicationContext
    private val manager = appContext.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
    private val callback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) {
            maybeOnline()
        }

        override fun onCapabilitiesChanged(network: Network, networkCapabilities: NetworkCapabilities) {
            val internet = networkCapabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            val validated = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                networkCapabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
            } else {
                true
            }
            if (NetworkStatus.isOnline(internet, validated)) onOnline()
        }
    }

    fun start() {
        val cm = manager ?: return
        val request = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .build()
        runCatching { cm.registerNetworkCallback(request, callback) }
        maybeOnline()
    }

    private fun maybeOnline() {
        val cm = manager ?: return
        val network = cm.activeNetwork ?: return
        val caps = cm.getNetworkCapabilities(network) ?: return
        val internet = caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
        val validated = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
        } else {
            true
        }
        if (NetworkStatus.isOnline(internet, validated)) onOnline()
    }

    companion object {
        fun enqueueReportingDrain(context: Context) {
            SyncScheduler.enqueueReporting(context)
        }
    }
}
