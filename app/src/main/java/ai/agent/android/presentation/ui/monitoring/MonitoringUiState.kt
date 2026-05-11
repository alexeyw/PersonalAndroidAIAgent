package ai.agent.android.presentation.ui.monitoring

import ai.agent.android.domain.models.AgentMetrics
import ai.agent.android.domain.models.ChatMessage

/**
 * Represents the UI state of the Monitoring screen.
 *
 * @property metrics The current performance metrics of the AI agent.
 * @property recentLogs A list of recent system messages representing actions/observations.
 * @property isLoading True if logs are currently being fetched.
 * @property isPowerSavingActive True if the system is in power saving mode.
 */
data class MonitoringUiState(
    val metrics: AgentMetrics = AgentMetrics(),
    val recentLogs: List<ChatMessage> = emptyList(),
    val isLoading: Boolean = false,
    val isPowerSavingActive: Boolean = false,
)
