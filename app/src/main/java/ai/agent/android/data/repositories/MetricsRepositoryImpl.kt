package ai.agent.android.data.repositories

import ai.agent.android.domain.models.AgentMetrics
import ai.agent.android.domain.models.NodeType
import ai.agent.android.domain.repositories.MetricsRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Implementation of [MetricsRepository] that holds metrics in memory.
 *
 * The implementation is intentionally process-local: metrics are session-scoped and
 * reset on process death. Persistence is unnecessary because the user-visible charts
 * always show "current session" figures.
 */
@Singleton
class MetricsRepositoryImpl @Inject constructor() : MetricsRepository {
    private val _metrics = MutableStateFlow(AgentMetrics())
    override val metrics: StateFlow<AgentMetrics> = _metrics.asStateFlow()

    override fun updateMetrics(timeMs: Long, tokensProcessed: Int) {
        val tps = if (timeMs > 0) {
            (tokensProcessed.toFloat() / (timeMs.toFloat() / 1000f))
        } else {
            0f
        }
        _metrics.update { current ->
            current.copy(
                lastInferenceTimeMs = timeMs,
                tokensPerSecond = tps,
            )
        }
    }

    override fun recordNodeExecution(nodeType: NodeType, durationMs: Long, tokenCount: Int?) {
        _metrics.update { current ->
            val updatedPerType = current.timePerNodeType.toMutableMap().apply {
                put(nodeType, (getOrDefault(nodeType, 0L)) + durationMs)
            }
            current.copy(
                totalExecutionTimeMs = current.totalExecutionTimeMs + durationMs,
                totalTokensProcessed = current.totalTokensProcessed + (tokenCount ?: 0),
                timePerNodeType = updatedPerType,
            )
        }
    }
}
