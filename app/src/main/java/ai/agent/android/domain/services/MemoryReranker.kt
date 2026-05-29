package ai.agent.android.domain.services

import ai.agent.android.domain.models.MemoryChunk
import javax.inject.Inject

/**
 * Re-ranks the raw cosine-similarity hits returned by the long-term memory
 * vector search into a list that better matches what a human would expect to
 * surface.
 *
 * Pure cosine similarity is a blunt instrument: a stale chunk can outrank a
 * fresh one purely on lexical overlap, exact duplicates clutter the limited
 * context budget, and user-pinned facts are treated no differently from noise.
 * This service layers four deterministic rules on top of the raw scores to fix
 * that, each of which is independently unit-testable:
 *
 *  1. **Deduplication** — chunks sharing the same first [DEDUP_PREFIX_LENGTH]
 *     characters collapse to a single entry; the newest (highest `timestamp`)
 *     survivor wins so the freshest phrasing of a repeated fact is kept.
 *  2. **Recency weighting** — a non-pinned chunk's final score decays with age:
 *     `final = similarity * (1 - RECENCY_DECAY * daysSince / halfLifeDays)`,
 *     floored at zero. With [RECENCY_DECAY] = `0.5`, `halfLifeDays` is the
 *     literal half-life — a chunk exactly that old keeps half its similarity.
 *  3. **Pinned boost** — pinned chunks skip decay entirely and receive a flat
 *     [PINNED_BOOST] on top of their raw similarity. They are also sorted ahead
 *     of every non-pinned chunk and are **exempt from the threshold filter**, so
 *     a deliberately curated fact is always surfaced ("always at the top").
 *  4. **Threshold filter** — non-pinned chunks whose final score falls below the
 *     caller's threshold are dropped before they reach the prompt.
 *
 * The service is stateless and clock-free: the caller supplies `nowMillis` so
 * recency is reproducible in tests and the domain layer keeps a single
 * wall-clock read site (the retrieval use case).
 */
class MemoryReranker @Inject constructor() {

    /**
     * Applies the four ranking rules to [candidates] and returns the surviving
     * chunks paired with their recomputed final score, ordered best-first
     * (pinned chunks first, then by descending final score).
     *
     * @param candidates Raw vector-search hits as `(chunk, cosineSimilarity)`
     *   pairs. Order is irrelevant — this method re-sorts from scratch.
     * @param nowMillis Current wall-clock time in `System.currentTimeMillis()`
     *   units, used as the reference point for recency decay. Chunks with a
     *   `timestamp` in the future (clock skew) are treated as zero days old.
     * @param halfLifeDays Age, in days, at which a non-pinned chunk's recency
     *   weight reaches `0.5`. Coerced to at least `1` to avoid division by zero.
     * @param threshold Minimum final score a non-pinned chunk must reach to be
     *   retained. Pinned chunks bypass this filter entirely.
     * @return The retained chunks with their final scores, ordered best-first.
     */
    fun rerank(
        candidates: List<Pair<MemoryChunk, Float>>,
        nowMillis: Long,
        halfLifeDays: Int,
        threshold: Float,
    ): List<Pair<MemoryChunk, Float>> {
        val safeHalfLifeDays = halfLifeDays.coerceAtLeast(1)

        return deduplicate(candidates)
            .map { (chunk, similarity) -> chunk to finalScore(chunk, similarity, nowMillis, safeHalfLifeDays) }
            .filter { (chunk, score) -> chunk.isPinned || score >= threshold }
            .sortedWith(
                compareByDescending<Pair<MemoryChunk, Float>> { it.first.isPinned }
                    .thenByDescending { it.second },
            )
    }

    /**
     * Collapses near-identical chunks that share the same first
     * [DEDUP_PREFIX_LENGTH] characters of text down to a single survivor.
     *
     * A **pinned** chunk always wins its group, with the newest `timestamp` as
     * the tiebreaker; only among equally-pinned chunks does the freshest
     * phrasing win. Picking purely by timestamp would let a later auto-extracted
     * (unpinned) duplicate evict a chunk the user deliberately pinned, silently
     * stripping its threshold exemption and top-of-list guarantee.
     *
     * @param candidates Raw search hits.
     * @return One representative `(chunk, similarity)` pair per distinct prefix.
     */
    private fun deduplicate(candidates: List<Pair<MemoryChunk, Float>>): List<Pair<MemoryChunk, Float>> = candidates
        .groupBy { it.first.text.take(DEDUP_PREFIX_LENGTH) }
        .map { (_, group) ->
            group.maxWith(compareBy({ it.first.isPinned }, { it.first.timestamp }))
        }

    /**
     * Computes the post-ranking score for a single chunk.
     *
     * Pinned chunks skip recency decay and gain a flat [PINNED_BOOST]; everyone
     * else has their similarity scaled by the age-derived recency weight.
     *
     * @param chunk The chunk being scored (its `isPinned` / `timestamp` drive
     *   the formula).
     * @param similarity The raw cosine similarity of [chunk] to the query.
     * @param nowMillis Reference time for the age calculation.
     * @param halfLifeDays Pre-validated (`>= 1`) recency half-life.
     * @return The final score used for filtering and ordering.
     */
    private fun finalScore(chunk: MemoryChunk, similarity: Float, nowMillis: Long, halfLifeDays: Int): Float {
        if (chunk.isPinned) return similarity + PINNED_BOOST
        return similarity * recencyWeight(chunk.timestamp, nowMillis, halfLifeDays)
    }

    /**
     * Derives the recency weight applied to a non-pinned chunk's similarity.
     *
     * @param timestamp The chunk's creation time in `System.currentTimeMillis()`
     *   units.
     * @param nowMillis Reference "now".
     * @param halfLifeDays Pre-validated (`>= 1`) recency half-life.
     * @return A weight in `[MIN_RECENCY_WEIGHT, 1.0]` — `1.0` for a brand-new
     *   chunk, `0.5` at exactly the half-life, and floored at
     *   [MIN_RECENCY_WEIGHT] for anything older than `2 * halfLifeDays`.
     */
    private fun recencyWeight(timestamp: Long, nowMillis: Long, halfLifeDays: Int): Float {
        val daysSince = ((nowMillis - timestamp).coerceAtLeast(0L)).toDouble() / MILLIS_PER_DAY
        val weight = 1.0 - RECENCY_DECAY * daysSince / halfLifeDays
        return weight.toFloat().coerceIn(MIN_RECENCY_WEIGHT, 1f)
    }

    private companion object {
        /** Flat bonus added to a pinned chunk's final score. */
        const val PINNED_BOOST: Float = 0.2f

        /**
         * Decay coefficient in the recency formula. `0.5` makes `halfLifeDays`
         * the literal half-life (weight `0.5` at exactly that age).
         */
        const val RECENCY_DECAY: Double = 0.5

        /** Lower bound on the recency weight so old chunks never go negative. */
        const val MIN_RECENCY_WEIGHT: Float = 0f

        /** Number of leading characters compared when collapsing duplicates. */
        const val DEDUP_PREFIX_LENGTH: Int = 80

        /** Milliseconds in one day, used to convert an age delta to days. */
        const val MILLIS_PER_DAY: Double = 86_400_000.0
    }
}
