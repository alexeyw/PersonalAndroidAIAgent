package app.knotwork.android.domain.models

/**
 * One persisted record in the **trigger-evaluation journal** — the durable
 * answer to "what did this trigger decide at this moment, and what became of the
 * run it started?".
 *
 * The journal exists because a background trigger that "just didn't fire" is
 * otherwise indistinguishable across very different causes: the poll never ran
 * (Doze / an OEM background killer), it ran but the evaluator returned
 * [TriggerEvaluationVerdict.Skipped], or it fired and the run later failed or was
 * cancelled by the system. One row is written per evaluated
 * *(trigger × moment)*, so every non-fire is explainable after the fact instead
 * of being a mystery.
 *
 * **Invariant — no silent skips.** Every consumer that evaluates a trigger MUST
 * write exactly one [TriggerEvaluation] recording its [verdict], atomically with
 * the decision it describes. A trigger evaluated without a journal row is a
 * diagnostic blind spot and defeats the journal's entire purpose. Recording is a
 * pure observer: a failure to write the journal must never alter or abort the
 * evaluation or run it observes (it is surfaced as its own error, not swallowed).
 *
 * **Two-phase lifecycle.** For a [TriggerEvaluationVerdict.Fired] verdict the row
 * is written first with [runId] set and [outcome] `null` (the enqueue has
 * happened but the run has not finished); the same row is later completed by
 * writing the terminal [outcome] once the background run settles. Non-firing
 * verdicts ([Skipped][TriggerEvaluationVerdict.Skipped] /
 * [ReArmed][TriggerEvaluationVerdict.ReArmed]) carry neither a [runId] nor an
 * [outcome] and are terminal on write.
 *
 * @property id Stable unique identifier of this journal record (UUID). Distinct
 *   from [triggerId]: one trigger accrues many evaluation rows over time.
 * @property triggerId Id of the [Trigger] that was evaluated.
 * @property evaluatedAt Epoch-millis of the evaluation moment.
 * @property source Where the evaluation originated (periodic poll, a device-state
 *   event, or the charging sweep) — so a gap in one source is distinguishable
 *   from a gap in another.
 * @property verdict The decision reached: fired, re-armed, or skipped with a
 *   typed reason.
 * @property runId Id of the enqueued background run when [verdict] is
 *   [TriggerEvaluationVerdict.Fired]; `null` for every other verdict. Used to
 *   attribute the eventual run [outcome] back to this row.
 * @property outcome Terminal fate of the enqueued run, or `null` when the run has
 *   not settled yet (or the verdict was not [TriggerEvaluationVerdict.Fired] and
 *   so started no run).
 */
data class TriggerEvaluation(
    val id: String,
    val triggerId: String,
    val evaluatedAt: Long,
    val source: TriggerEvaluationSource,
    val verdict: TriggerEvaluationVerdict,
    val runId: String? = null,
    val outcome: TriggerRunOutcome? = null,
)

/**
 * Where a trigger evaluation was initiated from.
 *
 * The names are **persisted** on the journal row, so they must never be renamed.
 * Kept coarse — the mechanism, not its parameters — so distinguishing a poll gap
 * (Doze / OEM killer) from an event-listener gap needs only this discriminator.
 */
enum class TriggerEvaluationSource {
    /**
     * The periodic poll worker (`TriggerWatchWorker`) — the heartbeat that wakes
     * on a cadence and evaluates time-scheduled and event triggers. A missing run
     * of this source is itself the primary "the platform starved the poll" signal.
     */
    POLL,

    /**
     * A device-state event listener firing an event trigger directly (charging /
     * connectivity edge) outside the poll cadence.
     */
    EVENT,

    /**
     * The charging sweep worker (`ChargingTriggerSweepWorker`) that re-checks
     * charging triggers around a charging constraint, distinct from the general
     * poll so its re-arm window is separately observable.
     */
    CHARGING_SWEEP,
}

/**
 * The decision a single trigger evaluation reached — the persisted, run-free
 * counterpart of
 * [TriggerFiringDecision][app.knotwork.android.domain.usecases.TriggerFiringDecision].
 *
 * Unlike the live decision it deliberately does **not** carry the bound pipeline
 * id or prompt: those are attributes of the [Trigger], not of the diagnostic
 * record, and a prompt can be mildly sensitive. The enqueued run is referenced by
 * [TriggerEvaluation.runId] instead.
 */
sealed interface TriggerEvaluationVerdict {

    /** The condition was satisfied and a background run was enqueued. */
    data object Fired : TriggerEvaluationVerdict

    /**
     * An event trigger's condition dropped while the trigger was still disarmed,
     * so its edge latch was re-armed. No run was started.
     */
    data object ReArmed : TriggerEvaluationVerdict

    /**
     * The trigger was evaluated but did not fire.
     *
     * @property reason The typed cause, so a skip reads as "condition X was not
     *   met" rather than a bare "nothing happened".
     */
    data class Skipped(val reason: TriggerSkipReason) : TriggerEvaluationVerdict
}

/**
 * Terminal fate of a background run started by a [TriggerEvaluationVerdict.Fired]
 * evaluation, recorded back onto the originating [TriggerEvaluation] row.
 *
 * The three non-success variants are deliberately kept apart because they answer
 * three different diagnostic questions, and conflating them would hide exactly
 * the background-reliability signal the journal exists to expose:
 * - [Failure] — the run failed on its own (a product defect to investigate).
 * - [CancelledBySystem] — the **platform** stopped the run without the user
 *   asking: the hosting process was killed (OEM background killer / OOM) and the
 *   run was reaped as interrupted. This is the primary "the platform is
 *   unreliable" signal and must never be mistaken for a failure **or** for a
 *   deliberate stop.
 * - [Cancelled] — the run was **deliberately** stopped in-process (the user
 *   pressed Stop, or the hosting service was torn down) before finishing. Not a
 *   reliability defect, and specifically not blamed on the platform.
 *
 * The discriminator used to persist each variant must never be renamed.
 */
sealed interface TriggerRunOutcome {

    /** The run completed successfully. */
    data object Success : TriggerRunOutcome

    /**
     * The run failed with an error.
     *
     * @property error A short, human-readable failure description (never raw
     *   stack-trace noise), suitable for showing in the trigger journal UI.
     */
    data class Failure(val error: String) : TriggerRunOutcome

    /**
     * The **platform** stopped the run without the user asking: the hosting
     * process died mid-run (an OEM background killer or the OS reclaiming memory)
     * and the run was reaped as interrupted at the next startup sweep. This is a
     * platform outcome, not a product defect, and is the background-reliability
     * signal the journal is built to surface — kept distinct from both [Failure]
     * and the deliberate [Cancelled].
     */
    data object CancelledBySystem : TriggerRunOutcome

    /**
     * The run was **deliberately** cancelled in-process before reaching a
     * terminal state of its own — the user pressed Stop on the run, or the
     * hosting foreground service was torn down. Unlike [CancelledBySystem] this
     * is an intended stop, not a platform kill, so it is not a reliability defect.
     */
    data object Cancelled : TriggerRunOutcome

    /**
     * The run parked on a background human-in-the-loop interaction (approval /
     * clarification) that timed out without a user response.
     */
    data object HitlTimeout : TriggerRunOutcome
}
