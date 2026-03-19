package ai.agent.android.data.repositories

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import ai.agent.android.domain.models.NetworkState
import ai.agent.android.domain.repositories.NetworkStateRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Implementation of [NetworkStateRepository] that listens to system network changes.
 */
@Singleton
class NetworkStateRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context
) : NetworkStateRepository {

    private val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

    private val _networkState = MutableStateFlow(getCurrentState())
    override val networkState: StateFlow<NetworkState> = _networkState.asStateFlow()

    private val networkCallback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) {
            _networkState.value = getCurrentState()
        }

        override fun onLost(network: Network) {
            _networkState.value = getCurrentState()
        }

        override fun onCapabilitiesChanged(
            network: Network,
            networkCapabilities: NetworkCapabilities
        ) {
            _networkState.value = getCurrentState(networkCapabilities)
        }
    }

    init {
        val request = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .build()
        connectivityManager.registerNetworkCallback(request, networkCallback)
    }

    private fun getCurrentState(capabilities: NetworkCapabilities? = null): NetworkState {
        val activeNetwork = connectivityManager.activeNetwork ?: return NetworkState(false, false)
        val caps = capabilities ?: connectivityManager.getNetworkCapabilities(activeNetwork) ?: return NetworkState(false, false)
        
        val isConnected = caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
        val isWifi = caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
        
        return NetworkState(isConnected = isConnected, isWifiConnected = isWifi)
    }
}
