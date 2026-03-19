package ai.agent.android.domain.models

/**
 * Represents the current network state of the device.
 *
 * @property isConnected True if the device is currently connected to any network.
 * @property isWifiConnected True if the device is connected specifically via Wi-Fi.
 */
data class NetworkState(
    val isConnected: Boolean = false,
    val isWifiConnected: Boolean = false
)
