package app.knotwork.android.domain.usecases

import app.knotwork.android.domain.models.Trigger
import app.knotwork.android.domain.models.TriggerCondition
import app.knotwork.android.domain.models.TriggerHealthInputs
import app.knotwork.android.domain.models.TriggerHealthStatus
import app.knotwork.android.domain.models.TriggerRunOutcome
import javax.inject.Inject
import kotlin.math.max

/**
 * Pure derivation of a [Trigger]'s [TriggerHealthStatus] from its journal-derived
 * [TriggerHealthInputs] and the current wall-clock time.
 *
 * Stateless and side-effect-free — it reads no clock and touches no storage of
 * its own; the caller supplies `nowMillis` — so it is fully deterministic and
 * unit-testable. The list ViewModel calls it once per row on every emission of
 * the trigger list, the health-inputs map, or a clock tick.
 *
 * **Precedence.** A trigger can be simultaneously overdue *and* have a failed
 * last run; the badge shows exactly one state, so the order is fixed:
 * 1. [TriggerHealthStatus.STALE] — "not being checked at all" supersedes a past
 *    run result, because a stale trigger is not producing new runs to judge.
 * 2. [TriggerHealthStatus.ERRORED] — the background is alive but the most recent
 *    fired run failed.
 * 3. [TriggerHealthStatus.HEALTHY] — evaluated on cadence and the last run (if
 *    any) succeeded.
 *
 * **Inactive triggers have no health.** A trigger that is unbound
 * ([Trigger.pipelineId] `== null`) *or* disabled is not registered with the
 * background runtime and is never evaluated, so it has no meaningful health
 * signal and this returns `null` for it (the row shows no badge — a deliberately
 * off trigger must not read as "overdue").
 */
class TriggerHealthEvaluator @Inject constructor() {

    /**
     * Derives the health badge state for [trigger].
     *
     * @param trigger The trigger to classify.
     * @param inputs The trigger's collapsed journal facts, or `null` when the
     *   trigger has no journal rows at all (never evaluated yet, or every row aged
     *   out by retention).
     * @param nowMillis Current wall-clock time, epoch-millis.
     * @return The health state, or `null` when [trigger] is inactive (unbound or
     *   disabled) and therefore carries no health signal.
     */
    fun evaluate(trigger: Trigger, inputs: TriggerHealthInputs?, nowMillis: Long): TriggerHealthStatus? {
        // Inactive triggers (unbound or disabled) are never registered with the
        // background runtime, so they are never evaluated and have no health to show.
        if (!trigger.isActive) return null

        // No journal history: staleness is deliberately NOT inferred here. A trigger
        // reaches this state both when it was just made active (freshly bound or
        // re-enabled, its first evaluation still pending — [Trigger.createdAt] is
        // NOT the activation moment, so it cannot tell the two apart) and when it
        // has genuinely never been polled. A false "Overdue" the instant a trigger
        // goes active is the more damaging error — it erodes the badge's meaning —
        // so a trigger with no evaluations reads HEALTHY; the detail screen's empty
        // journal ("not checked yet") carries the never-evaluated state instead.
        // Real staleness applies as soon as the first evaluation lands.
        //
        // Known limitation: a trigger re-enabled after a long disable keeps an old
        // [TriggerHealthInputs.latestEvaluatedAt] and so may read STALE until its
        // next scheduled poll writes a fresh row (bounded by the trigger's own
        // interval — at most ~15 min for event/short-interval triggers). Closing
        // that fully needs a persisted activation timestamp (a schema change).
        if (inputs == null) return TriggerHealthStatus.HEALTHY

        val staleAfterMillis = staleThresholdMillis(trigger.condition)
        val sinceLastEvaluation = nowMillis - inputs.latestEvaluatedAt
        if (sinceLastEvaluation > staleAfterMillis) return TriggerHealthStatus.STALE

        return if (inputs.latestFiredOutcome.isError()) {
            TriggerHealthStatus.ERRORED
        } else {
            TriggerHealthStatus.HEALTHY
        }
    }

    /**
     * The maximum silence, in millis, tolerated before a trigger is considered
     * overdue: its expected evaluation cadence multiplied by [STALE_GRACE_FACTOR].
     * The grace factor absorbs WorkManager's flex window and ordinary Doze
     * maintenance gaps so normal background behaviour is not mistaken for the
     * platform starving the poll.
     */
    private fun staleThresholdMillis(condition: TriggerCondition): Long =
        expectedCadenceMinutes(condition) * STALE_GRACE_FACTOR * MILLIS_PER_MINUTE

    /**
     * The cadence, in minutes, at which the background runtime is expected to
     * evaluate a trigger of this [condition]. Mirrors the periods chosen by the
     * data-layer scheduler:
     * - an interval schedule polls on its own interval, floored at the platform
     *   periodic-work minimum ([MIN_BACKGROUND_CADENCE_MINUTES]),
     * - a daily schedule is evaluated once per day,
     * - event conditions (charging / network) are re-checked by the periodic
     *   watch at the platform floor.
     */
    private fun expectedCadenceMinutes(condition: TriggerCondition): Long = when (condition) {
        is TriggerCondition.IntervalSchedule ->
            max(condition.intervalMinutes, MIN_BACKGROUND_CADENCE_MINUTES)
        is TriggerCondition.DailySchedule -> MINUTES_PER_DAY
        TriggerCondition.Charging, is TriggerCondition.NetworkConnected -> MIN_BACKGROUND_CADENCE_MINUTES
    }

    /** Whether a settled outcome represents a run that did not complete cleanly. */
    private fun TriggerRunOutcome?.isError(): Boolean = when (this) {
        null, TriggerRunOutcome.Success -> false
        is TriggerRunOutcome.Failure,
        TriggerRunOutcome.CancelledBySystem,
        TriggerRunOutcome.HitlTimeout,
        TriggerRunOutcome.Cancelled,
        -> true
    }

    /** Tunable health thresholds shared with the health documentation and tests. */
    companion object {
        /**
         * Multiplier applied to a trigger's expected evaluation cadence to obtain
         * the "overdue" threshold. The single product-tunable knob of the health
         * signal: at `2` a trigger reads as overdue only after missing two full
         * expected cadences, which tolerates ordinary Doze flex while still
         * catching a genuinely starved poll.
         */
        const val STALE_GRACE_FACTOR: Long = 2L

        /**
         * The floor expected cadence, in minutes, mirroring the platform periodic
         * WorkManager minimum the scheduler clamps to. Event and short-interval
         * triggers can never be evaluated more often than this.
         */
        const val MIN_BACKGROUND_CADENCE_MINUTES: Long = 15L

        private const val MINUTES_PER_DAY: Long = 24L * 60L
        private const val MILLIS_PER_MINUTE: Long = 60_000L
    }
}
