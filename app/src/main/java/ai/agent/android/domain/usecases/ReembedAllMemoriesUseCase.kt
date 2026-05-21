package ai.agent.android.domain.usecases

import ai.agent.android.domain.engine.TextEmbeddingEngine
import ai.agent.android.domain.repositories.MemoryRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import timber.log.Timber
import javax.inject.Inject

/**
 * Re-runs the active [TextEmbeddingEngine] over every stored memory
 * chunk and writes the fresh embedding back. Backs the Settings →
 * Memory → Re-embed action.
 *
 * Streams progress as a fraction `0f..1f` so the UI can render an
 * inline progress indicator without polling. Emits a final `1f` once
 * every chunk has been re-encoded and persisted.
 *
 * Errors on individual chunks are logged and skipped — a single bad
 * row should not abort the rest of the batch. The caller can still
 * inspect the final emitted value (which will be `1f` once the iteration
 * finishes) and the Timber log to spot partial failures.
 */
class ReembedAllMemoriesUseCase @Inject constructor(
    private val memoryRepository: MemoryRepository,
    private val embeddingEngine: TextEmbeddingEngine,
) {
    /**
     * @return A cold [Flow] that begins at `0f` and ticks up to `1f` as
     *   chunks are re-embedded; consumers should `collect` it inside a
     *   `viewModelScope` (or hand it to a WorkManager worker for
     *   resilience).
     */
    operator fun invoke(): Flow<Float> = flow {
        val memories = memoryRepository.getAllMemories()
        if (memories.isEmpty()) {
            emit(1f)
            return@flow
        }
        emit(0f)
        memories.forEachIndexed { index, memory ->
            runCatching { embeddingEngine.generateEmbedding(memory.text) }
                .onSuccess { embedding ->
                    memoryRepository.updateMemory(memory.id, memory.text, embedding)
                }
                .onFailure { error -> Timber.w(error, "Failed to re-embed memory ${memory.id}") }
            emit((index + 1).toFloat() / memories.size)
        }
    }.flowOn(Dispatchers.IO)
}
