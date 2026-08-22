package app.knotwork.android.domain.models

/**
 * Persisted discriminator of a [RunTerminationReason].
 *
 * Split from the reason itself because the two carriers need different things:
 * the run record needs a stable string it can round-trip through Room, while a
 * live run needs the numbers behind the decision (which limit, how much was
 * spent) so the presentation layer can render a sentence without re-reading
 * settings that may have changed since.
 *
 * **The constants are persisted** in `pipeline_runs.terminationReason` — never
 * rename one. Adding a value is free: an unknown string decodes back to `null`
 * (an unclassified termination), exactly as a row written before this column
 * existed does.
 */
enum class RunTerminationKind {
    /** The run tree exhausted its shared step ceiling. */
    STEP_CEILING,

    /** The run tree exhausted its shared token ceiling. */
    TOKEN_CEILING,

    /** A background human-in-the-loop gate went unanswered past its window. */
    HITL_WINDOW_EXPIRED,

    /** The stuck-detector ended a run that kept repeating itself. */
    NO_PROGRESS,

    /** The queue's watchdog ended a run that went silent for too long. */
    RUN_STALLED,

    /** The pipeline graph was edited between interruption and resume. */
    GRAPH_CHANGED,

    /** The owning process died mid-run; found by the start-up orphan sweep. */
    PROCESS_DIED,

    /** The user discarded the run from the chat surface. */
    DISCARDED_BY_USER,

    /**
     * A background answer arrived for a run that could no longer be resumed,
     * so the run was settled instead of being left un-actionable.
     */
    NOT_RESUMABLE,
}

/**
 * Why a run stopped before producing an answer — the single typed vocabulary
 * shared by the autonomous-run ceilings, the graph stuck-detector, the queue
 * watchdog, the resume guards and the start-up orphan sweep.
 *
 * Before this type existed the cause travelled as a free-form `errorMessage`
 * string, and two independent consumers had already resorted to comparing that
 * string by equality to recover it (`TriggerRunOutcomeMapper` and
 * `ParkedRunResumer`). A vocabulary recovered by string matching is a
 * vocabulary that breaks the moment the copy is edited — and editing that copy
 * is a documented, planned follow-up. So the cause is typed at the source and
 * the string becomes what it should always have been: a rendering.
 *
 * The reason is deliberately **not** a new [PipelineRunStatus]. A protective
 * stop is still a run that did not finish its work, so it stays
 * [PipelineRunStatus.FAILED]; what changes is that consumers can now tell *why*
 * without parsing prose, and can decide — as the trigger health badge does —
 * that a working safety limit is not a product failure.
 *
 * `null` (rather than a catch-all variant) is the representation for "not
 * classified": an ordinary node failure has no entry here, and neither do rows
 * written before the column existed.
 *
 * @property kind The persisted discriminator. See [RunTerminationKind].
 */
sealed interface RunTerminationReason {

    val kind: RunTerminationKind

    /**
     * The run tree spent its whole step ceiling.
     *
     * @property limit The hard ceiling in force for this run, in node
     *   executions across the whole run tree.
     * @property spent How many node executions the tree had actually performed
     *   when the ceiling bound — counted across every resume of the logical
     *   run, not just the last attempt.
     */
    data class StepCeiling(val limit: Int, val spent: Int) : RunTerminationReason {
        override val kind: RunTerminationKind = RunTerminationKind.STEP_CEILING
    }

    /**
     * The run tree spent its whole token ceiling.
     *
     * @property limit The hard ceiling in force for this run, in tokens.
     * @property spent How many tokens the tree had accounted for when the
     *   ceiling bound. May under-count — see `RunBudgetLedger` for exactly
     *   which inference is charged and which is not.
     */
    data class TokenCeiling(val limit: Int, val spent: Int) : RunTerminationReason {
        override val kind: RunTerminationKind = RunTerminationKind.TOKEN_CEILING
    }

    /** A background approval or clarification went unanswered past its window. */
    data object HitlWindowExpired : RunTerminationReason {
        override val kind: RunTerminationKind = RunTerminationKind.HITL_WINDOW_EXPIRED
    }

