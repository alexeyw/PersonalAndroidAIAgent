package ai.agent.android.domain.models

/**
 * Represents the current power state of the device.
 *
 * @property isBatteryLow True if the battery level is critically low (usually below 15%).
 * @property isCharging True if the device is currently connected to a power source and charging.
 */
data class PowerState(
    val isBatteryLow: Boolean = false,
    val isCharging: Boolean = false
)
