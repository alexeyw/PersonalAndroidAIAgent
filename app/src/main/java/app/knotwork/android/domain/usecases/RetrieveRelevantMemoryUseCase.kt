package app.knotwork.android.domain.usecases

import app.knotwork.android.domain.models.MemoryChunk
import app.knotwork.android.domain.repositories.MemoryRepository
import app.knotwork.android.domain.repositories.SettingsRepository
import app.knotwork.android.domain.services.EmbeddingProviderResolver
import app.knotwork.android.domain.services.MemoryReranker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import javax.inject.Inject

/**
 * Use case for retrieving the most relevant long-term memories for a given user
 * query. It embeds the query into a vector, runs a cosine-similarity search
 * against the stored memory chunks, re-ranks the full scored pool through
 * [MemoryReranker] (recency weighting, pinned boost, deduplication, threshold
 * filtering), and returns the top-K survivors.
 *
 * This is the query-string façade over the lower-level vector search
 * ([MemoryRepository.findSimilarMemories], which takes a raw embedding). It is
 * the single entry point used by the pipeline ([app.knotwork.android.domain.engine.GraphExecutionEngine]
 * resolves it once per run, keyed off the immutable user prompt).
 *
 * **Why re-rank here, not in the repository.** [MemoryRepository.findSimilarMemories]
 * is shared with [MemoryExtractionUseCase]'s near-duplicate detection, which
 * needs raw cosine similarity untouched. Re-ranking therefore lives in this
 * retrieval-only path. The search is asked for the *full* scored pool (top-K is
 * applied **after** re-ranking) so a pinned or fresh chunk that ranks below the
 * raw-cosine top-K can still be promoted into the final result.
 *
 * **Why the resolver, not a fixed engine.** The query *must* be embedded with
 * the same provider that produced the stored chunks' embeddings — otherwise the
 * vectors live in different spaces (and, for cross-dimension providers, cosine
 * similarity collapses to `0` outright). Auto-extraction
 * ([MemoryExtractionUseCase]) embeds via [EmbeddingProviderResolver]; retrieval
 * resolves the *same* active provider here so the flag actually surfaces
 * memories regardless of which backend the user selected.
 *
 * @property embeddingProviderResolver Resolves the user's active embedding
 *   provider (with graceful on-device fallback).
 * @property memoryRepository Backing store exposing the raw vector search.
 * @property memoryReranker Applies recency / pinned / dedup / threshold rules to
 *   the raw search hits.
 * @property settingsRepository Source of the default top-K / threshold /
 *   recency half-life when the caller does not override them.
 */
class RetrieveRelevantMemoryUseCase @Inject constructor(
    private val embeddingProviderResolver: EmbeddingProviderResolver,
    private val memoryRepository: MemoryRepository,
    private val memoryReranker: MemoryReranker,
    private val settingsRepository: SettingsRepository,
) {
    /**
     * Executes the retrieval process.
     *
     * @param query The text query (e.g. the user's message) to find context for.
     * @param limit Maximum number of memories to return. When `null` (the
     *   default), `SettingsRepository.memorySearchTopK` is used. Provided as an
     *   explicit override mainly for tests.
     * @param threshold Minimum final (post-rerank) score a memory must reach to
     *   be kept. Pinned chunks bypass this filter. When `null` (the default),
     *   `SettingsRepository.memorySearchThreshold` is used.
     * @return Relevant [MemoryChunk]s ordered best-first (pinned chunks first,
     *   then by descending final score), capped at the effective top-K.
     */
    suspend operator fun invoke(query: String, limit: Int? = null, threshold: Float? = null): List<MemoryChunk> =
        retrieveScored(query, limit, threshold).map { it.first }

    /**
     * Score-preserving variant of [invoke]: runs the exact same embed → search →
     * re-rank → top-K pipeline but returns each surviving chunk paired with its
     * final (post-rerank) score, best-first.
     *
     * Used by [app.knotwork.android.domain.engine.GraphExecutionEngine] to surface
     * the similarity scores in the `MemoryAccess` console event without a second
     * retrieval. [invoke] is the score-free façade callers use when they only
     * need the chunks.
     *
     * @param query The text query (e.g. the user's message) to find context for.
     * @param limit Maximum number of memories to return. When `null` (the
     *   default), `SettingsRepository.memorySearchTopK` is used.
     * @param threshold Minimum final (post-rerank) score a memory must reach to
     *   be kept. Pinned chunks bypass this filter. When `null` (the default),
     *   `SettingsRepository.memorySearchThreshold` is used.
     * @return Relevant `(chunk, finalScore)` pairs ordered best-first (pinned
     *   chunks first, then by descending final score), capped at the effective
     *   top-K.
     */
    suspend fun retrieveScored(
        query: String,
        limit: Int? = null,
        threshold: Float? = null,
    ): List<Pair<MemoryChunk, Float>> = withContext(Dispatchers.Default) {
        // Chunks imported under a different provider are repaired off the hot
        // path by MemoryReembedWorker (scheduled at import time); retrieval just
        // tolerates not-yet-repaired chunks (their cross-space vectors score ~0)
        // rather than blocking here on a potentially large re-embed.

        // Embed the query with the user's active provider so it shares the
        // stored chunks' embedding space.
        val provider = embeddingProviderResolver.resolve()
        val queryEmbedding = provider.embed(query)

        val effectiveLimit = limit ?: settingsRepository.memorySearchTopK.first()
        val effectiveThreshold = threshold ?: settingsRepository.memorySearchThreshold.first()
        val halfLifeDays = settingsRepository.memoryRecencyHalfLifeDays.first()
        val searchPoolLimit = settingsRepository.maxMemoryChunksForSearch.first()

        // Pull the full scored pool (not just the raw-cosine top-K) so the
        // re-ranker can promote a pinned or fresh chunk that the raw search
        // would have left just outside the top-K. Top-K is applied last.
        memoryRepository.findSimilarMemories(queryEmbedding, searchPoolLimit, limit = searchPoolLimit)
            .let { candidates ->
                memoryReranker.rerank(
                    candidates = candidates,
                    nowMillis = System.currentTimeMillis(),
                    halfLifeDays = halfLifeDays,
                    threshold = effectiveThreshold,
                )
            }
            .take(effectiveLimit)
    }
}
