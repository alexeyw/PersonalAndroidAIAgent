package app.knotwork.android.domain.usecases

import app.knotwork.android.domain.models.ChatSession
import app.knotwork.android.domain.models.RunOrigin
import app.knotwork.android.domain.models.Trigger
import app.knotwork.android.domain.repositories.ChatRepository
import app.knotwork.android.domain.repositories.NetworkStateRepository
import app.knotwork.android.domain.repositories.PipelineRepository
import app.knotwork.android.domain.repositories.PowerStateRepository
import app.knotwork.android.domain.repositories.TriggerRepository
import app.knotwork.android.domain.services.ScheduledTaskConstraints
import app.knotwork.android.domain.services.ScheduledTaskNotifier
import app.knotwork.android.domain.services.TaskScheduler
import timber.log.Timber
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Outcome of a single trigger-fire attempt, returned for logging and so the
 * worker can decide whether its watch should keep running.
 */
sealed interface TriggerFireOutcome {

    /**
     * Whether the background watch that produced this outcome should keep
     * running. `false` means the trigger no longer warrants a watch (gone,
     * disabled, unbound, or auto-disabled), so the worker cancels its own work.
     */
    val watchStillWarranted: Boolean

    /**
     * A background run of [pipelineId] was enqueued.
     *
     * @property pipelineId The pipeline that was scheduled.
     */
    data class Fired(val pipelineId: String) : TriggerFireOutcome {
        override val watchStillWarranted: Boolean get() = true
    }

    /** An event trigger's condition dropped, so the trigger was re-armed. */
    data object ReArmed : TriggerFireOutcome {
        override val watchStillWarranted: Boolean get() = true
    }

    /**
     * The trigger did not fire this time but its watch is still valid (condition
     * not yet met, or already fired this cycle).
     *
     * @property reason Why the evaluation skipped the trigger.
     */
    data class Skipped(val reason: TriggerSkipReason) : TriggerFireOutcome {
        override val watchStillWarranted: Boolean
            get() = reason == TriggerSkipReason.CONDITION_NOT_MET || reason == TriggerSkipReason.ALREADY_FIRED
    }

    /**
     * The trigger's bound pipeline no longer exists, so the trigger was
     * auto-disabled to stop it waking pointlessly.
     *
     * @property pipelineId The id of the now-missing pipeline.
     */
    data class Disabled(val pipelineId: String) : TriggerFireOutcome {
        override val watchStillWarranted: Boolean get() = false
    }

    /** No trigger row matched the id (deleted between wakeup and handling). */
    data object NotFound : TriggerFireOutcome {
        override val watchStillWarranted: Boolean get() = false
    }
}

/**
 * Handles a background wakeup for a single trigger: load it, decide via the pure
 * [EvaluateTriggerFiringUseCase], and act.
 *
 * On [TriggerFiringDecision.Fire] it does **not** run inference itself — it
 * delegates to the existing [TaskScheduler.scheduleOneTime] path so the run goes
 * through `AgentWorker` exactly like a scheduled task (foreground promotion,
 * completion notification and engine unload all reused), attributed to
 * [RunOrigin.TRIGGER].
 *
 * **Bound session.** Unlike a scheduled task — which inherits the chat session
 * it was scheduled from — a trigger has no originating conversation, so it
 * lazily owns one. On the first fire (or after the user deletes the previously
 * bound session) it mints a chat session named after the trigger, persists the
 * binding ([TriggerRepository.setSessionId]), and threads it to the scheduler,
 * so the results of recurring fires accumulate in one conversation instead of
 * spawning a fresh session each time. A "Trigger fired" notification deep-links
 * the user straight into that session.
 *
 * Safety behaviours enforced here:
 * - **Idempotency.** The fire-state writes ([TriggerRepository.markFired] and,
 *   for event triggers, the disarm) happen **before** the irreversible enqueue,
 *   so a process death or DB error on the worker retry path re-evaluates against
 *   the recorded state and cannot enqueue a duplicate run (the cost of the rare
 *   failure is a *missed* run, not a doubled one). Session resolution happens
 *   **before** those writes, so a failure to create the session re-evaluates and
 *   fires fresh rather than burning the cycle.
 * - **Edge firing.** Event conditions (charging / network) fire once per
 *   transition into the satisfied state; the disarm here plus the
 *   [TriggerFiringDecision.ReArm] path implement that latch.
 * - **Deleted bound pipeline.** Auto-disabled rather than firing into nothing.
 */
