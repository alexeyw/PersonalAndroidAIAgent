package ai.agent.android.data.services

import ai.agent.android.domain.repositories.MemoryRepository
import ai.agent.android.domain.services.MemoryReembedScheduler
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import timber.log.Timber
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * WorkManager-backed [MemoryReembedScheduler]: enqueues a one-off
 * [MemoryReembedWorker] to repair imported chunks off the hot path.
 *
 * Relaxed constraints (battery-not-low only) so the repair runs promptly after
 * an import rather than waiting for charge/idle — a freshly imported memory
 * should become searchable soon. Exponential backoff lets a transient provider
 * failure retry without hammering.
 *
 * [ExistingWorkPolicy.APPEND_OR_REPLACE] (not `KEEP`): if a pass is already
 * running, `KEEP` would drop a second import's enqueue, and that running pass —
 * which snapshotted the pending set before the second import — would never see
 * the newly-flagged chunks, stranding them. `APPEND_OR_REPLACE` instead chains a
 * fresh drain after the current pass (and runs immediately if the previous run
 * had failed/cancelled), so every import is guaranteed a pass that sees its
 * chunks.
 *
 * @property workManager The WorkManager instance the pass is enqueued on.
 * @property memoryRepository Source of the pending-chunk count for [rearmIfPending].
 */
@Singleton
class WorkManagerMemoryReembedScheduler @Inject constructor(
    private val workManager: WorkManager,
    private val memoryRepository: MemoryRepository,
) : MemoryReembedScheduler {

    override fun schedule() {
        val constraints = Constraints.Builder()
            .setRequiresBatteryNotLow(true)
            .build()

        val request = OneTimeWorkRequestBuilder<MemoryReembedWorker>()
            .setConstraints(constraints)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, BACKOFF_SECONDS, TimeUnit.SECONDS)
            .build()

        workManager.enqueueUniqueWork(
            MemoryReembedWorker.UNIQUE_NAME,
            ExistingWorkPolicy.APPEND_OR_REPLACE,
            request,
        )
        Timber.tag(TAG).d("Scheduled background memory re-embed pass")
    }

    override suspend fun rearmIfPending() {
        if (memoryRepository.countMemoriesNeedingReembedding() > 0) {
            Timber.tag(TAG).d("Pending re-embed chunks found on startup; re-arming the pass")
            schedule()
        }
    }

    private companion object {
        const val TAG = "MemoryReembed"

        /** Initial exponential-backoff delay between worker retries. */
        const val BACKOFF_SECONDS = 30L
    }
}
