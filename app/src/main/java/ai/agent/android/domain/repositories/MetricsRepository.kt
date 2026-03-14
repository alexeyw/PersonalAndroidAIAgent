package ai.agent.android.domain.repositories

import ai.agent.android.domain.models.AgentMetrics
import kotlinx.coroutines.flow.StateFlow

/**
 * Repository for managing and observing AI agent performance metrics.
 */
interface MetricsRepository {
    /**
     * A [StateFlow] emitting the current [AgentMetrics].
     */
    val metrics: StateFlow<AgentMetrics>

    /**
     * Updates the metrics with the result of a new inference run.
     *
     * @param timeMs The time taken for the inference in milliseconds.
     * @param tokensProcessed The number of tokens generated/processed.
     */
    fun updateMetrics(timeMs: Long, tokensProcessed: Int)
}
