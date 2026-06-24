package app.knotwork.android.domain.usecases

import app.knotwork.android.domain.models.RunOrigin
import app.knotwork.android.domain.repositories.NetworkStateRepository
import app.knotwork.android.domain.repositories.PipelineRepository
import app.knotwork.android.domain.repositories.PowerStateRepository
import app.knotwork.android.domain.repositories.TriggerRepository
import app.knotwork.android.domain.services.ScheduledTaskConstraints
import app.knotwork.android.domain.services.TaskScheduler
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Outcome of a single trigger-fire attempt, returned for logging and testing.
 */
sealed interface TriggerFireOutcome {

    /**
     * A background run of [pipelineId] was enqueued.
     *
     * @property pipelineId The pipeline that was scheduled.
     */
    data class Fired(val pipelineId: String) : TriggerFireOutcome

    /**
     * The trigger did not fire this time.
     *
     * @property reason Why it was skipped.
     */
    data class Skipped(val reason: TriggerSkipReason) : TriggerFireOutcome

    /**
     * The trigger's bound pipeline no longer exists, so the trigger was
     * auto-disabled to stop it waking pointlessly.
     *
     * @property pipelineId The id of the now-missing pipeline.
     */
    data class Disabled(val pipelineId: String) : TriggerFireOutcome

    /** No trigger row matched the id (deleted between wakeup and handling). */
    data object NotFound : TriggerFireOutcome
}

/**
 * Handles a background wakeup for a single trigger: load it, decide via the pure
 * [EvaluateTriggerFiringUseCase], and act.
 *
 * On [TriggerFiringDecision.Fire] it does **not** run inference itself — it
 * delegates to the existing [TaskScheduler.scheduleOneTime] path so the run goes
 * through `AgentWorker` exactly like a scheduled task (session resolution,
 * foreground promotion, completion notification and engine unload all reused),
 * attributed to [RunOrigin.TRIGGER]. Result delivery / notification refinement
 * is a later task; this task only enqueues the run.
 *
 * Two safety behaviours are enforced here:
 * - **Deleted bound pipeline.** If the resolved pipeline id no longer maps to a
 *   pipeline, the trigger is auto-disabled rather than firing into nothing.
 * - **Idempotency.** The fire-time stamp is written via
 *   [TriggerRepository.markFired], feeding the evaluator's re-arm debounce so a
 *   still-satisfied constraint cannot enqueue duplicate runs.
 */
@Singleton
class FireTriggerUseCase @Inject constructor(
    private val triggerRepository: TriggerRepository,
    private val pipelineRepository: PipelineRepository,
    private val powerStateRepository: PowerStateRepository,
    private val networkStateRepository: NetworkStateRepository,
    private val evaluateTriggerFiring: EvaluateTriggerFiringUseCase,
    private val taskScheduler: TaskScheduler,
) {

    /**
     * Evaluates and, if warranted, fires the trigger identified by [triggerId].
     *
     * @param triggerId The id of the trigger that woke the background runtime.
     * @param nowMillis Current wall-clock time, epoch-millis (injectable for tests).
     * @return The [TriggerFireOutcome] describing what happened.
     */
    suspend operator fun invoke(triggerId: String, nowMillis: Long = System.currentTimeMillis()): TriggerFireOutcome {
        val trigger = triggerRepository.getTriggerById(triggerId)
        if (trigger == null) {
            Timber.tag(TAG).d("Trigger %s no longer exists; nothing to fire.", triggerId)
            return TriggerFireOutcome.NotFound
        }

        val decision = evaluateTriggerFiring(
            trigger = trigger,
            power = powerStateRepository.powerState.value,
            network = networkStateRepository.networkState.value,
            nowMillis = nowMillis,
        )

        return when (decision) {
            is TriggerFiringDecision.Skip -> {
                Timber.tag(TAG).d("Trigger %s skipped: %s", triggerId, decision.reason)
                TriggerFireOutcome.Skipped(decision.reason)
            }

            is TriggerFiringDecision.Fire -> fire(triggerId, decision, nowMillis)
        }
    }

    /**
     * Enqueues the bound pipeline run after a final existence check, or disables
     * the trigger when the bound pipeline has been deleted.
     */
    private suspend fun fire(
        triggerId: String,
        decision: TriggerFiringDecision.Fire,
        nowMillis: Long,
    ): TriggerFireOutcome {
        if (pipelineRepository.getPipelineById(decision.pipelineId) == null) {
            triggerRepository.setEnabled(triggerId, false)
            Timber.tag(TAG).w("Trigger %s bound pipeline %s missing; auto-disabled.", triggerId, decision.pipelineId)
            return TriggerFireOutcome.Disabled(decision.pipelineId)
        }

        taskScheduler.scheduleOneTime(
            prompt = decision.prompt,
            delayMinutes = 0,
            sessionId = null,
            constraints = ScheduledTaskConstraints(requiresBatteryNotLow = true),
            pipelineId = decision.pipelineId,
            origin = RunOrigin.TRIGGER,
        )
        triggerRepository.markFired(triggerId, nowMillis)
        Timber.tag(TAG).d("Trigger %s fired pipeline %s.", triggerId, decision.pipelineId)
        return TriggerFireOutcome.Fired(decision.pipelineId)
    }

    private companion object {
        const val TAG = "Trigger"
    }
}
