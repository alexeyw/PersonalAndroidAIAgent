package ai.agent.android.domain.usecases

import ai.agent.android.domain.models.MemoryChunk
import ai.agent.android.domain.repositories.MemoryRepository
import ai.agent.android.domain.repositories.SettingsRepository
import ai.agent.android.domain.services.EmbeddingProviderResolver
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import javax.inject.Inject

/**
 * Use case for retrieving the most relevant long-term memories for a given user
 * query. It embeds the query into a vector and runs a cosine-similarity search
 * against the stored memory chunks, then keeps only the hits that clear the
 * configured relevance threshold.
 *
 * This is the query-string façade over the lower-level vector search
 * ([MemoryRepository.findSimilarMemories], which takes a raw embedding). It is
 * the single entry point used by the pipeline ([ai.agent.android.domain.engine.GraphExecutionEngine]
 * resolves it once per run, keyed off the immutable user prompt).
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
 * @property settingsRepository Source of the default top-K / threshold when the
 *   caller does not override them.
 */
class RetrieveRelevantMemoryUseCase @Inject constructor(
    private val embeddingProviderResolver: EmbeddingProviderResolver,
    private val memoryRepository: MemoryRepository,
    private val settingsRepository: SettingsRepository,
) {
    /**
     * Executes the retrieval process.
     *
     * @param query The text query (e.g. the user's message) to find context for.
     * @param limit Maximum number of memories to return. When `null` (the
     *   default), `SettingsRepository.memorySearchTopK` is used. Provided as an
     *   explicit override mainly for tests.
     * @param threshold Minimum cosine-similarity score (0.0–1.0) a memory must
     *   reach to be kept. When `null` (the default),
     *   `SettingsRepository.memorySearchThreshold` is used.
     * @return Relevant [MemoryChunk]s that clear the threshold, ordered by
     *   descending similarity.
     */
    suspend operator fun invoke(query: String, limit: Int? = null, threshold: Float? = null): List<MemoryChunk> =
        withContext(Dispatchers.Default) {
            // Embed the query with the user's active provider so it shares the
            // stored chunks' embedding space.
            val provider = embeddingProviderResolver.resolve()
            val queryEmbedding = provider.embed(query)

            val effectiveLimit = limit ?: settingsRepository.memorySearchTopK.first()
            val effectiveThreshold = threshold ?: settingsRepository.memorySearchThreshold.first()
            val searchPoolLimit = settingsRepository.maxMemoryChunksForSearch.first()

            memoryRepository.findSimilarMemories(queryEmbedding, searchPoolLimit, effectiveLimit)
                .filter { it.second >= effectiveThreshold }
                .map { it.first }
        }
}
