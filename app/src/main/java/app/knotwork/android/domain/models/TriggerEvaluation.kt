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
 * **The HITL dimension.** The terminal [outcome] alone cannot answer "did this
 * background run need me, and did I answer it?": a run that asked for approval
 * and got it settles as plain [TriggerRunOutcome.Success], indistinguishable from
 * one that never asked, while only the unanswered case is visible (as
 * [TriggerRunOutcome.HitlTimeout]). That asymmetry is a gap in the no-silent-skips
 * invariant on the very interaction the user is most likely to miss, so the row
 * also carries the run's HITL activity in [hitl].
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
 * @property hitl Human-in-the-loop activity of the enqueued run, or `null` when
 *   the run raised no HITL gate at all (the overwhelming majority of rows). See
 *   [TriggerHitlActivity].
 */
data class TriggerEvaluation(
    val id: String,
    val triggerId: String,
    val evaluatedAt: Long,
    val source: TriggerEvaluationSource,
    val verdict: TriggerEvaluationVerdict,
    val runId: String? = null,
    val outcome: TriggerRunOutcome? = null,
    val hitl: TriggerHitlActivity? = null,
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

/**
 * Human-in-the-loop activity of the background run a
 * [TriggerEvaluationVerdict.Fired] evaluation started — "did this run stop and
 * ask, did it have to wait in the shade for the answer, and what did the answer
 * turn out to be?".
 *
 * Recorded for **every** gate the run raises, not only for the ones that go
 * durable: the live in-process waiting phase is a full minute by default
 * (`SettingsDefaults.TOOL_CALL_TIMEOUT_MS_DEFAULT`), so an approval answered
 * promptly from the notification never parks at all. Recording only parks would
 * therefore leave the *fastest* — and most common — background approval
 * invisible, which is the exact blind spot this record closes.
 *
 * **Last-gate-wins, with a count.** A run may raise several gates (a tool call
 * per loop iteration, a clarification followed by an approval). Rather than a
 * separate `trigger_hitl_events` table — a DAO, a join and a retention pass of
 * its own for a multiplicity that is rare in practice — the journal row keeps the
 * **latest** gate's kind and resolution alongside [gateCount], so a collapsed
 * record can never quietly pass itself off as the whole story.
 *
 * @property gateCount How many HITL gates this run raised in total. Always `>= 1`
 *   (a run with no gate carries no [TriggerEvaluation.hitl] at all).
 * @property lastKind Which gate the latest one was — an approval or a
 *   clarification.
 * @property lastResolution How the latest gate ended, or
 *   [TriggerHitlResolution.PENDING] while it is still waiting.
 * @property parked Whether the latest gate outlived its live waiting phase and
 *   parked on a durable record — i.e. the answer had to come back from a
 *   notification rather than from a screen the user already had open. This is
 *   what makes "the background HITL deep-link works" provable from the journal.
 */
data class TriggerHitlActivity(
    val gateCount: Int,
    val lastKind: PendingInteractionKind,
    val lastResolution: TriggerHitlResolution,
    val parked: Boolean,
)

/**
 * How a single human-in-the-loop gate ended.
 *
 * The names are **persisted** on the journal row and exported in the diagnostic
 * dump, so they must never be renamed.
 */
enum class TriggerHitlResolution {
    /** The gate is still waiting for the user — live, or parked in the shade. */
    PENDING,

    /** The user approved the staged tool call. */
    APPROVED,

    /** The user denied the staged tool call. */
    DENIED,

    /** The user answered the clarifying question. */
    ANSWERED,

    /**
     * The parked gate's approval window elapsed with no answer and the run was
     * failed. The run's own outcome is [TriggerRunOutcome.HitlTimeout].
     */
    TIMED_OUT,

    /**
     * The gate ended without ever reaching the user: it could not be parked
     * durably (a non-persisted editor run, or a storage failure), or the park
     * was later discarded because the pipeline graph had changed underneath it.
     * Distinct from [TIMED_OUT] — nobody was ever given the chance to answer.
     */
    ABANDONED,
}

/**
 * One transition in the life of a HITL gate, as reported to the journal by the
 * component that owns the gate.
 *
 * Modelled as events rather than as a "write the whole activity" call because no
 * single call site knows the full picture: the executor that raises a gate does
 * not know whether it will park, and the resumer that settles an expired park
 * does not know how many gates preceded it. The journal folds the events onto the
 * row ([TriggerHitlActivity]), which keeps the counting in one place.
 */
sealed interface TriggerHitlEvent {

    /**
     * A gate was raised and the run began waiting on the user. Increments
     * [TriggerHitlActivity.gateCount] and resets the row's resolution to
     * [TriggerHitlResolution.PENDING].
     *
     * @property kind Which gate was raised.
     */
    data class Raised(val kind: PendingInteractionKind) : TriggerHitlEvent

    /**
     * The gate outlived its live waiting phase and was persisted as a
     * [PendingInteraction]: from here on the answer can only arrive from a
     * notification (or the reopened chat). Sets [TriggerHitlActivity.parked].
     */
    data object Parked : TriggerHitlEvent

    /**
     * The gate ended.
     *
     * @property resolution How it ended. Never [TriggerHitlResolution.PENDING] —
     *   that value exists to describe a gate that has *not* been resolved, so
     *   reporting it as a resolution is a programming error.
     */
    data class Resolved(val resolution: TriggerHitlResolution) : TriggerHitlEvent {
        init {
            require(resolution != TriggerHitlResolution.PENDING) {
                "PENDING is the absence of a resolution, not a resolution"
            }
        }
    }
}
