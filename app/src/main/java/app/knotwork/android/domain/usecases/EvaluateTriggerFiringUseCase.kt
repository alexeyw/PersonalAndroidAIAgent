package app.knotwork.android.domain.usecases

import app.knotwork.android.domain.constants.TimeAndIdConstants
import app.knotwork.android.domain.models.NetworkState
import app.knotwork.android.domain.models.PowerState
import app.knotwork.android.domain.models.Trigger
import app.knotwork.android.domain.models.TriggerCondition
import java.time.Instant
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Outcome of evaluating whether a [Trigger] should fire right now.
 */
sealed interface TriggerFiringDecision {

    /**
     * The trigger should fire: enqueue a background run of [pipelineId] with
     * [prompt].
     *
     * @property pipelineId The bound pipeline to run (guaranteed non-null here).
     * @property prompt The input message for the run.
     */
    data class Fire(val pipelineId: String, val prompt: String) : TriggerFiringDecision

    /**
     * The trigger should not fire now.
     *
     * @property reason Why it was skipped (for logging / tests).
     */
    data class Skip(val reason: TriggerSkipReason) : TriggerFiringDecision
}

/**
 * Why a trigger evaluation resulted in [TriggerFiringDecision.Skip].
 */
enum class TriggerSkipReason {
    /** The trigger is disabled. */
    DISABLED,

    /** The trigger has no bound pipeline (inert until bound). */
    UNBOUND,

    /** The condition is not currently satisfied (not charging, no network, or before the daily time). */
    CONDITION_NOT_MET,

    /** The condition is satisfied but the trigger fired too recently / already in this period. */
    DEBOUNCED,
}

/**
 * Pure decision core: given a [Trigger] and a snapshot of the device state and
 * clock, decides whether it should fire now.
 *
 * This is the heart of the trigger feature and carries no Android, framework or
 * I/O dependency — it is exhaustively unit-testable. The background runtime
 * wakes a thin worker when a trigger's coarse constraint (charging / network /
 * interval) is met; that worker calls
 * [app.knotwork.android.domain.usecases.FireTriggerUseCase], which delegates the
 * actual yes/no to this evaluator.
 *
 * **Edge-from-level via debounce.** The platform constraints are
 * *level-triggered* (they hold while charging, while connected, every interval),
 * not *edge-triggered* ("the moment charging starts"). To approximate edge
 * semantics without persisting extra device-state history, the evaluator
 * debounces on [Trigger.lastFiredAt]: an event trigger (charging / network)
 * fires at most once per [EVENT_REARM_WINDOW_MINUTES] window, an interval
 * trigger at most once per its own interval, and a daily trigger at most once
 * per calendar day. The bound exact "fire only on the connect edge" is a noted
 * future refinement (see `project_docs/decisions.md`).
 */
@Singleton
class EvaluateTriggerFiringUseCase @Inject constructor() {

    /**
     * Evaluates [trigger] against the current device state and clock.
     *
     * @param trigger The trigger to evaluate.
     * @param power Current power/charging snapshot (used by charging triggers).
     * @param network Current network snapshot (used by network triggers).
     * @param nowMillis Current wall-clock time, epoch-millis.
     * @param zone Device-local zone, used to resolve a daily trigger's
     *   time-of-day to an instant. Defaults to the system zone.
     * @return [TriggerFiringDecision.Fire] when the trigger should run now,
     *   otherwise [TriggerFiringDecision.Skip] with the reason.
     */
    operator fun invoke(
        trigger: Trigger,
        power: PowerState,
        network: NetworkState,
        nowMillis: Long,
        zone: ZoneId = ZoneId.systemDefault(),
    ): TriggerFiringDecision {
        if (!trigger.enabled) return TriggerFiringDecision.Skip(TriggerSkipReason.DISABLED)
        val pipelineId = trigger.pipelineId ?: return TriggerFiringDecision.Skip(TriggerSkipReason.UNBOUND)
        val fire = TriggerFiringDecision.Fire(pipelineId, trigger.prompt)
        val lastFired = trigger.lastFiredAt

        return when (val condition = trigger.condition) {
            is TriggerCondition.IntervalSchedule -> {
                val intervalMs = condition.intervalMinutes * TimeAndIdConstants.MS_PER_MINUTE
                if (withinDebounce(lastFired, nowMillis, intervalMs)) {
                    TriggerFiringDecision.Skip(TriggerSkipReason.DEBOUNCED)
                } else {
                    fire
                }
            }

            is TriggerCondition.DailySchedule -> evaluateDaily(condition, lastFired, nowMillis, zone, fire)

            TriggerCondition.Charging ->
                if (!power.isCharging) {
                    TriggerFiringDecision.Skip(TriggerSkipReason.CONDITION_NOT_MET)
                } else {
                    eventDecision(lastFired, nowMillis, fire)
                }

            is TriggerCondition.NetworkConnected -> {
                val satisfied = if (condition.wifiOnly) network.isWifiConnected else network.isConnected
                if (!satisfied) {
                    TriggerFiringDecision.Skip(TriggerSkipReason.CONDITION_NOT_MET)
                } else {
                    eventDecision(lastFired, nowMillis, fire)
                }
            }
        }
    }

