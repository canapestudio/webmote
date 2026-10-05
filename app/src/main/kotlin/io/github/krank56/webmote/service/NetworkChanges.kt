package io.github.krank56.webmote.service

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.util.Log
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

/**
 * Emits each time the phone's default network changes after collection starts: another network
 * (e.g. another Wi-Fi), no network, or different transports. The network the phone is on when
 * collection starts doesn't count. It never reads the Wi-Fi name, which would need location access.
 */
internal fun defaultNetworkChanges(context: Context): Flow<Unit> = callbackFlow {
    val connectivity = context.getSystemService(ConnectivityManager::class.java)
    // The callback runs on one ConnectivityManager thread, which is the only one touching these after registration.
    var network: Network? = connectivity?.activeNetwork
    var transports: Set<Int>? = network?.let { connectivity?.getNetworkCapabilities(it) }?.let(::transportsOf)

    val callback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(available: Network) {
            if (available == network) return
            network = available
            transports = null
            trySend(Unit)
        }

        override fun onLost(lost: Network) {
            if (lost != network) return
            network = null
            transports = null
            trySend(Unit)
        }

        override fun onCapabilitiesChanged(changed: Network, capabilities: NetworkCapabilities) {
            val current = transportsOf(capabilities)
            val isChange = changed != network || (transports != null && transports != current)
            network = changed
            transports = current
            if (isChange) trySend(Unit)
        }
    }

    val registered = try {
        connectivity?.registerDefaultNetworkCallback(callback)
        connectivity != null
    } catch (e: RuntimeException) {
        Log.w(TAG, "Couldn't watch network changes", e)
        false
    }
    awaitClose { if (registered) connectivity?.unregisterNetworkCallback(callback) }
}

private val WATCHED_TRANSPORTS = listOf(
    NetworkCapabilities.TRANSPORT_WIFI,
    NetworkCapabilities.TRANSPORT_ETHERNET,
    NetworkCapabilities.TRANSPORT_CELLULAR,
    NetworkCapabilities.TRANSPORT_VPN,
)

private fun transportsOf(capabilities: NetworkCapabilities): Set<Int> =
    WATCHED_TRANSPORTS.filterTo(mutableSetOf()) { capabilities.hasTransport(it) }

private const val TAG = "Webmote"
