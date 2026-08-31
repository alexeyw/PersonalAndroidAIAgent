package app.knotwork.android.domain.models

/**
 * Mutable spend ledger shared by every run in one execution tree.
 *
 * Replaces the former `RunStepBudget`, which counted one thing (steps) and
 * counted it per *execution attempt*. That scope was the defect: a resumed run
 * re-seeded a fresh budget, and a resume is not only what happens after the
 * process dies — every answered background approval comes back through
 * `ResumePipelineRunUseCase`, and a run may park an unbounded number of times.
 * A nightly loop that raises one gate per iteration therefore received a full
 * fresh ceiling after every answer, which is to say the ceiling did not bind in
 * exactly the scenario it exists for.
 *
 * So the ledger counts the **logical run**, not the attempt. It is seeded from
 * the counters persisted on the root run record and is written back as the tree
 * executes, which makes two properties true that were not true before:
 * a resumed run continues the count where the previous attempt left off, and a
 * sub-pipeline at any nesting depth charges the same root.
 *
 * **Replayed nodes are not charged.** On resume the engine re-walks the
 * recorded prefix to rebuild control flow; those nodes were charged when they
 * actually ran. Charging them again would make a run that parks often die
 * *earlier* than one that never parks — the inverse of the defect this type
 * fixes. The engine therefore charges from the live branch only, alongside the
 * trace append and the metrics write, which are already replay-guarded.
 *
 * **What the token axis counts.** Only what a pipeline node reports as its
 * `NodeExecutionResult.tokenCount` — the CLOUD, LITE_RT and SKILL nodes. Two
 * classes of inference are deliberately *not* charged: the structured-output
 * gate's repair calls, and off-graph inference (memory extraction and
 * compaction, history compression, the search tool, task delegation). Neither
 * omission can hide a runaway: repairs are already hard-bounded per call by
 * `structuredOutputMaxRepairs`, and the number of calls that can happen at all
 * is bounded by the step ceiling in this same ledger. The consequence is that
 * the token count is a floor, so the ceiling binds late rather than early —
 * the only direction in which being wrong is safe.
 *
 * The holder is deliberately a small mutable object passed by reference: the
 * engine's walk is the only writer and the recursion into a sub-pipeline is
 * strictly synchronous within one coroutine (the parent suspends on the
 * child's flow), so no synchronisation is needed.
 *
 * **Raising a ceiling does not remove it.** When a run breaches, the user is
 * asked whether it may carry on, and a yes buys exactly one more portion of the
 * axis that bound — the same allowance again, which is what "continue for
 * another N steps" means to the person reading it. The grant is counted, not
 * flagged, so the next crossing asks again; a run that has been waved through
 * ten times is a run the user has said yes to ten times. The counts are seeded
 * from the root run record alongside the spend, because an extension that did
 * not survive the resume would leave the run breaching on its first node after
 * the very answer that was supposed to let it continue.
 *
 * @property ceilings The base limits in force for this run tree, resolved once
 *   from the root run's origin. What actually binds is [effectiveHard], which
 *   applies the extensions granted since.
 * @property rootRunId Id of the run at the root of the tree — the record whose
 *   columns hold the persisted counters. `null` for a non-persisted run (an
 *   editor test run), in which case the ledger is purely in-memory and nothing
 *   survives the invocation.
 */
