package ai.agent.android.domain.usecases

import ai.agent.android.domain.repositories.MemoryRepository
import ai.agent.android.domain.services.EmbeddingProviderResolver
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import javax.inject.Inject

/**
 * Re-embeds the memory chunks flagged `needsReembedding` (those imported under a
 * different embedding provider) with the active provider, so their stored
 * vectors re-enter the active embedding space and become retrievable.
 *
 * This runs **off the hot path**, driven by a background WorkManager job
 * ([ai.agent.android.data.services.MemoryReembedWorker]) that an import schedules
 * via [ai.agent.android.domain.services.MemoryReembedScheduler]. Retrieval never
 * calls it directly, so a large or temporarily-failing repair can never stall a
 * pipeline run or a search keystroke.
 *
 * Failure handling is split deliberately:
 *  - a **batch-embed failure** (provider offline, etc.) propagates so the worker
 *    can return `Result.retry()` and let WorkManager back off and re-attempt —
 *    the chunks stay flagged and are repaired on a later run rather than silently
 *    abandoned;
 *  - a **per-chunk persist failure** is logged and skipped so one bad row does
 *    not abort the rest of the batch.
 *
 * @property memoryRepository Backing store exposing the pending-chunk query and
 *   the per-chunk write-back.
 * @property embeddingProviderResolver Resolves the user's active embedding
 *   provider (with graceful on-device fallback).
 */
class RecomputePendingEmbeddingsUseCase @Inject constructor(
    private val memoryRepository: MemoryRepository,
    private val embeddingProviderResolver: EmbeddingProviderResolver,
) {
    /**
     * Repairs every pending chunk in one batch. A no-op when none are flagged.
     *
     * @return The number of chunks successfully re-embedded.
     * @throws ai.agent.android.domain.services.EmbeddingException if the batch
     *   embedding call fails (propagated so the caller can retry).
     */
    suspend operator fun invoke(): Int = withContext(Dispatchers.IO) {
        val pending = memoryRepository.getMemoriesNeedingReembedding()
        if (pending.isEmpty()) return@withContext 0

        val provider = embeddingProviderResolver.resolve()
        var repaired = 0
        // Re-embed in bounded batches rather than one giant call: caps the
        // request/payload size (a cloud provider would otherwise exceed its
        // token/payload limit or time out on a multi-thousand-chunk import) and
        // the peak memory of an on-device pass. A batch failure propagates so the
        // worker retries with backoff, while the batches already persisted below
        // preserve their progress across that retry.
        for (batch in pending.chunked(BATCH_SIZE)) {
            val embeddings = provider.embed(batch.map { it.text })
            if (embeddings.size != batch.size) {
                // The provider broke the index-aligned contract. Writing now would
                // assign the wrong vector to a chunk, so skip the whole batch
                // (chunks stay flagged for a later re-arm) rather than corrupt it.
                Timber.w(
                    "Re-embed batch size mismatch: requested %d, got %d; skipping batch to avoid misalignment",
                    batch.size,
                    embeddings.size,
                )
                continue
            }
            batch.forEachIndexed { index, chunk ->
                try {
                    memoryRepository.markMemoryReembedded(chunk.id, embeddings[index])
                    repaired++
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Timber.w(e, "Failed to persist re-embedding for memory %d", chunk.id)
                }
            }
        }
        repaired
    }

    private companion object {
        /**
         * Chunks re-embedded per provider call. Bounds request size and peak
         * memory; each batch's writes are committed before the next call, so a
         * failure mid-corpus does not discard earlier progress.
         */
        const val BATCH_SIZE = 64
    }
}
