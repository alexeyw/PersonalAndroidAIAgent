package ai.agent.android.domain.usecases

import ai.agent.android.domain.repositories.MemoryRepository
import ai.agent.android.domain.services.EmbeddingProviderResolver
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import javax.inject.Inject

/**
 * Re-embeds the memory chunks that were imported under a different embedding
 * provider, repairing their vector space lazily on the next retrieval.
 *
 * The memory import flow flags every chunk it loads under a mismatched provider
 * with `needsReembedding`; until repaired their stored vectors live in an
 * incompatible space and can never match a query (cross-dimension cosine
 * similarity collapses to `0`). [RetrieveRelevantMemoryUseCase] invokes this
 * use case before each search so the imported memories become findable the
 * first time the user asks for something — the "re-computed lazily on first
 * retrieval" contract surfaced in the import warning.
 *
 * The common path is cheap: a single `COUNT` short-circuits when nothing is
 * pending. When chunks do need repair, their texts are embedded in one batch
 * call with the active provider, then each fresh vector is written back and the
 * flag cleared. Per-chunk failures are logged and skipped so one bad row never
 * aborts a retrieval; cancellation is propagated.
 *
 * @property memoryRepository Backing store exposing the pending-chunk queries
 *   and the write-back.
 * @property embeddingProviderResolver Resolves the user's active embedding
 *   provider (with graceful on-device fallback).
 */
class RecomputePendingEmbeddingsUseCase @Inject constructor(
    private val memoryRepository: MemoryRepository,
    private val embeddingProviderResolver: EmbeddingProviderResolver,
) {
    /**
     * Repairs every pending chunk, or returns immediately when none are
     * flagged. Safe to call on every retrieval.
     *
     * @return The number of chunks successfully re-embedded.
     */
    suspend operator fun invoke(): Int = withContext(Dispatchers.IO) {
        if (memoryRepository.countMemoriesNeedingReembedding() == 0) return@withContext 0

        val pending = memoryRepository.getMemoriesNeedingReembedding()
        if (pending.isEmpty()) return@withContext 0

        val provider = embeddingProviderResolver.resolve()
        val embeddings = try {
            provider.embed(pending.map { it.text })
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Timber.w(e, "Batch re-embedding of %d imported chunks failed", pending.size)
            return@withContext 0
        }

        var repaired = 0
        pending.forEachIndexed { index, chunk ->
            val embedding = embeddings.getOrNull(index) ?: return@forEachIndexed
            try {
                memoryRepository.markMemoryReembedded(chunk.id, embedding)
                repaired++
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Timber.w(e, "Failed to persist re-embedding for memory %d", chunk.id)
            }
        }
        repaired
    }
}
