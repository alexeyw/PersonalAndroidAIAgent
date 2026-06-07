package app.knotwork.android.domain.repositories

import app.knotwork.android.domain.models.NetworkState
import kotlinx.coroutines.flow.StateFlow

/**
 * Repository interface for observing the device's network state.
 */
interface NetworkStateRepository {
    /**
     * A [StateFlow] that continuously emits the current [NetworkState] of the device.
     */
    val networkState: StateFlow<NetworkState>
}
