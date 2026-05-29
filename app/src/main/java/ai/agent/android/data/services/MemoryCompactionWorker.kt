package ai.agent.android.data.services

import ai.agent.android.domain.repositories.SettingsRepository
import ai.agent.android.domain.usecases.MemoryCompactionUseCase
import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first
import timber.log.Timber

/**
 * Background worker that runs one long-term memory compaction pass.
 *
 * Scheduled by [MemoryCompactionScheduler] both as a daily periodic job
 * (constrained to charging + idle so it never costs the user battery) and as an
 * out-of-schedule one-off when the chunk count crosses the hard limit. The
 * worker itself is deliberately thin: it gates on the
 * [SettingsRepository.memoryCompactionEnabled] toggle and delegates the actual
 * clustering and consolidation to [MemoryCompactionUseCase] — mirroring how
 * [AgentWorker] delegates to its orchestrator use case.
 *
 * @property memoryCompactionUseCase The compaction pass itself.
 * @property settingsRepository Source of the compaction toggle, re-read on every
 *   run so a user disabling the feature cancels an already-queued job.
 */
@HiltWorker
class MemoryCompactionWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted workerParams: WorkerParameters,
    private val memoryCompactionUseCase: MemoryCompactionUseCase,
    private val settingsRepository: SettingsRepository,
) : CoroutineWorker(context, workerParams) {

    /**
     * Runs the compaction pass.
     *
     * @return [Result.success] when the toggle is off (no-op) or the pass
     *   completes; [Result.retry] when the pass throws unexpectedly, so
     *   WorkManager re-attempts it under the same constraints.
     */
    override suspend fun doWork(): Result {
        if (!settingsRepository.memoryCompactionEnabled.first()) {
            Timber.tag(TAG).d("Memory compaction disabled; skipping run")
            return Result.success()
        }

        return try {
            val outcome = memoryCompactionUseCase()
            Timber.tag(TAG).d(
                "Memory compaction finished: %d clusters, %d chunks merged into %d",
                outcome.clustersProcessed,
                outcome.chunksConsolidated,
                outcome.chunksCreated,
            )
            Result.success()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "Memory compaction run failed")
            Result.retry()
        }
    }

    companion object {
        private const val TAG = "MemoryCompaction"

        /** Unique work name for the daily periodic compaction job. */
        const val UNIQUE_PERIODIC_NAME = "memory-compaction-periodic"

        /** Unique work name for the out-of-schedule hard-limit compaction job. */
        const val UNIQUE_IMMEDIATE_NAME = "memory-compaction-immediate"
    }
}
