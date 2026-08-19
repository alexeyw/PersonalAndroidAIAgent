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
 * @property ceilings The limits in force for this run tree, resolved once from
 *   the root run's origin.
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
) {

    /** Node executions charged to this tree, across every attempt of the run. */
    var stepsSpent: Int = stepsAlreadySpent
        private set

    /** Tokens charged to this tree, across every attempt of the run. */
    var tokensSpent: Int = tokensAlreadySpent
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
        stepsSpent >= ceilings.steps.hard ->
            RunTerminationReason.StepCeiling(limit = ceilings.steps.hard, spent = stepsSpent)

        tokensSpent >= ceilings.tokens.hard ->
            RunTerminationReason.TokenCeiling(limit = ceilings.tokens.hard, spent = tokensSpent)

        else -> null
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
     * teaches the user to ignore it. Each axis warns once per run tree.
     *
     * @return The crossing, carrying everything a warning needs to name itself,
     *   or `null` when nothing new crossed. Returning the numbers rather than
     *   only the axis keeps the caller from having to ask for a spend or a
     *   limit that could come back null for an axis this ledger does not
     *   measure.
     */
    fun claimSoftBreach(): SoftCeilingBreach? {
        val breach = when {
            RunCeilingAxis.STEPS !in softBreachesAnnounced && stepsSpent >= ceilings.steps.soft ->
                SoftCeilingBreach(RunCeilingAxis.STEPS, spent = stepsSpent, hardLimit = ceilings.steps.hard)

            RunCeilingAxis.TOKENS !in softBreachesAnnounced && tokensSpent >= ceilings.tokens.soft ->
                SoftCeilingBreach(RunCeilingAxis.TOKENS, spent = tokensSpent, hardLimit = ceilings.tokens.hard)

            else -> null
        } ?: return null
        softBreachesAnnounced += breach.axis
        return breach
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
