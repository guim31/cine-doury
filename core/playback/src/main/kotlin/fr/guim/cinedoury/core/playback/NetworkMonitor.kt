package fr.guim.cinedoury.core.playback

import android.annotation.SuppressLint
import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import fr.guim.cinedoury.core.model.NetworkKind
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * The network of the phone, as the player sees it (M2, `SPEC.md` § 4.27): whether there is one, and
 * whether it is a mobile one (cellular, or metered), for the default quality. Every change is also a
 * moment to try again at once what failed (the fetch of the margin).
 */
interface NetworkMonitor {
    val state: StateFlow<NetworkState>

    /** Runs [action] at each change of network, until the returned handle is closed. */
    fun onChange(action: () -> Unit): AutoCloseable
}

data class NetworkState(
    val online: Boolean = true,
    val kind: NetworkKind = NetworkKind.Wifi,
)

/**
 * [NetworkMonitor] on the default network of [ConnectivityManager]; one per process. Only the phone
 * makes one, and its manifest declares `ACCESS_NETWORK_STATE` (the TV's does not need it).
 */
@SuppressLint("MissingPermission")
class AndroidNetworkMonitor(
    context: Context,
) : NetworkMonitor {
    private val connectivity = context.getSystemService(ConnectivityManager::class.java)
    private val current = MutableStateFlow(read(connectivity?.getNetworkCapabilities(connectivity.activeNetwork)))
    override val state: StateFlow<NetworkState> = current.asStateFlow()
    private val listeners = java.util.concurrent.CopyOnWriteArrayList<() -> Unit>()

    init {
        connectivity?.registerDefaultNetworkCallback(
            object : ConnectivityManager.NetworkCallback() {
                override fun onCapabilitiesChanged(
                    network: Network,
                    capabilities: NetworkCapabilities,
                ) = publish(read(capabilities))

                override fun onLost(network: Network) = publish(NetworkState(online = false, kind = current.value.kind))
            },
        )
    }

    private fun publish(state: NetworkState) {
        if (state == current.value) return
        current.value = state
        listeners.forEach { it() }
    }

    override fun onChange(action: () -> Unit): AutoCloseable {
        listeners += action
        return AutoCloseable { listeners -= action }
    }

    private companion object {
        fun read(capabilities: NetworkCapabilities?): NetworkState {
            if (capabilities == null) return NetworkState(online = false)
            val mobile =
                capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) ||
                    !capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED)
            return NetworkState(
                online = capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET),
                kind = if (mobile) NetworkKind.Mobile else NetworkKind.Wifi,
            )
        }
    }
}
