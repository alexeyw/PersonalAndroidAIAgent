package app.knotwork.android.data.services

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import app.knotwork.android.domain.usecases.CleanupPipelineRunsUseCase
import app.knotwork.android.domain.usecases.CleanupTriggerJournalUseCase
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.CancellationException
import timber.log.Timber

/**
 * Background worker that runs one database-retention maintenance pass.
 *
 * Scheduled by [RunRetentionScheduler] as a daily periodic job inside the
 * same charging + idle maintenance window as [MemoryCompactionWorker]. The
 * worker is deliberately thin: it delegates each policy to a dedicated use case
 * and only translates the combined outcome into a WorkManager result. It runs
 * two independent retention passes over derived, at-rest data:
 *  - [CleanupPipelineRunsUseCase] — persisted pipeline runs and their traces
 *    (per-session count, max age, the terminal-only invariant), and
 *  - [CleanupTriggerJournalUseCase] — the trigger-evaluation journal (age window
 *    + hard record cap).
 *
 * No agent-busy gate is needed: the run pass deletes **terminal** runs only, so
 * it can never race a run that is still executing or waiting, and the journal
 * pass touches a diagnostic table no live run depends on; both work on the
 * database rather than the shared inference engine.
 *
 * @property cleanupPipelineRunsUseCase The pipeline-run retention pass.
 * @property cleanupTriggerJournalUseCase The trigger-journal retention pass.
 */
@HiltWorker
class RunRetentionWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted workerParams: WorkerParameters,
    private val cleanupPipelineRunsUseCase: CleanupPipelineRunsUseCase,
    private val cleanupTriggerJournalUseCase: CleanupTriggerJournalUseCase,
) : CoroutineWorker(context, workerParams) {

    /**
     * Runs both retention passes.
     *
     * @return [Result.success] when the passes complete; [Result.retry] when
     *   one throws unexpectedly, so WorkManager re-attempts under the same
     *   constraints.
     */
    override suspend fun doWork(): Result = try {
        val outcome = cleanupPipelineRunsUseCase()
        val deletedJournalRows = cleanupTriggerJournalUseCase()
        Timber.tag(TAG).d(
            "Retention finished: %d runs deleted, %d legacy trace rows deleted, %d journal rows deleted",
            outcome.deletedRuns,
            outcome.deletedLegacyTraceRows,
            deletedJournalRows,
        )
        Result.success()
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Timber.tag(TAG).e(e, "Retention pass failed")
        Result.retry()
    }

    companion object {
        private const val TAG = "RunRetention"

        /** Unique work name for the daily periodic retention job. */
        const val UNIQUE_PERIODIC_NAME = "run-retention-periodic"
    }
}
