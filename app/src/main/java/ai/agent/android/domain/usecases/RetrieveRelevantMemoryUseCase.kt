package ai.agent.android.domain.usecases

import ai.agent.android.domain.engine.TextEmbeddingEngine
import ai.agent.android.domain.models.MemoryChunk
import ai.agent.android.domain.repositories.MemoryRepository
import ai.agent.android.domain.repositories.SettingsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import javax.inject.Inject

/**
 * Use case for retrieving the most relevant long-term memories based on a given user query.
 * It first converts the query text into a vector embedding, and then performs a similarity
 * search against the stored memory chunks.
 */
class RetrieveRelevantMemoryUseCase @Inject constructor(
    private val textEmbeddingEngine: TextEmbeddingEngine,
    private val memoryRepository: MemoryRepository,
    private val settingsRepository: SettingsRepository
) {
    /**
     * Executes the retrieval process.
     *
     * @param query The text query (e.g., user's message) to find context for.
     * @param limit The maximum number of memories to return. Defaults to 3.
     * @param threshold The minimum cosine similarity score (0.0 to 1.0) for a memory to be considered relevant.
     * @return A list of relevant [MemoryChunk]s that meet the threshold.
     */
    suspend operator fun invoke(
        query: String, 
        limit: Int = 3,
        threshold: Float = 0.5f
    ): List<MemoryChunk> = withContext(Dispatchers.Default) {
        
        // 1. Generate embedding for the query
        val queryEmbedding = textEmbeddingEngine.generateEmbedding(query)
        
        // 2. Find similar memories
        val searchPoolLimit = settingsRepository.maxMemoryChunksForSearch.first()
        val similarMemories = memoryRepository.findSimilarMemories(queryEmbedding, searchPoolLimit, limit)
        
        // 3. Filter by threshold and extract just the chunks
        similarMemories
            .filter { it.second >= threshold }
            .map { it.first }
    }
}
