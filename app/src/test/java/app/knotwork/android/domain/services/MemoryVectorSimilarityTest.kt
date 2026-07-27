package app.knotwork.android.domain.services

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for [MemoryVectorSimilarity] — the metric every stage of the
 * memory subsystem shares, plus the near-duplicate threshold expressed in it.
 */
class MemoryVectorSimilarityTest {

    @Test
    fun `given identical vectors when cosine then similarity is one`() {
        val similarity = MemoryVectorSimilarity.cosine(floatArrayOf(1f, 2f, 3f), floatArrayOf(1f, 2f, 3f))

        assertEquals(1f, similarity, EPSILON)
    }

    @Test
    fun `given parallel vectors of different magnitude when cosine then similarity is one`() {
        // Cosine is scale-invariant: only direction matters.
        val similarity = MemoryVectorSimilarity.cosine(floatArrayOf(1f, 2f), floatArrayOf(10f, 20f))

        assertEquals(1f, similarity, EPSILON)
    }

    @Test
    fun `given orthogonal vectors when cosine then similarity is zero`() {
        val similarity = MemoryVectorSimilarity.cosine(floatArrayOf(1f, 0f, 0f), floatArrayOf(0f, 1f, 0f))

        assertEquals(0f, similarity, EPSILON)
    }

    @Test
    fun `given opposite vectors when cosine then similarity is minus one`() {
        val similarity = MemoryVectorSimilarity.cosine(floatArrayOf(1f, 1f), floatArrayOf(-1f, -1f))

        assertEquals(-1f, similarity, EPSILON)
    }

    @Test
    fun `given vectors of different dimensions when cosine then similarity is zero`() {
        // A chunk embedded by another provider (awaiting the background re-embed)
        // must score 0 rather than blow up the search that walks over it.
        val similarity = MemoryVectorSimilarity.cosine(floatArrayOf(1f, 0f), floatArrayOf(1f, 0f, 0f))

        assertEquals(0f, similarity, 0f)
    }

    @Test
    fun `given empty vectors when cosine then similarity is zero`() {
        val similarity = MemoryVectorSimilarity.cosine(floatArrayOf(), floatArrayOf())

        assertEquals(0f, similarity, 0f)
    }

    @Test
    fun `given a zero-magnitude operand when cosine then similarity is zero`() {
        val similarity = MemoryVectorSimilarity.cosine(floatArrayOf(0f, 0f), floatArrayOf(1f, 1f))

        assertEquals(0f, similarity, 0f)
    }

    @Test
    fun `given the near-duplicate threshold then it sits between paraphrase and unrelated similarity`() {
        // Guards the constant against being nudged to a value that would either
        // merge distinct facts or stop catching restatements.
        assertTrue(MemoryVectorSimilarity.NEAR_DUPLICATE_THRESHOLD > 0.5f)
        assertTrue(MemoryVectorSimilarity.NEAR_DUPLICATE_THRESHOLD < 1f)
    }

    @Test
    fun `given several vectors when centroid then it is their component-wise mean`() {
        val centroid = MemoryVectorSimilarity.centroid(
            listOf(floatArrayOf(1f, 0f), floatArrayOf(0f, 1f), floatArrayOf(2f, 2f)),
        )

        assertEquals(1f, centroid[0], EPSILON)
        assertEquals(1f, centroid[1], EPSILON)
    }

    @Test
    fun `given a vector from another space when centroid then it is skipped instead of corrupting the mean`() {
        // Padding or truncating a foreign-dimension vector would silently shift
        // the centroid; the mean of the comparable vectors is the honest answer.
        val centroid = MemoryVectorSimilarity.centroid(
            listOf(floatArrayOf(1f, 0f), floatArrayOf(3f, 2f), floatArrayOf(9f, 9f, 9f)),
        )

        assertEquals(2, centroid.size)
        assertEquals(2f, centroid[0], EPSILON)
        assertEquals(1f, centroid[1], EPSILON)
    }

    @Test
    fun `given no usable vector when centroid then the result is empty and compares as zero`() {
        val centroid = MemoryVectorSimilarity.centroid(listOf(floatArrayOf(), floatArrayOf()))

        assertEquals(0, centroid.size)
        assertEquals(0f, MemoryVectorSimilarity.cosine(centroid, floatArrayOf(1f, 0f)), 0f)
    }

    @Test
    fun `given an empty list when centroid then the result is empty`() {
        assertEquals(0, MemoryVectorSimilarity.centroid(emptyList()).size)
    }

    private companion object {
        const val EPSILON: Float = 1e-4f
    }
}
