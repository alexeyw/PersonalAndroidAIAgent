package ai.agent.android.presentation.ui.monitoring

import ai.agent.android.domain.models.ChatMessage
import ai.agent.android.domain.repositories.ChatRepository
import ai.agent.android.domain.repositories.MetricsRepository
import ai.agent.android.domain.repositories.PowerStateRepository
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * ViewModel for the Monitoring and Task Status screen.
 * Aggregates system logs and agent performance metrics.
 */
@HiltViewModel
class MonitoringViewModel @Inject constructor(
    private val chatRepository: ChatRepository,
    metricsRepository: MetricsRepository,
    powerStateRepository: PowerStateRepository,
) : ViewModel() {

    private val _isLoading = MutableStateFlow(true)
    private val _recentLogs = MutableStateFlow<List<ChatMessage>>(emptyList())

    /**
     * The combined state containing metrics and logs.
     */
    val uiState: StateFlow<MonitoringUiState> = combine(
        metricsRepository.metrics,
        _recentLogs,
        _isLoading,
        powerStateRepository.powerState,
    ) { metrics, logs, isLoading, powerState ->
        MonitoringUiState(
            metrics = metrics,
            recentLogs = logs,
            isLoading = isLoading,
            isPowerSavingActive = powerState.isBatteryLow && !powerState.isCharging,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(STATE_STOP_TIMEOUT_MS),
        initialValue = MonitoringUiState(isLoading = true),
    )

    init {
        loadLogs()
    }

    /**
     * Loads the latest system logs (actions and observations).
     */
    fun loadLogs() {
        viewModelScope.launch {
            _isLoading.value = true
            chatRepository.getRecentSystemMessages(limit = 50).collect { logs ->
                _recentLogs.value = logs
                _isLoading.value = false
            }
        }
    }

    private companion object {
        /**
         * Grace period in milliseconds the upstream flow keeps running after the
         * last subscriber detaches; protects the merge against quick navigation
         * round-trips that would otherwise tear down the chain.
         */
        const val STATE_STOP_TIMEOUT_MS: Long = 5_000L
    }
}
