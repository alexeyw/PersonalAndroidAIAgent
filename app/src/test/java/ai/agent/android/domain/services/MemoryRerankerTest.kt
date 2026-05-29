package ai.agent.android.domain.services

import ai.agent.android.domain.models.MemoryChunk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for [MemoryReranker]. Each ranking rule — recency weighting,
 * pinned boost, deduplication, and threshold filtering — is exercised in
 * isolation, plus the edge cases (clock skew, zero half-life, recency floor).
 */
class MemoryRerankerTest {

    private val reranker = MemoryReranker()

    /**
     * Anchor "now" at a round multiple of [DAY] so chunk ages are exact whole
     * days and recency math is easy to assert.
     */
    private val now = 1_000L * DAY

    private fun chunk(
        id: Long,
        text: String = "fact-$id",
        timestamp: Long = now,
        isPinned: Boolean = false,
    ): MemoryChunk = MemoryChunk(
        id = id,
        text = text,
        embedding = floatArrayOf(1f, 0f),
        timestamp = timestamp,
        isPinned = isPinned,
    )

    // region recency weighting

    @Test
    fun `given a non-pinned chunk at the half-life when reranked then similarity is halved`() {
        val aged = chunk(id = 1, timestamp = now - 30 * DAY)

        val result = reranker.rerank(
            candidates = listOf(aged to 0.8f),
            nowMillis = now,
            halfLifeDays = 30,
            threshold = 0f,
        )

        assertEquals(1, result.size)
        // weight = 1 - 0.5 * 30/30 = 0.5  ->  0.8 * 0.5 = 0.4
        assertEquals(0.4f, result.single().second, EPSILON)
    }

    @Test
    fun `given equal similarity when one chunk is fresher then it ranks first`() {
        val fresh = chunk(id = 1, timestamp = now)
        val stale = chunk(id = 2, timestamp = now - 20 * DAY)

        val result = reranker.rerank(
            candidates = listOf(stale to 0.9f, fresh to 0.9f),
            nowMillis = now,
            halfLifeDays = 30,
            threshold = 0f,
        )

        assertEquals(listOf(1L, 2L), result.map { it.first.id })
    }

    @Test
    fun `given a chunk older than twice the half-life when reranked then recency weight floors at zero`() {
        val ancient = chunk(id = 1, timestamp = now - 100 * DAY)

        val result = reranker.rerank(
            candidates = listOf(ancient to 0.9f),
            nowMillis = now,
            halfLifeDays = 30,
            threshold = 0.55f,
        )

        // weight = 1 - 0.5 * 100/30 < 0  ->  floored to 0  ->  final 0  ->  dropped by threshold
        assertTrue(result.isEmpty())
    }

    @Test
    fun `given a future timestamp when reranked then age is clamped to zero`() {
        val skewed = chunk(id = 1, timestamp = now + 10 * DAY)

        val result = reranker.rerank(
            candidates = listOf(skewed to 0.7f),
            nowMillis = now,
            halfLifeDays = 30,
            threshold = 0f,
        )

        // Negative age clamps to 0 -> weight 1 -> final == raw similarity.
        assertEquals(0.7f, result.single().second, EPSILON)
    }

    // endregion

    // region pinned boost

    @Test
    fun `given a pinned chunk when reranked then it sorts above a higher-similarity non-pinned chunk`() {
        val pinned = chunk(id = 1, isPinned = true)
        val strong = chunk(id = 2, isPinned = false)

        val result = reranker.rerank(
            candidates = listOf(strong to 0.9f, pinned to 0.3f),
            nowMillis = now,
            halfLifeDays = 30,
            threshold = 0f,
        )

        assertEquals(listOf(1L, 2L), result.map { it.first.id })
        // pinned final = 0.3 + 0.2 boost = 0.5 (no recency decay applied)
        assertEquals(0.5f, result.first().second, EPSILON)
    }

    // endregion

    // region threshold filtering

    @Test
    fun `given a non-pinned chunk below threshold when reranked then it is dropped`() {
        val weak = chunk(id = 1)

        val result = reranker.rerank(
            candidates = listOf(weak to 0.40f),
            nowMillis = now,
            halfLifeDays = 30,
            threshold = 0.55f,
        )

        assertTrue(result.isEmpty())
    }

    @Test
    fun `given a pinned chunk below threshold when reranked then it is retained`() {
        val pinned = chunk(id = 1, isPinned = true)
        val weakNonPinned = chunk(id = 2)

        val result = reranker.rerank(
            // pinned final = 0.1 + 0.2 = 0.3 (< 0.55) but exempt; non-pinned 0.4 (< 0.55) dropped.
            candidates = listOf(pinned to 0.1f, weakNonPinned to 0.4f),
            nowMillis = now,
            halfLifeDays = 30,
            threshold = 0.55f,
        )

        assertEquals(listOf(1L), result.map { it.first.id })
    }

    // endregion

    // region deduplication

    @Test
    fun `given chunks sharing the first 80 chars when reranked then only the newest survives`() {
        val sharedPrefix = "x".repeat(80)
        val older = chunk(id = 1, text = sharedPrefix + "AAA", timestamp = now - 5 * DAY)
        val newer = chunk(id = 2, text = sharedPrefix + "BBB", timestamp = now - 1 * DAY)

        val result = reranker.rerank(
            candidates = listOf(older to 0.9f, newer to 0.9f),
            nowMillis = now,
            halfLifeDays = 30,
            threshold = 0f,
        )

        assertEquals(listOf(2L), result.map { it.first.id })
    }

    @Test
    fun `given chunks differing within the first 80 chars when reranked then both survive`() {
        val a = chunk(id = 1, text = "A".repeat(40) + "left")
        val b = chunk(id = 2, text = "A".repeat(40) + "right")

        val result = reranker.rerank(
            candidates = listOf(a to 0.9f, b to 0.9f),
            nowMillis = now,
            halfLifeDays = 30,
            threshold = 0f,
        )

        assertEquals(2, result.size)
    }

    // endregion

    // region edge cases

    @Test
    fun `given a non-positive half-life when reranked then it is coerced and does not divide by zero`() {
        val fresh = chunk(id = 1, timestamp = now)

        val result = reranker.rerank(
            candidates = listOf(fresh to 0.8f),
            nowMillis = now,
            halfLifeDays = 0,
            threshold = 0f,
        )

        // halfLife coerced to 1; age 0 -> weight 1 -> final == raw similarity.
        assertEquals(0.8f, result.single().second, EPSILON)
    }

    @Test
    fun `given no candidates when reranked then the result is empty`() {
        val result = reranker.rerank(
            candidates = emptyList(),
            nowMillis = now,
            halfLifeDays = 30,
            threshold = 0.55f,
        )

        assertTrue(result.isEmpty())
    }

    // endregion

    private companion object {
        const val DAY: Long = 86_400_000L
        const val EPSILON: Float = 1e-4f
    }
}
