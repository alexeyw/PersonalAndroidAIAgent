package ai.agent.android.data.repositories

import ai.agent.android.domain.models.AgentMetrics
import ai.agent.android.domain.repositories.MetricsRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Implementation of [MetricsRepository] that holds metrics in memory.
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
        val current = _metrics.value
        _metrics.value = current.copy(
            lastInferenceTimeMs = timeMs,
            tokensPerSecond = tps,
            totalTokensProcessed = current.totalTokensProcessed + tokensProcessed
        )
    }
}
