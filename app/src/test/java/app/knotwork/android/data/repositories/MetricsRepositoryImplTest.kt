package app.knotwork.android.data.repositories

import app.knotwork.android.domain.models.NodeType
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

/**
 * Tests for [MetricsRepositoryImpl]: per-node aggregation and live-inference updates.
 */
class MetricsRepositoryImplTest {

    private lateinit var repository: MetricsRepositoryImpl

    @Before
    fun setup() {
        repository = MetricsRepositoryImpl()
    }

    @Test
    fun `given fresh repository when metrics read then all counters are zero`() {
        val metrics = repository.metrics.value
        assertEquals(0L, metrics.lastInferenceTimeMs)
        assertEquals(0f, metrics.tokensPerSecond, 0.001f)
        assertEquals(0, metrics.totalTokensProcessed)
        assertEquals(0L, metrics.totalExecutionTimeMs)
        assertEquals(emptyMap<NodeType, Long>(), metrics.timePerNodeType)
    }

    @Test
    fun `given updateMetrics when called then only last-inference fields change`() {
        repository.recordNodeExecution(NodeType.LITE_RT, durationMs = 50L, tokenCount = 7)

        repository.updateMetrics(timeMs = 2000L, tokensProcessed = 10)

        val metrics = repository.metrics.value
        assertEquals(2000L, metrics.lastInferenceTimeMs)
        assertEquals(5f, metrics.tokensPerSecond, 0.001f)
        // Aggregates must remain those set by recordNodeExecution — updateMetrics no longer touches them.
        assertEquals(50L, metrics.totalExecutionTimeMs)
        assertEquals(7, metrics.totalTokensProcessed)
        assertEquals(mapOf(NodeType.LITE_RT to 50L), metrics.timePerNodeType)
    }

    @Test
    fun `given updateMetrics with zero time when called then tokensPerSecond is zero`() {
        repository.updateMetrics(timeMs = 0L, tokensProcessed = 100)

        assertEquals(0f, repository.metrics.value.tokensPerSecond, 0.001f)
    }

    @Test
    fun `given recordNodeExecution when called multiple times then per-type totals accumulate`() {
        repository.recordNodeExecution(NodeType.LITE_RT, durationMs = 100L, tokenCount = 20)
        repository.recordNodeExecution(NodeType.LITE_RT, durationMs = 150L, tokenCount = 30)
        repository.recordNodeExecution(NodeType.INTENT_ROUTER, durationMs = 40L, tokenCount = null)

        val metrics = repository.metrics.value
        assertEquals(290L, metrics.totalExecutionTimeMs)
        assertEquals(50, metrics.totalTokensProcessed)
        assertEquals(
            mapOf(NodeType.LITE_RT to 250L, NodeType.INTENT_ROUTER to 40L),
            metrics.timePerNodeType,
        )
    }

    @Test
    fun `given recordNodeExecution with null tokens when called then totalTokens is unchanged`() {
        repository.recordNodeExecution(NodeType.IF_CONDITION, durationMs = 5L, tokenCount = null)

        assertEquals(0, repository.metrics.value.totalTokensProcessed)
        assertEquals(5L, repository.metrics.value.totalExecutionTimeMs)
        assertEquals(mapOf(NodeType.IF_CONDITION to 5L), repository.metrics.value.timePerNodeType)
    }
}
