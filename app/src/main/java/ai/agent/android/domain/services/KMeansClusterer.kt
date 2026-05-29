package ai.agent.android.domain.services

import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.floor
import kotlin.math.sqrt

/**
 * Deterministic k-means clusterer over embedding vectors, used by the
 * background memory-compaction worker
 * ([ai.agent.android.domain.usecases.MemoryCompactionUseCase]) to group
 * semantically-similar long-term memory chunks before consolidating each dense
 * group into a single fact.
 *
 * **Why deterministic.** Classic k-means seeds its centroids randomly, which
 * makes the same input cluster differently on each run and makes unit tests
 * flaky. This implementation instead seeds with a **farthest-first traversal**
 * (the first centroid is index `0`; each subsequent centroid is the point with
 * the greatest cosine distance to the already-chosen centroids), so the output
 * is a pure function of the input — reproducible across runs and across
 * processes. It also avoids `Math.random()`, which the domain layer never uses.
 *
 * **Metric.** Cosine distance (`1 - cosineSimilarity`) is used throughout,
 * matching every other similarity computation in the memory subsystem
 * ([MemoryReranker], `MemoryRepositoryImpl`). Centroids are recomputed as the
 * component-wise mean of their members; cosine ignores magnitude, so an
 * un-normalised mean is a fine centroid for assignment.
 *
 * The clusterer is stateless and therefore a process-wide [Singleton].
 */
@Singleton
class KMeansClusterer @Inject constructor() {

    /**
     * Partitions [embeddings] into clusters and returns the **input indices**
     * grouped by cluster.
     *
     * The cluster count is `k = max(1, floor(sqrt(N) / 2))` where `N` is the
     * number of embeddings — a coarse heuristic that keeps cluster sizes
     * growing slower than the corpus so consolidation stays meaningful as
     * memory accumulates.
     *
     * @param embeddings Vectors to cluster. All are expected to share the same
     *   dimension (the first vector's dimension is taken as canonical); a
     *   differently-sized vector simply scores zero similarity and lands in the
     *   first cluster. An empty input yields an empty result.
     * @return One inner list of original indices per non-empty cluster. The
     *   union of all inner lists is exactly `0 until embeddings.size`.
     */
    fun cluster(embeddings: List<FloatArray>): List<List<Int>> {
        val n = embeddings.size
        if (n == 0) return emptyList()
        if (n == 1) return listOf(listOf(0))

        val k = clusterCount(n)
        if (k <= 1) return listOf(embeddings.indices.toList())
        if (k >= n) return embeddings.indices.map { listOf(it) }

        val centroids = seedCentroids(embeddings, k)
        var assignments = assign(embeddings, centroids)

        repeat(MAX_ITERATIONS) {
            recomputeCentroids(embeddings, assignments, centroids)
            val next = assign(embeddings, centroids)
            if (next.contentEquals(assignments)) return@repeat
            assignments = next
        }

        return groupByCluster(assignments, k)
    }

    /**
     * Computes the heuristic cluster count `max(1, floor(sqrt(N) / 2))`.
     *
     * @param n Number of points to cluster (must be positive).
     * @return The number of clusters to form.
     */
    internal fun clusterCount(n: Int): Int = maxOf(1, floor(sqrt(n.toDouble()) / 2.0).toInt())

    /**
     * Seeds [k] centroids via farthest-first traversal: index `0` is the first
     * centroid, then each subsequent centroid is the point whose minimum cosine
     * distance to the already-chosen centroids is the largest. Ties resolve to
     * the lowest index, keeping the seeding deterministic.
     *
     * @return A fresh, independent copy of each chosen point's vector.
     */
    private fun seedCentroids(embeddings: List<FloatArray>, k: Int): Array<FloatArray> {
        val chosen = ArrayList<Int>(k)
        chosen += 0
        while (chosen.size < k) {
            var bestIndex = -1
            var bestDistance = Float.NEGATIVE_INFINITY
            for (i in embeddings.indices) {
                if (i in chosen) continue
                val minDistanceToChosen = chosen.minOf { cosineDistance(embeddings[i], embeddings[it]) }
                if (minDistanceToChosen > bestDistance) {
                    bestDistance = minDistanceToChosen
                    bestIndex = i
                }
            }
            // No distinct candidate left (all remaining are identical to a
            // chosen centroid) — stop early; the remaining clusters stay empty.
            if (bestIndex == -1) break
            chosen += bestIndex
        }
        return Array(chosen.size) { embeddings[chosen[it]].copyOf() }
    }

    /**
     * Assigns every point to the nearest centroid (maximum cosine similarity),
     * breaking ties toward the lowest centroid index.
     *
     * @return An array, index-aligned with [embeddings], of centroid indices.
     */
    private fun assign(embeddings: List<FloatArray>, centroids: Array<FloatArray>): IntArray =
        IntArray(embeddings.size) { i ->
            var best = 0
            var bestSimilarity = Float.NEGATIVE_INFINITY
            for (c in centroids.indices) {
                val similarity = cosineSimilarity(embeddings[i], centroids[c])
                if (similarity > bestSimilarity) {
                    bestSimilarity = similarity
                    best = c
                }
            }
            best
        }

    /**
     * Recomputes each centroid in place as the component-wise mean of the
     * points currently assigned to it. A centroid that lost all its members is
     * left unchanged so it keeps competing for points on the next pass.
     */
    private fun recomputeCentroids(embeddings: List<FloatArray>, assignments: IntArray, centroids: Array<FloatArray>) {
        val dimension = centroids[0].size
        for (c in centroids.indices) {
            val sum = FloatArray(dimension)
            var count = 0
            for (i in embeddings.indices) {
                if (assignments[i] != c) continue
                val vector = embeddings[i]
                if (vector.size != dimension) continue
                for (d in 0 until dimension) {
                    sum[d] += vector[d]
                }
                count++
            }
            if (count == 0) continue
            for (d in 0 until dimension) {
                sum[d] /= count
            }
            centroids[c] = sum
        }
    }

    /**
     * Folds the flat [assignments] array into one index list per cluster,
     * dropping clusters that ended up empty.
     */
    private fun groupByCluster(assignments: IntArray, k: Int): List<List<Int>> {
        val buckets = Array(k) { mutableListOf<Int>() }
        assignments.forEachIndexed { index, cluster -> buckets[cluster] += index }
        return buckets.filter { it.isNotEmpty() }.map { it.toList() }
    }

    /** Cosine distance `1 - cosineSimilarity`, in `0f..2f`. */
    private fun cosineDistance(a: FloatArray, b: FloatArray): Float = 1f - cosineSimilarity(a, b)

    /**
     * Cosine similarity between two equal-length vectors. Returns `0` for
     * mismatched, empty, or zero-magnitude operands — mirroring the metric used
     * across the rest of the memory subsystem.
     */
    private fun cosineSimilarity(a: FloatArray, b: FloatArray): Float {
        if (a.size != b.size || a.isEmpty()) return 0f
        var dot = 0f
        var normA = 0f
        var normB = 0f
        for (i in a.indices) {
            dot += a[i] * b[i]
            normA += a[i] * a[i]
            normB += b[i] * b[i]
        }
        if (normA == 0f || normB == 0f) return 0f
        return dot / (sqrt(normA) * sqrt(normB))
    }

    private companion object {
        /**
         * Upper bound on Lloyd refinement passes. Convergence on embedding-sized
         * inputs is typically reached well before this; the cap just guards
         * against a pathological oscillation that never stabilises.
         */
        const val MAX_ITERATIONS = 20
    }
}
