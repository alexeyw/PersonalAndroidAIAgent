package app.knotwork.android.domain.usecases

import app.knotwork.android.domain.constants.TimeAndIdConstants
import app.knotwork.android.domain.models.NetworkState
import app.knotwork.android.domain.models.PowerState
import app.knotwork.android.domain.models.Trigger
import app.knotwork.android.domain.models.TriggerCondition
import app.knotwork.android.domain.models.TriggerSkipReason
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
     * An event trigger's condition is no longer satisfied while the trigger is
     * still disarmed from a previous fire: re-arm it so the next transition into
     * the satisfied state fires again. Carries no run.
     */
    data object ReArm : TriggerFiringDecision

    /**
     * The trigger should not fire now and needs no state change.
     *
     * @property reason Why it was skipped (for logging / tests).
     */
    data class Skip(val reason: TriggerSkipReason) : TriggerFiringDecision
}

/**
 * Pure decision core: given a [Trigger] and a snapshot of the device state and
 * clock, decides whether it should fire now.
 *
 * This is the heart of the trigger feature and carries no Android, framework or
 * I/O dependency — it is exhaustively unit-testable. The background runtime
 * wakes a thin worker when a trigger's schedule fires (time conditions) or on a
 * 15-minute poll (event conditions); that worker calls [FireTriggerUseCase],
 * which delegates the actual yes/no to this evaluator.
 *
 * **Two firing models.**
 * - **Time** ([TriggerCondition.IntervalSchedule] / [TriggerCondition.DailySchedule])
 *   fire on the clock. The interval debounce window is **half the interval**, so
 *   normal WorkManager flex/jitter (a wake earlier than a full interval after the
 *   last fire) cannot drop a cycle — which an interval-equal-to-period window
 *   would. Daily fires once per calendar day.
 * - **Event** ([TriggerCondition.isEventTriggered]) fire on a device-state
 *   *edge*. The runtime polls the live [PowerState] / [NetworkState]
 *   unconstrained, and this evaluator fires only on the false → true transition,
 *   tracked by [Trigger.armed]: fire when satisfied & armed (caller disarms),
 *   [ReArm][TriggerFiringDecision.ReArm] when the condition drops while disarmed,
 *   skip otherwise. A sustained state therefore fires exactly once, not once per
 *   poll.
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
     *   [TriggerFiringDecision.ReArm] when an event trigger should be re-armed,
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

        return when (val condition = trigger.condition) {
            is TriggerCondition.IntervalSchedule -> evaluateInterval(condition, trigger.lastFiredAt, nowMillis, fire)
            is TriggerCondition.DailySchedule -> evaluateDaily(condition, trigger.lastFiredAt, nowMillis, zone, fire)
            TriggerCondition.Charging -> evaluateEvent(power.isCharging, trigger.armed, fire)
            is TriggerCondition.NetworkConnected -> {
                val satisfied = if (condition.ssids.isEmpty()) {
                    // No SSID scope: fire on any Wi-Fi (wifiOnly) or any network.
                    if (condition.wifiOnly) network.isWifiConnected else network.isConnected
                } else {
                    // SSID-scoped: require a Wi-Fi connection whose name matches one
                    // of the configured SSIDs. Matching is case-insensitive to agree
                    // with the editor's case-insensitive de-duplication — otherwise a
                    // "home" entry would silently never match a "Home" network. A
                    // null/unreadable SSID matches nothing (equals returns false).
                    network.isWifiConnected &&
                        condition.ssids.any { it.equals(network.wifiSsid, ignoreCase = true) }
                }
                evaluateEvent(satisfied, trigger.armed, fire)
            }
        }
    }

    /**
     * Interval decision: fire unless a fire happened within the last *half* of
     * the interval. The half-interval window absorbs WorkManager flex (a periodic
     * worker may wake anywhere in its period, so two real wakes can be far less
     * than one interval apart) without skipping a whole cycle.
     */
    private fun evaluateInterval(
        condition: TriggerCondition.IntervalSchedule,
        lastFired: Long?,
        nowMillis: Long,
        fire: TriggerFiringDecision.Fire,
    ): TriggerFiringDecision {
        val intervalMs =
            condition.intervalMinutes.coerceAtLeast(MIN_INTERVAL_MINUTES) * TimeAndIdConstants.MS_PER_MINUTE
        val debounceMs = intervalMs / DEBOUNCE_DIVISOR
        return if (withinDebounce(lastFired, nowMillis, debounceMs)) {
            TriggerFiringDecision.Skip(TriggerSkipReason.ALREADY_FIRED)
        } else {
            fire
        }
    }

    /**
     * Event decision driven by the [armed] edge latch: fire only on the false →
     * true transition into the satisfied state, re-arm when it drops.
     */
    private fun evaluateEvent(
        satisfied: Boolean,
        armed: Boolean,
        fire: TriggerFiringDecision.Fire,
    ): TriggerFiringDecision = when {
        satisfied && armed -> fire
        satisfied -> TriggerFiringDecision.Skip(TriggerSkipReason.ALREADY_FIRED)
        armed -> TriggerFiringDecision.Skip(TriggerSkipReason.CONDITION_NOT_MET)
        else -> TriggerFiringDecision.ReArm
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
        val scheduledToday = DailyTriggerTime.instantTodayMillis(condition.hour, condition.minute, nowMillis, zone)
        return when {
            nowMillis < scheduledToday -> TriggerFiringDecision.Skip(TriggerSkipReason.CONDITION_NOT_MET)
            lastFired != null && lastFired >= scheduledToday -> TriggerFiringDecision.Skip(
                TriggerSkipReason.ALREADY_FIRED,
            )
            else -> fire
        }
    }

    /**
     * Whether [nowMillis] falls inside the [windowMs] window after [lastFired].
     * A never-fired trigger (`lastFired == null`) is never debounced.
     */
    private fun withinDebounce(lastFired: Long?, nowMillis: Long, windowMs: Long): Boolean =
        lastFired != null && nowMillis - lastFired < windowMs

    private companion object {
        /** Floor applied to a stored interval so a 0/negative value can never collapse the debounce window. */
        const val MIN_INTERVAL_MINUTES: Long = 1L

        /** The interval debounce window is the interval divided by this (i.e. half the interval). */
        const val DEBOUNCE_DIVISOR: Long = 2L
    }
}
