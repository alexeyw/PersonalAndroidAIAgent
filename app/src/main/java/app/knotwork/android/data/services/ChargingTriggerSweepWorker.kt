package app.knotwork.android.data.services

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import app.knotwork.android.domain.models.TriggerCondition
import app.knotwork.android.domain.models.TriggerEvaluationSource
import app.knotwork.android.domain.repositories.TriggerRepository
import app.knotwork.android.domain.usecases.FireTriggerUseCase
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first
import timber.log.Timber

/**
 * One-shot worker that evaluates every active **charging** trigger the moment
 * the device starts charging.
 *
 * It is registered by [WorkManagerTriggerScheduler] as a single shared
 * `setRequiresCharging(true)` one-time request: `JobScheduler` honours that
 * constraint and wakes even a closed app within seconds of the cable going in —
 * unlike a manifest `ACTION_POWER_CONNECTED` receiver, which is never delivered
 * (that broadcast is not on the implicit-broadcast exception list). Without this
 * fast path charging triggers would only be visited by the 15-minute periodic
 * poll, so plugging in could take up to a quarter of an hour to fire. The poll
 * remains as a backstop and, on unplug, re-arms and re-enqueues this one-shot
 * for the next charge edge.
 *
 * It does **no** firing logic of its own: it loads the active charging triggers
 * and hands each to [FireTriggerUseCase], which re-checks the live power state,
 * applies the armed-edge latch (so a connect fires once), verifies the bound
 * pipeline, and enqueues the run. The decision therefore stays single-sourced
 * with the poll path.
 *
 * @property triggerRepository Source of the active-trigger set.
 * @property fireTriggerUseCase The per-trigger fire/skip decision + enqueue.
 */
@HiltWorker
class ChargingTriggerSweepWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted workerParams: WorkerParameters,
    private val triggerRepository: TriggerRepository,
    private val fireTriggerUseCase: FireTriggerUseCase,
) : CoroutineWorker(context, workerParams) {

    /**
     * Fires every active charging trigger once against the current power state.
     *
     * @return [Result.success] once all charging triggers have been evaluated;
     *   [Result.retry] on an unexpected error so WorkManager re-attempts (the
     *   periodic poll is the longer-horizon backstop either way).
     */
    override suspend fun doWork(): Result = try {
        val chargingTriggers = triggerRepository.observeActiveTriggers().first()
            .filter { it.condition is TriggerCondition.Charging }
        chargingTriggers.forEach { trigger ->
            val outcome = fireTriggerUseCase(trigger.id, TriggerEvaluationSource.CHARGING_SWEEP)
            Timber.tag(TAG).d("Charging sweep handled trigger %s: %s", trigger.id, outcome)
        }
        Result.success()
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Timber.tag(TAG).e(e, "Charging-trigger sweep failed")
        Result.retry()
    }

    private companion object {
        const val TAG = "Trigger"
    }
}