    /**
     * Decision for an event trigger whose condition is already satisfied: fire
     * unless within the shared event re-arm window.
     */
    private fun eventDecision(
        lastFired: Long?,
        nowMillis: Long,
        fire: TriggerFiringDecision.Fire,
    ): TriggerFiringDecision {
        val windowMs = EVENT_REARM_WINDOW_MINUTES * TimeAndIdConstants.MS_PER_MINUTE
        return if (withinDebounce(lastFired, nowMillis, windowMs)) {
            TriggerFiringDecision.Skip(TriggerSkipReason.DEBOUNCED)
        } else {
            fire
        }
    }

    /**
     * Daily-schedule decision: fire once the device-local time has reached
     * today's [hour][TriggerCondition.DailySchedule.hour]:[minute][TriggerCondition.DailySchedule.minute]
     * and the trigger has not already fired during today's window.
     */
    private fun evaluateDaily(
        condition: TriggerCondition.DailySchedule,
        lastFired: Long?,
        nowMillis: Long,
        zone: ZoneId,
        fire: TriggerFiringDecision.Fire,
    ): TriggerFiringDecision {
        val scheduledToday = scheduledInstantToday(condition, nowMillis, zone)
        return when {
            nowMillis < scheduledToday -> TriggerFiringDecision.Skip(TriggerSkipReason.CONDITION_NOT_MET)
            lastFired != null && lastFired >= scheduledToday -> TriggerFiringDecision.Skip(TriggerSkipReason.DEBOUNCED)
            else -> fire
        }
    }

    /**
     * Resolves today's scheduled wall-clock instant for a daily condition, in
     * epoch-millis. Hour/minute are coerced into valid ranges so a corrupt
     * stored value can never throw a `DateTimeException`.
     */
    private fun scheduledInstantToday(condition: TriggerCondition.DailySchedule, nowMillis: Long, zone: ZoneId): Long {
        val today = Instant.ofEpochMilli(nowMillis).atZone(zone).toLocalDate()
        val hour = condition.hour.coerceIn(MIN_HOUR, MAX_HOUR)
        val minute = condition.minute.coerceIn(MIN_MINUTE, MAX_MINUTE)
        return today.atTime(hour, minute).atZone(zone).toInstant().toEpochMilli()
    }

    /**
     * Whether [nowMillis] falls inside the [windowMs] re-arm window after
     * [lastFired]. A never-fired trigger (`lastFired == null`) is never
     * debounced.
     */
    private fun withinDebounce(lastFired: Long?, nowMillis: Long, windowMs: Long): Boolean =
        lastFired != null && nowMillis - lastFired < windowMs

    private companion object {
        /**
         * Minimum spacing between two fires of the same event trigger
         * (charging / network). Set to the platform periodic-work floor so a
         * trigger fires at most once per background wake cycle, preventing a
         * still-satisfied constraint from enqueuing a run on every poll.
         */
        const val EVENT_REARM_WINDOW_MINUTES: Long = 15L

        const val MIN_HOUR: Int = 0
        const val MAX_HOUR: Int = 23
        const val MIN_MINUTE: Int = 0
        const val MAX_MINUTE: Int = 59
    }
}
