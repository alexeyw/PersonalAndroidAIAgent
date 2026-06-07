package app.knotwork.android.domain.repositories

import app.knotwork.android.domain.models.PowerState
import kotlinx.coroutines.flow.StateFlow

/**
 * Repository interface for observing the device's power and battery state.
 */
interface PowerStateRepository {
    /**
     * A [StateFlow] that continuously emits the current [PowerState] of the device.
     */
    val powerState: StateFlow<PowerState>
}