    /**
     * The run kept repeating itself without getting anywhere, and the graph
     * stuck-detector ended it.
     *
     * Carries no numbers on purpose. The detector's own evidence — which node,
     * which signal, how many repetitions — is a *diagnostic*, written to the
     * run console where the repetition is visible step by step, and the chat
     * copy for this kind sends the reader there rather than trying to
     * summarise a loop in a sentence. Persisting a signal name would also make
     * a second vocabulary out of what is deliberately one.
     */
    data object NoProgress : RunTerminationReason {
        override val kind: RunTerminationKind = RunTerminationKind.NO_PROGRESS
    }

    /**
     * The run went quiet and the queue's watchdog ended it.
     *
     * Distinct from [NoProgress], and the distinction is the whole point of
     * having both: this run emitted *nothing at all* for five minutes, which is
     * what a step waiting on an external tool that never answers looks like.
     * [NoProgress] is the opposite shape — a run emitting briskly and saying
     * the same thing every time. Collapsing them would tell a user whose MCP
     * server hung that the same work kept repeating, and would send them to
     * compare steps that never repeated. (The console is not empty for a stall:
     * its log ends on the step that started and never finished, which is the
     * one thing worth seeing. What it has no row for is that step's output,
     * because there never was one.)
     */
    data object RunStalled : RunTerminationReason {
        override val kind: RunTerminationKind = RunTerminationKind.RUN_STALLED
    }

    /** The pipeline graph changed between interruption and resume. */
    data object GraphChanged : RunTerminationReason {
        override val kind: RunTerminationKind = RunTerminationKind.GRAPH_CHANGED
    }

    /** The owning process died mid-run. */
    data object ProcessDied : RunTerminationReason {
        override val kind: RunTerminationKind = RunTerminationKind.PROCESS_DIED
    }

    /** The user discarded the run. */
    data object DiscardedByUser : RunTerminationReason {
        override val kind: RunTerminationKind = RunTerminationKind.DISCARDED_BY_USER
    }

    /**
     * The user answered a background gate, but the run behind it could no
     * longer be resumed, so it was settled rather than stranded.
     *
     * Distinct from [HitlWindowExpired] on purpose: the user *did* answer. The
     * journal records the gate as abandoned rather than timed out, which is
     * what it was before the cause became typed — collapsing the two would
     * rewrite the record of who failed to respond.
     */
    data object NotResumable : RunTerminationReason {
        override val kind: RunTerminationKind = RunTerminationKind.NOT_RESUMABLE
    }
}

/**
 * Terse, stable, **log-oriented** rendering of a termination reason.
 *
 * Deliberately not user copy. Two audiences read a stopped run and they need
 * different things:
 *
 *  - a **person** needs a sentence that explains what happened and what to do
 *    about it. That lives in the presentation layer, in one place, resolved
 *    from string resources — `RunTerminationCopy`;
 *  - the **run console and the persisted `pipeline_runs.errorMessage`** need a
 *    short, stable, greppable line that means the same thing in a bug report
 *    six months from now. That is this.
 *
 * Keeping the two apart is the point of the split. Before it, one hand-written
 * English sentence tried to be both, ended up persisted, quoted verbatim in the
 * user guide, and drifted from the code it described. A diagnostic string
 * cannot drift in the same way because it describes *values*, not behaviour:
 * there is no claim in `step-ceiling: 15/15` that can become false.
 *
 * @return A single line, lower-case, no trailing punctuation.
 */
fun RunTerminationReason.diagnostic(): String = when (this) {
    is RunTerminationReason.StepCeiling -> "step-ceiling: $spent/$limit steps"
    is RunTerminationReason.TokenCeiling -> "token-ceiling: $spent/$limit tokens"
    RunTerminationReason.HitlWindowExpired -> "hitl-window-expired"
    RunTerminationReason.NoProgress -> "no-progress"
    RunTerminationReason.RunStalled -> "run-stalled"
    RunTerminationReason.GraphChanged -> "graph-changed"
    RunTerminationReason.ProcessDied -> "process-died"
    RunTerminationReason.DiscardedByUser -> "discarded-by-user"
    RunTerminationReason.NotResumable -> "not-resumable"
}
