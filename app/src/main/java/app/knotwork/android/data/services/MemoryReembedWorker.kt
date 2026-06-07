package app.knotwork.android.data.services

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import app.knotwork.android.domain.usecases.RecomputePendingEmbeddingsUseCase
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.CancellationException
import timber.log.Timber

/**
 * Background worker that re-embeds the memory chunks flagged `needsReembedding`
 * — those imported from a device whose active embedding provider differed.
 *
 * Scheduled as a one-off by [MemoryReembedScheduler] at import time (see
 * [app.knotwork.android.domain.usecases.MemoryImportUseCase]). Running the repair
 * here — rather than inline on the retrieval path — keeps retrieval O(1) and
 * gives the work WorkManager's retry/backoff: a large corpus or a temporarily
 * unavailable provider never stalls a pipeline run, and a transient failure is
 * retried later instead of abandoning the chunks. The worker is deliberately
 * thin, delegating to [RecomputePendingEmbeddingsUseCase] (mirroring how
 * [MemoryCompactionWorker] delegates to its use case).
 *
 * No agent-busy gate is needed: embedding uses the [EmbeddingProvider] backend
 * (on-device USE or a cloud endpoint), not the single-conversation
 * [app.knotwork.android.domain.engine.LlmInferenceEngine] that compaction shares
 * with foreground generation.
 *
 * @property recomputePendingEmbeddings The repair pass itself.
 */
@HiltWorker
class MemoryReembedWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted workerParams: WorkerParameters,
    private val recomputePendingEmbeddings: RecomputePendingEmbeddingsUseCase,
) : CoroutineWorker(context, workerParams) {

    /**
     * Runs one re-embed pass.
     *
     * @return [Result.success] once the pending chunks are repaired (or there
     *   are none); [Result.retry] when the embedding pass throws, so WorkManager
     *   re-attempts it with backoff and the chunks stay flagged in the meantime.
     */
    override suspend fun doWork(): Result = try {
        val repaired = recomputePendingEmbeddings()
        Timber.tag(TAG).d("Re-embedded %d imported memory chunks", repaired)
        Result.success()
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Timber.tag(TAG).w(e, "Memory re-embed pass failed; will retry")
        Result.retry()
    }

    companion object {
        private const val TAG = "MemoryReembed"

        /** Unique work name so re-scheduling never stacks duplicate passes. */
        const val UNIQUE_NAME = "memory-reembed"
    }
}
