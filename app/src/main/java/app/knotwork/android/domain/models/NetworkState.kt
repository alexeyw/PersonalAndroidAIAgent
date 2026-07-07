package app.knotwork.android.domain.models

/**
 * Represents the current network state of the device.
 *
 * @property isConnected True if the device is currently connected to any network.
 * @property isWifiConnected True if the device is connected specifically via Wi-Fi.
 * @property wifiSsid Name (SSID) of the connected Wi-Fi network, or `null` when
 *   not connected via Wi-Fi or the SSID cannot be read (no location permission,
 *   or the platform withheld it). Consumers must treat `null` as "unknown" and
 *   never match an SSID-scoped condition against it.
 */
data class NetworkState(
    val isConnected: Boolean = false,
    val isWifiConnected: Boolean = false,
    val wifiSsid: String? = null,
)