@Singleton
class FireTriggerUseCase @Inject constructor(
    private val triggerRepository: TriggerRepository,
    private val pipelineRepository: PipelineRepository,
    private val powerStateRepository: PowerStateRepository,
    private val networkStateRepository: NetworkStateRepository,
    private val evaluateTriggerFiring: EvaluateTriggerFiringUseCase,
    private val taskScheduler: TaskScheduler,
    private val chatRepository: ChatRepository,
    private val scheduledTaskNotifier: ScheduledTaskNotifier,
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

            TriggerFiringDecision.ReArm -> {
                triggerRepository.setArmed(triggerId, true)
                Timber.tag(TAG).d("Trigger %s re-armed.", triggerId)
                TriggerFireOutcome.ReArmed
            }

            is TriggerFiringDecision.Fire -> fire(trigger, decision, nowMillis)
        }
    }

    /**
     * Enqueues the bound pipeline run after a final existence check, recording
     * the fire/disarm state **before** the enqueue, or disables the trigger when
     * the bound pipeline has been deleted.
     */
    private suspend fun fire(
        trigger: Trigger,
        decision: TriggerFiringDecision.Fire,
        nowMillis: Long,
    ): TriggerFireOutcome {
        if (pipelineRepository.getPipelineById(decision.pipelineId) == null) {
            triggerRepository.setEnabled(trigger.id, false)
            Timber.tag(TAG).w("Trigger %s bound pipeline %s missing; auto-disabled.", trigger.id, decision.pipelineId)
            return TriggerFireOutcome.Disabled(decision.pipelineId)
        }

        // Resolve the bound session before any suppression write: a failure to
        // create it must re-evaluate and fire fresh, not burn the cycle.
        val sessionId = resolveBoundSession(trigger, decision, nowMillis)

        // Record the suppression state first: if the enqueue or process dies
        // after this point, the next wake re-evaluates against the recorded
        // state and will not double-fire.
        triggerRepository.markFired(trigger.id, nowMillis)
        if (trigger.condition.isEventTriggered) {
            triggerRepository.setArmed(trigger.id, false)
        }

        taskScheduler.scheduleOneTime(
            prompt = decision.prompt,
            delayMinutes = 0,
            sessionId = sessionId,
            constraints = ScheduledTaskConstraints(requiresBatteryNotLow = true),
            pipelineId = decision.pipelineId,
            origin = RunOrigin.TRIGGER,
        )
        scheduledTaskNotifier.notifyTriggerFired(sessionId, trigger.name)
        Timber.tag(TAG).d("Trigger %s fired pipeline %s into session %s.", trigger.id, decision.pipelineId, sessionId)
        return TriggerFireOutcome.Fired(decision.pipelineId)
    }

    /**
     * Returns the chat session the trigger's run should land in, reusing the
     * persisted binding when its session still exists, otherwise minting and
     * binding a fresh session named after the trigger.
     *
     * The new session is themed to the bound pipeline so an interactive
     * follow-up the user types into it reuses the same pipeline as the
     * automation. Rebinding on a deleted session means future fires accumulate
     * in the new session rather than re-creating one each time.
     */
    private suspend fun resolveBoundSession(
        trigger: Trigger,
        decision: TriggerFiringDecision.Fire,
        nowMillis: Long,
    ): String {
        val bound = trigger.sessionId
        if (bound != null && chatRepository.getSessionById(bound) != null) {
            return bound
        }
        val sessionId = UUID.randomUUID().toString()
        chatRepository.saveSession(
            ChatSession(
                id = sessionId,
                name = trigger.name,
                updatedAt = nowMillis,
                pipelineId = decision.pipelineId,
            ),
        )
        triggerRepository.setSessionId(trigger.id, sessionId)
        Timber.tag(TAG).d("Trigger %s bound to new session %s.", trigger.id, sessionId)
        return sessionId
    }

    private companion object {
        const val TAG = "Trigger"
    }
}
