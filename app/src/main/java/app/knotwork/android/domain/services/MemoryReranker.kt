package app.knotwork.android.domain.services

import app.knotwork.android.domain.models.MemoryChunk
import javax.inject.Inject
import kotlin.math.pow

/**
 * Re-ranks the raw cosine-similarity hits returned by the long-term memory
 * vector search into a list that better matches what a human would expect to
 * surface.
 *
 * Pure cosine similarity is a blunt instrument: a stale chunk can outrank a
 * fresh one purely on lexical overlap, restatements of one fact clutter the
 * limited context budget, and user-pinned facts are treated no differently from
 * noise. This service layers four deterministic rules on top of the raw scores
 * to fix that, each of which is independently unit-testable:
 *
 *  1. **Additive scoring** — `final = similarity + recencyBonus + pinnedBoost`.
 *     Relevance is never scaled down; freshness and pinning only ever *add*.
 *  2. **Recency bonus** — a chunk earns up to [RECENCY_BONUS] on top of its
 *     similarity, halving every `halfLifeDays` of age:
 *     `bonus = RECENCY_BONUS * 0.5^(daysSince / halfLifeDays)`. The bonus decays
 *     towards zero but never reaches it, so fresher always outranks staler at
 *     equal relevance — at any age, with no plateau.
 *  3. **Pinned boost** — pinned chunks receive a flat [PINNED_BOOST], sort ahead
 *     of every non-pinned chunk, and are **exempt from the threshold filter**,
 *     so a deliberately curated fact is always surfaced ("always at the top").
 *  4. **Threshold filter, then near-duplicate collapse** — non-pinned chunks
 *     below the caller's threshold are dropped, and among the survivors any
 *     chunk whose embedding is within
 *     [MemoryVectorSimilarity.NEAR_DUPLICATE_THRESHOLD] of an already-kept,
 *     better-ranked chunk is collapsed into it.
 *
 * **Why age must not scale relevance.** The previous model multiplied
 * similarity by a weight that fell linearly to zero at twice the half-life and
 * then thresholded the *product*. That made age a hard time-to-live: past that
 * point no chunk was retrievable at any relevance, and well before it a
 * perfectly on-topic fact was already scored below a default threshold. Under
 * the additive model the threshold judges relevance alone, so an old but
 * genuinely relevant chunk stays retrievable indefinitely, while a fresh chunk
 * of equal relevance still wins the ordering.
 *
 * **Why duplicates are detected by embedding, not by text prefix.** Collapsing
 * chunks that share their first N characters both over- and under-merges:
 * pipelines that write with a fixed preamble ("Journal entry for …") produced
 * identical prefixes for unrelated facts, while the same fact reworded kept two
 * slots. Comparing embeddings measures the thing that actually matters, and
 * reuses the exact threshold the write path uses to refuse a duplicate.
 *
 * The service is stateless and clock-free: the caller supplies `nowMillis` so
 * recency is reproducible in tests and the domain layer keeps a single
 * wall-clock read site (the retrieval use case).
 */
class MemoryReranker @Inject constructor() {

    /**
     * Applies the ranking rules to [candidates] and returns the surviving chunks
     * paired with their recomputed final score, ordered best-first (pinned
     * chunks first, then by descending final score), capped at [limit].
     *
     * @param candidates Raw vector-search hits as `(chunk, cosineSimilarity)`
     *   pairs. Order is irrelevant — this method re-sorts from scratch.
     * @param nowMillis Current wall-clock time in `System.currentTimeMillis()`
     *   units, used as the reference point for the recency bonus. Chunks with a
     *   `timestamp` in the future (clock skew) are treated as zero days old.
     * @param halfLifeDays Age, in days, at which a chunk's recency bonus halves.
     *   Coerced to at least `1` to avoid division by zero.
     * @param threshold Minimum similarity a non-pinned chunk must reach to be
     *   retained. Pinned chunks bypass this filter entirely.
     * @param limit Maximum number of chunks to return; values below `1` yield an
     *   empty list. Capping here rather than in the caller is what bounds the
     *   near-duplicate scan — see [collapseNearDuplicates].
     * @return The retained chunks with their final scores, ordered best-first.
     */
    fun rerank(
        candidates: List<Pair<MemoryChunk, Float>>,
        nowMillis: Long,
        halfLifeDays: Int,
        threshold: Float,
        limit: Int,
    ): List<Pair<MemoryChunk, Float>> {
        if (limit < 1) return emptyList()
        val safeHalfLifeDays = halfLifeDays.coerceAtLeast(1)

        val ranked = candidates
            .filter { (chunk, similarity) -> chunk.isPinned || similarity >= threshold }
            .map { (chunk, similarity) -> chunk to finalScore(chunk, similarity, nowMillis, safeHalfLifeDays) }
            .sortedWith(
                compareByDescending<Pair<MemoryChunk, Float>> { it.first.isPinned }
                    .thenByDescending { it.second },
            )

        return collapseNearDuplicates(ranked, limit)
    }

