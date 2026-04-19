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
     * Updates the "last inference" metrics with the result of a new LLM inference run.
     * Does NOT modify aggregated counters — call [recordNodeExecution] for that.
     *
     * @param timeMs The time taken for the inference in milliseconds.
     * @param tokensProcessed The number of tokens generated/processed.
     */
    fun updateMetrics(timeMs: Long, tokensProcessed: Int)

    /**
     * Records the completion of a single pipeline node execution for aggregated statistics.
     * Updates `totalExecutionTimeMs`, `timePerNodeType`, and (when tokens are available)
     * `totalTokensProcessed` so the statistics screen can display cumulative usage.
     *
     * @param nodeName The node type name (e.g. "LLM", "TOOL", "INTENT_ROUTER").
     * @param durationMs Wall-clock time the node took, in milliseconds.
     * @param tokenCount Approximate tokens produced by the node, or `null` for non-LLM nodes.
     */
    fun recordNodeExecution(nodeName: String, durationMs: Long, tokenCount: Int?)
}
