package app.knotwork.android.data.services

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import app.knotwork.android.domain.usecases.CleanupOrphanAttachmentsUseCase
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.CancellationException
import timber.log.Timber

/**
 * Background worker that runs one orphaned-attachment cleanup pass.
 *
 * Scheduled by [AttachmentOrphanCleanupScheduler] as a daily periodic job in
 * the same charging + idle maintenance window as [RunRetentionWorker]. Like the
 * other maintenance workers it is deliberately thin: it delegates the whole
 * diff-and-delete policy to [CleanupOrphanAttachmentsUseCase] and only
 * translates the outcome into a WorkManager result.
 *
 * No agent-busy gate is needed: the pass only deletes files no message
 * references, so it cannot race a live attachment or the inference engine.
 *
 * @property cleanupOrphanAttachmentsUseCase The cleanup pass itself.
 */
@HiltWorker
class AttachmentOrphanCleanupWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted workerParams: WorkerParameters,
    private val cleanupOrphanAttachmentsUseCase: CleanupOrphanAttachmentsUseCase,
) : CoroutineWorker(context, workerParams) {

    /**
     * Runs the orphan-cleanup pass.
     *
     * @return [Result.success] when the pass completes; [Result.retry] when it
     *   throws unexpectedly, so WorkManager re-attempts it under the same
     *   constraints.
     */
    override suspend fun doWork(): Result = try {
        val deleted = cleanupOrphanAttachmentsUseCase()
        Timber.tag(TAG).d("Attachment orphan cleanup finished: %d file(s) deleted", deleted)
        Result.success()
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Timber.tag(TAG).e(e, "Attachment orphan cleanup pass failed")
        Result.retry()
    }

    companion object {
        private const val TAG = "AttachmentCleanup"

        /** Unique work name for the daily periodic orphan-cleanup job. */
        const val UNIQUE_PERIODIC_NAME = "attachment-orphan-cleanup-periodic"
    }
}
