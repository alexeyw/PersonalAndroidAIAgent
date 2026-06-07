package app.knotwork.android.domain.services

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Unit tests for [KMeansClusterer].
 *
 * The clusterer is deterministic by construction (farthest-first seeding, no
 * randomness), so the tests assert exact partitions rather than statistical
 * properties.
 */
class KMeansClustererTest {

    private lateinit var clusterer: KMeansClusterer

    @Before
    fun setup() {
        clusterer = KMeansClusterer()
    }

    @Test
    fun `clusterCount follows sqrt N over two heuristic`() {
        assertEquals(1, clusterer.clusterCount(1))
        assertEquals(1, clusterer.clusterCount(4))
        assertEquals(1, clusterer.clusterCount(9))
        assertEquals(2, clusterer.clusterCount(16))
        assertEquals(3, clusterer.clusterCount(36))
        assertEquals(5, clusterer.clusterCount(100))
    }

    @Test
    fun `given empty input when cluster then returns empty`() {
        assertEquals(emptyList<List<Int>>(), clusterer.cluster(emptyList()))
    }

    @Test
    fun `given single vector when cluster then returns one singleton cluster`() {
        val result = clusterer.cluster(listOf(floatArrayOf(1f, 0f)))
        assertEquals(listOf(listOf(0)), result)
    }

    @Test
    fun `given two well-separated groups when cluster then splits them cleanly`() {
        // 16 vectors → k = 2. Indices 0..7 cluster near the x-axis, 8..15 near
        // the y-axis; cosine separation is sharp so the partition is exact.
        val groupA = (0 until 8).map { i -> floatArrayOf(1f, 0.02f * (i + 1)) }
        val groupB = (0 until 8).map { i -> floatArrayOf(0.02f * (i + 1), 1f) }
        val embeddings = groupA + groupB

        val clusters = clusterer.cluster(embeddings)

        assertEquals(2, clusters.size)
        val asSets = clusters.map { it.toSet() }
        assertTrue((0..7).toSet() in asSets)
        assertTrue((8..15).toSet() in asSets)
    }

    @Test
    fun `given identical vectors when cluster then collapses to a single cluster`() {
        // 16 identical vectors → k = 2 by the heuristic, but farthest-first
        // cannot find a distinct second centroid, so a single cluster covers all.
        val embeddings = (0 until 16).map { floatArrayOf(1f, 1f) }

        val clusters = clusterer.cluster(embeddings)

        assertEquals(1, clusters.size)
        assertEquals((0..15).toList(), clusters[0])
    }

    @Test
    fun `given the same input twice when cluster then results are identical`() {
        val embeddings = (0 until 25).map { i ->
            floatArrayOf((i % 5).toFloat(), (i / 5).toFloat(), 1f)
        }

        val first = clusterer.cluster(embeddings)
        val second = clusterer.cluster(embeddings)

        assertEquals(first, second)
    }

    @Test
    fun `cluster output covers every input index exactly once`() {
        val embeddings = (0 until 30).map { i -> floatArrayOf(i.toFloat(), (29 - i).toFloat()) }

        val clusters = clusterer.cluster(embeddings)

        val flattened = clusters.flatten().sorted()
        assertEquals((0 until 30).toList(), flattened)
    }
}
