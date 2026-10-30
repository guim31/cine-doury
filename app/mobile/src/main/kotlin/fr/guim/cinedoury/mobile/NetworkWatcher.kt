package fr.guim.cinedoury.mobile

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import fr.guim.cinedoury.core.data.SessionManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Follows the network of the phone (M1, `SPEC.md` § 4.26): when it changes (the Wi-Fi of the house
 * left for the mobile network, or found again), the open profile moves to the address that answers
 * now ([SessionManager.followNetwork]), in the middle of the navigation, the screens already open
 * included. A short pause first: a network that comes up is not always ready at once.
 */
class NetworkWatcher(
    context: Context,
    private val sessions: SessionManager,
    private val scope: CoroutineScope,
) {
    private val connectivity = context.getSystemService(ConnectivityManager::class.java)
    private var pending: Job? = null
    private var last: Network? = null

    private val callback =
        object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) = changed(network)

            override fun onLost(network: Network) = changed(null)
        }

    fun start() {
        connectivity?.registerDefaultNetworkCallback(callback)
    }

    fun stop() {
        runCatching { connectivity?.unregisterNetworkCallback(callback) }
        pending?.cancel()
    }

    private fun changed(network: Network?) {
        if (network != null && network == last) return
        last = network
        pending?.cancel()
        pending =
            scope.launch {
                delay(SETTLE_MILLIS)
                sessions.followNetwork()
            }
    }

    private companion object {
        const val SETTLE_MILLIS = 1_500L
    }
}
