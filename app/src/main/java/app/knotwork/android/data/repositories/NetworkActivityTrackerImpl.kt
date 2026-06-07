package app.knotwork.android.data.repositories

import app.knotwork.android.domain.repositories.NetworkActivityTracker
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * In-memory [NetworkActivityTracker] backed by a single [MutableStateFlow].
 *
 * Lives at process scope (`@Singleton`); the value resets when the
 * process is recreated, which matches the privacy-indicator semantics
 * ("since you opened the app").
 */
@Singleton
class NetworkActivityTrackerImpl @Inject constructor() : NetworkActivityTracker {

    private val _lastOutboundAt = MutableStateFlow<Long?>(value = null)

    override val lastOutboundAt: StateFlow<Long?> = _lastOutboundAt.asStateFlow()

    override fun recordOutbound() {
        _lastOutboundAt.value = System.currentTimeMillis()
    }
}
