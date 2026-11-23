package ua.robochyichas.app.data.cloud

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import ua.robochyichas.app.data.local.SyncPreferences

class ConnectivitySyncMonitor(
    private val context: Context,
    private val syncPreferences: SyncPreferences,
    private val onNetworkBecameAvailable: suspend () -> Unit
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
    private var registered = false

    private val callback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) {
            updateAndMaybeSync()
        }

        override fun onCapabilitiesChanged(network: Network, networkCapabilities: NetworkCapabilities) {
            val online = networkCapabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
                networkCapabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
            syncPreferences.setOnline(online)
            if (online && syncPreferences.hasPendingSync() && syncPreferences.hasAnyCloudTarget()) {
                scope.launch { onNetworkBecameAvailable() }
            }
        }

        override fun onLost(network: Network) {
            updateOnlineState()
        }
    }

    fun start() {
        updateOnlineState()
        val mgr = cm ?: return
        if (registered) return
        val request = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .build()
        runCatching {
            mgr.registerNetworkCallback(request, callback)
            registered = true
        }
    }

    fun stop() {
        val mgr = cm ?: return
        if (!registered) return
        runCatching {
            mgr.unregisterNetworkCallback(callback)
            registered = false
        }
    }

    private fun updateOnlineState(): Boolean {
        val online = AndroidConnectivityChecker(context).isNetworkAvailable()
        syncPreferences.setOnline(online)
        return online
    }

    private fun updateAndMaybeSync() {
        val online = updateOnlineState()
        if (online && syncPreferences.hasPendingSync() && syncPreferences.hasAnyCloudTarget()) {
            scope.launch { onNetworkBecameAvailable() }
        }
    }
}
