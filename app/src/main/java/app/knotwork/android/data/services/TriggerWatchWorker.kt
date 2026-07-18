package app.knotwork.android.data.services

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import app.knotwork.android.domain.models.TriggerEvaluationSource
import app.knotwork.android.domain.usecases.FireTriggerUseCase
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.CancellationException
import timber.log.Timber

/**
 * Thin background worker woken by [WorkManagerTriggerScheduler] when a trigger's
 * schedule fires (time conditions) or on its 15-minute poll (event conditions).
 *
 * It does **no** inference itself: it reads the [KEY_TRIGGER_ID] and hands the
 * fire/skip decision to [FireTriggerUseCase], which re-checks the live
 * condition, applies the edge/debounce logic, verifies the bound pipeline still
 * exists, and — when warranted — enqueues a normal background run through the
 * existing scheduler → `AgentWorker` path. Keeping the worker thin is what lets
 * the whole firing decision live in pure, unit-tested domain code.
 *
 * **Self-reclaiming.** When the outcome shows the trigger no longer warrants a
 * watch ([TriggerFireOutcome.watchStillWarranted] `== false` — deleted,
 * disabled, unbound, or auto-disabled), the worker cancels its own unique work.
 * This reclaims a watch orphaned while the process was dead (the cold-start
 * [WorkManagerTriggerScheduler.syncAll] can only re-register active triggers, it
 * cannot enumerate stale ones) on the first wake, with no in-memory bookkeeping.
 *
 * @property fireTriggerUseCase The fire/skip decision + enqueue.
 * @property workManager Used to cancel this trigger's own watch when reclaimed.
 */
@HiltWorker
class TriggerWatchWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted workerParams: WorkerParameters,
    private val fireTriggerUseCase: FireTriggerUseCase,
    private val workManager: WorkManager,
) : CoroutineWorker(context, workerParams) {

    /**
     * Handles one trigger wakeup.
     *
     * @return [Result.success] once the decision is handled (including a skip or
     *   a self-cancel); [Result.failure] when no trigger id was supplied (a
     *   malformed request that a retry cannot fix); [Result.retry] on an
     *   unexpected error so WorkManager re-attempts under the same constraints.
     */
    override suspend fun doWork(): Result {
        val triggerId = inputData.getString(KEY_TRIGGER_ID)
        if (triggerId.isNullOrBlank()) {
            Timber.tag(TAG).e("TriggerWatchWorker invoked without a trigger id.")
            return Result.failure()
        }
        return try {
            // POLL-sourced: this per-trigger periodic watch *is* the poll
            // heartbeat — a POLL row appearing for the trigger is itself the
            // "the platform did wake the poll" signal.
            val outcome = fireTriggerUseCase(triggerId, TriggerEvaluationSource.POLL)
            Timber.tag(TAG).d("Trigger %s handled: %s", triggerId, outcome)
            if (!outcome.watchStillWarranted) {
                workManager.cancelUniqueWork(UNIQUE_NAME_PREFIX + triggerId)
                Timber.tag(TAG).d("Trigger %s watch reclaimed (no longer warranted).", triggerId)
            }
            Result.success()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "Trigger %s handling failed", triggerId)
            Result.retry()
        }
    }

    companion object {
        private const val TAG = "Trigger"

        /** Input-data key carrying the id of the trigger this watch is bound to. */
        const val KEY_TRIGGER_ID = "trigger_id"

        /**
         * Prefix of the per-trigger unique work name. The full name appends the
         * trigger id so each trigger's watch is independently replaced/cancelled.
         */
        const val UNIQUE_NAME_PREFIX = "trigger-watch-"
    }
}
