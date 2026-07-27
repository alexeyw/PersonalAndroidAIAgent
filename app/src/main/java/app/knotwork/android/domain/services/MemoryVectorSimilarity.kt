package app.knotwork.android.domain.services

import kotlin.math.sqrt

/**
 * The single implementation of the vector metric used across the long-term
 * memory subsystem, plus the near-duplicate threshold expressed in that metric.
 *
 * Every stage of the memory lifecycle compares embeddings: the vector search
 * ranks stored chunks against the query, extraction rejects facts that restate
 * something already stored, the re-ranker collapses restatements that survived
 * into the retrieval pool, and compaction clusters chunks before summarising
 * them. Those stages must agree on what "similar" means — a stage using a
 * subtly different formula (or a different duplicate threshold) would accept as
 * novel exactly what another stage treats as a duplicate. Hence one object.
 */
object MemoryVectorSimilarity {

    /**
     * Cosine similarity at or above which two embeddings are treated as
     * restatements of the same fact.
     *
     * Fixed rather than user-tunable, and deliberately shared between the write
     * path ([app.knotwork.android.domain.usecases.MemoryExtractionUseCase],
     * which refuses to store a near-duplicate) and the read path
     * ([MemoryReranker], which collapses near-duplicates that reached the
     * retrieval pool anyway — through manual saves, imports, compaction, or
     * chunks written before this threshold existed). A read-side value below
     * the write-side one would merge chunks the writer deliberately kept apart;
     * a value above it would leave restatements to compete for the same context
     * budget. Both risks are avoided by there being one number.
     */
    const val NEAR_DUPLICATE_THRESHOLD: Float = 0.92f

    /**
     * Computes the cosine similarity of two embedding vectors.
     *
     * @param a First vector.
     * @param b Second vector.
     * @return Similarity in `-1f..1f`, or `0f` when the vectors cannot be
     *   meaningfully compared: differing dimensions (chunks embedded by
     *   different providers, e.g. one awaiting the background re-embed), an
     *   empty vector, or a zero-magnitude operand. Returning `0f` rather than
     *   throwing keeps a not-yet-repaired chunk merely unmatched instead of
     *   breaking the search that encounters it.
     */
    fun cosine(a: FloatArray, b: FloatArray): Float {
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
}