class RunBudgetLedger(
    val ceilings: RunCeilings,
    val rootRunId: String? = null,
    stepsAlreadySpent: Int = 0,
    tokensAlreadySpent: Int = 0,
    stepCeilingExtensions: Int = 0,
    tokenCeilingExtensions: Int = 0,
) {

    /** Node executions charged to this tree, across every attempt of the run. */
    var stepsSpent: Int = stepsAlreadySpent
        private set

    /** Tokens charged to this tree, across every attempt of the run. */
    var tokensSpent: Int = tokensAlreadySpent
        private set

    /**
     * Extra portions of the step ceiling the user has granted this tree. Each
     * one multiplies the base ceiling by one further whole allowance — see
     * [effectiveHard].
     */
    var stepCeilingExtensions: Int = stepCeilingExtensions
        private set

    /** Extra portions of the token ceiling the user has granted this tree. */
    var tokenCeilingExtensions: Int = tokenCeilingExtensions
        private set

    /**
     * `true` once any charged node reported an estimated rather than a
     * provider-reported token count — today that means a cloud provider whose
     * streaming response carries no usage record, or local inference, whose
     * unit is a stream chunk rather than a model token. Surfaced so the product
     * can qualify the number instead of implying a precision it does not have.
     */
    var tokensApproximate: Boolean = false
        private set

    private val softBreachesAnnounced: MutableSet<RunCeilingAxis> = mutableSetOf()

    /**
     * Whether a further node may execute, and why not when it may not.
     *
     * Checked *before* charging so a refused node is never counted: the run
     * that stops at the ceiling has spent exactly the ceiling, not one more.
     *
     * @return The reason the run must stop, or `null` when it may continue.
     */
    fun hardBreach(): RunTerminationReason? = when {
        stepsSpent >= effectiveHard(RunCeilingAxis.STEPS) ->
            RunTerminationReason.StepCeiling(limit = effectiveHard(RunCeilingAxis.STEPS), spent = stepsSpent)

        tokensSpent >= effectiveHard(RunCeilingAxis.TOKENS) ->
            RunTerminationReason.TokenCeiling(limit = effectiveHard(RunCeilingAxis.TOKENS), spent = tokensSpent)

        else -> null
    }

    /**
     * The limit actually in force on [axis]: the configured ceiling plus one
     * whole allowance for every extension the user has granted this tree.
     *
     * Computed in `Long` and clamped rather than multiplied in `Int`. Nothing
     * bounds how many times a user may answer "continue", and an overflow here
     * would not merely report a wrong number — it would wrap negative and turn
     * the ceiling into a limit the run is already past, killing the run on the
     * step *after* the one the user just paid for.
     *
     * @param axis The axis to look up. [RunCeilingAxis.MONEY] is unmeasured in
     *   this release and answers [Int.MAX_VALUE], never a number that could read
     *   as a limit it does not enforce.
     * @return The number this ledger stops the run at.
     */
    fun effectiveHard(axis: RunCeilingAxis): Int = when (axis) {
        RunCeilingAxis.STEPS -> extend(ceilings.steps.hard, stepCeilingExtensions)
        RunCeilingAxis.TOKENS -> extend(ceilings.tokens.hard, tokenCeilingExtensions)
        RunCeilingAxis.MONEY -> Int.MAX_VALUE
    }

    /**
     * Grants one more portion of [axis] to this tree.
     *
     * In-memory only: the durable count lives on the root run record and is
     * written by whoever recorded the user's answer, because the answer may well
     * arrive in a different process from the one that asked. This call exists so
     * a ledger built *before* the grant — a non-persisted editor run, or the
     * live tree in the process that is about to resume — agrees with the record.
     *
     * Also clears the axis's soft-warning claim, so the run warns again three
     * quarters of the way through the allowance it was just given. Without that
     * the extra portion would be spent with no notice at all: the claim set
     * remembers that this axis already warned, and the threshold it warned at is
     * now far behind.
     *
     * @param axis The axis the user granted one more portion of.
     */
    fun grantExtension(axis: RunCeilingAxis) {
        when (axis) {
            RunCeilingAxis.STEPS -> stepCeilingExtensions++
            RunCeilingAxis.TOKENS -> tokenCeilingExtensions++
            // Unmeasured: there is no ceiling to raise, so a grant would be a
            // record of a decision that changed nothing. Listed rather than
            // defaulted so promoting the money axis cannot silently skip it.
            RunCeilingAxis.MONEY -> return
        }
        softBreachesAnnounced -= axis
    }

    /**
     * Charges one node execution to the tree.
     *
     * Called from the live branch of the walk only; see the class
     * documentation on why replayed nodes are not charged.
     */
    fun chargeStep() {
        stepsSpent++
    }

    /**
     * Charges a node's token usage to the tree.
     *
     * @param tokens The node's reported usage. Non-positive values are ignored
     *   so a node that reports nothing cannot corrupt the count.
     * @param approximate `true` when the number is the app's own estimate
     *   rather than a figure the provider reported.
     */
    fun chargeTokens(tokens: Int?, approximate: Boolean) {
        if (tokens == null || tokens <= 0) return
        tokensSpent += tokens
        if (approximate) tokensApproximate = true
    }

    /**
     * Claims the next soft-threshold crossing that has not been announced yet.
     *
     * Claiming is destructive by design: a soft limit is a warning, and a
     * warning repeated on every node for the rest of the run is noise that
     * teaches the user to ignore it. Each axis warns once per *attempt*: the
     * claim set lives on this object, and a resumed run builds a fresh ledger
     * from the persisted counters alone. A run that parks repeatedly past its
     * soft threshold therefore warns again on each resume — which is a real
     * inconsistency with the stuck-detector beside it, whose escalation *is*
     * carried across a resume. Left as it is rather than changed here: making
     * it once-per-tree means persisting the claim, which is a schema change
     * this task did not open, and the failure mode is a repeated advisory
     * rather than a wrong one.
     *
     * @return The crossing, carrying everything a warning needs to name itself,
     *   or `null` when nothing new crossed. Returning the numbers rather than
     *   only the axis keeps the caller from having to ask for a spend or a
     *   limit that could come back null for an axis this ledger does not
     *   measure.
     */
    fun claimSoftBreach(): SoftCeilingBreach? {
        val breach = when {
            RunCeilingAxis.STEPS !in softBreachesAnnounced && stepsSpent >= softThreshold(RunCeilingAxis.STEPS) ->
                SoftCeilingBreach(
                    RunCeilingAxis.STEPS,
                    spent = stepsSpent,
                    hardLimit = effectiveHard(RunCeilingAxis.STEPS),
                )

            RunCeilingAxis.TOKENS !in softBreachesAnnounced && tokensSpent >= softThreshold(RunCeilingAxis.TOKENS) ->
                SoftCeilingBreach(
                    RunCeilingAxis.TOKENS,
                    spent = tokensSpent,
                    hardLimit = effectiveHard(RunCeilingAxis.TOKENS),
                )

            else -> null
        } ?: return null
        softBreachesAnnounced += breach.axis
        return breach
    }

    /**
     * The soft threshold of [axis] against the ceiling actually in force.
     *
     * Derived from [effectiveHard] rather than read off [ceilings], because a
     * granted extension moves the hard limit and a warning still anchored to the
     * original would fire the moment the run resumed — the spend is already past
     * it — telling a user who has just raised the ceiling that they are
     * approaching it.
     */
    private fun softThreshold(axis: RunCeilingAxis): Int = RunCeilings.softFor(effectiveHard(axis))

    private companion object {
        /**
         * One base ceiling raised by [extensions] whole portions, clamped to
         * [Int.MAX_VALUE].
         *
         * @param base The configured hard ceiling.
         * @param extensions How many further allowances the user granted.
         * @return The limit in force, never negative and never wrapped.
         */
        fun extend(base: Int, extensions: Int): Int =
            (base.toLong() * (1L + extensions)).coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
    }
}

/**
 * One axis crossing its soft threshold, with the numbers a warning needs.
 *
 * @property axis Which ceiling is being approached.
 * @property spent How much the run tree had charged when the threshold was
 *   crossed.
 * @property hardLimit The value at which the run will actually be stopped.
 */
data class SoftCeilingBreach(val axis: RunCeilingAxis, val spent: Int, val hardLimit: Int)
