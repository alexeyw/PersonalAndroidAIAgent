package app.knotwork.android.data.repositories

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.wifi.WifiInfo
import android.net.wifi.WifiManager
import androidx.core.content.ContextCompat
import app.knotwork.android.domain.models.NetworkState
import app.knotwork.android.domain.repositories.NetworkStateRepository
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
class NetworkStateRepositoryImpl @Inject constructor(@ApplicationContext private val context: Context) :
    NetworkStateRepository {

    private val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

    private val _networkState = MutableStateFlow(getCurrentState())
    override val networkState: StateFlow<NetworkState> = _networkState.asStateFlow()

    // FLAG_INCLUDE_LOCATION_INFO makes onCapabilitiesChanged deliver an un-redacted
    // WifiInfo (with the SSID) in NetworkCapabilities.transportInfo, provided the
    // app also holds ACCESS_FINE_LOCATION. Without the flag the SSID is always
    // redacted even when the permission is granted.
    private val networkCallback = object : ConnectivityManager.NetworkCallback(FLAG_INCLUDE_LOCATION_INFO) {
        override fun onAvailable(network: Network) {
            _networkState.value = getCurrentState()
        }

        override fun onLost(network: Network) {
            _networkState.value = getCurrentState()
        }

        override fun onCapabilitiesChanged(network: Network, networkCapabilities: NetworkCapabilities) {
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
        val caps =
            capabilities ?: connectivityManager.getNetworkCapabilities(activeNetwork)
                ?: return NetworkState(false, false)

        val isConnected = caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
        val isWifi = caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)

        return NetworkState(
            isConnected = isConnected,
            isWifiConnected = isWifi,
            wifiSsid = if (isWifi) readSsid(caps) else null,
        )
    }

    /**
     * Best-effort read of the connected Wi-Fi network name from [caps].
     *
     * Returns `null` — treated everywhere as "unknown" — when the location
     * permission is not granted, the transport info is not a [WifiInfo], or the
     * SSID is the framework's redaction sentinel. Never throws.
     *
     * @param caps Capabilities of the active Wi-Fi network.
     * @return The unquoted SSID, or `null` when it cannot be determined.
     */
    private fun readSsid(caps: NetworkCapabilities): String? {
        if (!hasLocationPermission()) return null
        val wifiInfo = caps.transportInfo as? WifiInfo ?: return null
        val raw = wifiInfo.ssid ?: return null
        // WifiInfo.ssid comes back double-quoted for UTF-8 SSIDs and equals the
        // literal UNKNOWN_SSID sentinel ("<unknown ssid>") when redacted.
        if (raw == WifiManager.UNKNOWN_SSID) return null
        return raw.trim().removeSurrounding("\"").takeIf { it.isNotBlank() }
    }

    /** Whether the app currently holds the runtime permission needed to read the SSID. */
    private fun hasLocationPermission(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED
}