    /**
     * Walks [ranked] best-first and keeps a chunk only when it restates none of
     * the chunks kept before it, stopping as soon as [limit] chunks are kept.
     *
     * Keeping the *best-ranked* representative of a group is what preserves the
     * pinned guarantee: pinned chunks sort ahead of every non-pinned one, so a
     * later unpinned restatement can never evict the pinned original and strip
     * its threshold exemption and top-of-list slot. Among equally-pinned chunks
     * the higher final score wins, which — for equal relevance — is the fresher
     * phrasing.
     *
     * Stopping early is safe rather than approximate: the rule is greedy, so the
     * kept set after `k` survivors depends only on the candidates already
     * walked. Truncating here therefore yields exactly what collapsing the whole
     * list and taking the first [limit] entries would, while bounding the scan
     * to at most [limit] vector comparisons per candidate.
     *
     * @param ranked Threshold survivors, already ordered best-first.
     * @param limit Maximum number of chunks to keep.
     * @return The kept chunks in input order (i.e. still best-first).
     */
    private fun collapseNearDuplicates(
        ranked: List<Pair<MemoryChunk, Float>>,
        limit: Int,
    ): List<Pair<MemoryChunk, Float>> {
        val kept = ArrayList<Pair<MemoryChunk, Float>>(minOf(limit, ranked.size))
        for (candidate in ranked) {
            val isRestatement = kept.any { (keptChunk, _) ->
                MemoryVectorSimilarity.cosine(candidate.first.embedding, keptChunk.embedding) >=
                    MemoryVectorSimilarity.NEAR_DUPLICATE_THRESHOLD
            }
            if (isRestatement) continue
            kept += candidate
            if (kept.size == limit) break
        }
        return kept
    }

    /**
     * Computes the post-ranking score for a single chunk:
     * `similarity + recencyBonus + (pinned ? PINNED_BOOST : 0)`.
     *
     * Both adjustments are additive, so neither can push a relevant chunk below
     * the similarity it earned — they reorder, they do not disqualify.
     *
     * @param chunk The chunk being scored (its `isPinned` / `timestamp` drive
     *   the adjustments).
     * @param similarity The raw cosine similarity of [chunk] to the query.
     * @param nowMillis Reference time for the age calculation.
     * @param halfLifeDays Pre-validated (`>= 1`) recency half-life.
     * @return The final score used for ordering.
     */
    private fun finalScore(chunk: MemoryChunk, similarity: Float, nowMillis: Long, halfLifeDays: Int): Float {
        val pinned = if (chunk.isPinned) PINNED_BOOST else 0f
        return similarity + recencyBonus(chunk.timestamp, nowMillis, halfLifeDays) + pinned
    }

    /**
     * Derives the freshness bonus added to a chunk's similarity.
     *
     * @param timestamp The chunk's creation time in `System.currentTimeMillis()`
     *   units.
     * @param nowMillis Reference "now".
     * @param halfLifeDays Pre-validated (`>= 1`) recency half-life.
     * @return A bonus in `(0f, RECENCY_BONUS]` — the full bonus for a chunk
     *   created right now, half of it at exactly the half-life, and an
     *   ever-smaller but strictly positive amount beyond, so freshness keeps
     *   breaking ties at any age.
     */
    private fun recencyBonus(timestamp: Long, nowMillis: Long, halfLifeDays: Int): Float {
        val daysSince = ((nowMillis - timestamp).coerceAtLeast(0L)).toDouble() / MILLIS_PER_DAY
        return (RECENCY_BONUS * HALVING_BASE.pow(daysSince / halfLifeDays)).toFloat()
    }

    private companion object {
        /** Flat bonus added to a pinned chunk's final score. */
        const val PINNED_BOOST: Float = 0.2f

        /**
         * Bonus a brand-new chunk earns for freshness, halving every
         * `halfLifeDays`. Kept below [PINNED_BOOST]: pinning is a deliberate
         * user act and must outweigh mere recency.
         */
        const val RECENCY_BONUS: Double = 0.15

        /** Base of the recency decay — `0.5` makes `halfLifeDays` a half-life. */
        const val HALVING_BASE: Double = 0.5

        /** Milliseconds in one day, used to convert an age delta to days. */
        const val MILLIS_PER_DAY: Double = 86_400_000.0
    }
}
