package app.knotwork.android.data.services

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import app.knotwork.android.domain.models.TriggerCondition
import app.knotwork.android.domain.repositories.TriggerRepository
import app.knotwork.android.domain.usecases.FireTriggerUseCase
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first
import timber.log.Timber

/**
 * One-shot worker that evaluates every active **charging** trigger immediately,
 * enqueued by [PowerConnectionReceiver] the moment the device is plugged in or
 * unplugged.
 *
 * Charging triggers are otherwise only visited by the 15-minute periodic poll
 * ([WorkManagerTriggerScheduler]), so plugging in could take up to a quarter of
 * an hour to fire. Because `ACTION_POWER_CONNECTED` / `ACTION_POWER_DISCONNECTED`
 * are exempt from the implicit-broadcast ban, a manifest receiver can wake the
 * app on the edge even when it is closed; this worker turns that wake into a
 * near-instant fire. The periodic poll remains as a backstop (e.g. if a wake is
 * missed under aggressive OEM power management).
 *
 * It does **no** firing logic of its own: it loads the active charging triggers
 * and hands each to [FireTriggerUseCase], which re-checks the live power state,
 * applies the armed-edge latch (so a connect fires once and an unplug re-arms),
 * verifies the bound pipeline, and enqueues the run. The decision therefore
 * stays single-sourced with the poll path.
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
            val outcome = fireTriggerUseCase(trigger.id)
            Timber.tag(TAG).d("Charging sweep handled trigger %s: %s", trigger.id, outcome)
        }
        Result.success()
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Timber.tag(TAG).e(e, "Charging-trigger sweep failed")
        Result.retry()
    }

    companion object {
        private const val TAG = "Trigger"

        /** Unique work name coalescing rapid plug/unplug edges into one in-flight sweep. */
        const val UNIQUE_NAME = "charging-trigger-sweep"
    }
}
