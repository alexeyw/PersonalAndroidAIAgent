package app.knotwork.android.data.services

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import app.knotwork.android.domain.usecases.CleanupPipelineRunsUseCase
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.CancellationException
import timber.log.Timber

/**
 * Background worker that runs one pipeline-run retention pass.
 *
 * Scheduled by [RunRetentionScheduler] as a daily periodic job inside the
 * same charging + idle maintenance window as [MemoryCompactionWorker]. The
 * worker is deliberately thin: it delegates the whole policy (per-session
 * count, max age, the terminal-only invariant) to
 * [CleanupPipelineRunsUseCase] and only translates the outcome into a
 * WorkManager result.
 *
 * No agent-busy gate is needed: the pass deletes **terminal** runs only, so
 * it can never race a run that is still executing or waiting, and it touches
 * the database rather than the shared inference engine.
 *
 * @property cleanupPipelineRunsUseCase The retention pass itself.
 */
@HiltWorker
class RunRetentionWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted workerParams: WorkerParameters,
    private val cleanupPipelineRunsUseCase: CleanupPipelineRunsUseCase,
) : CoroutineWorker(context, workerParams) {

    /**
     * Runs the retention pass.
     *
     * @return [Result.success] when the pass completes; [Result.retry] when
     *   it throws unexpectedly, so WorkManager re-attempts it under the same
     *   constraints.
     */
    override suspend fun doWork(): Result = try {
        val outcome = cleanupPipelineRunsUseCase()
        Timber.tag(TAG).d(
            "Run retention finished: %d runs deleted, %d legacy trace rows deleted",
            outcome.deletedRuns,
            outcome.deletedLegacyTraceRows,
        )
        Result.success()
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Timber.tag(TAG).e(e, "Run retention pass failed")
        Result.retry()
    }

    companion object {
        private const val TAG = "RunRetention"

        /** Unique work name for the daily periodic retention job. */
        const val UNIQUE_PERIODIC_NAME = "run-retention-periodic"
    }
}
