package app.knotwork.android.domain.engine.stuck

import java.security.MessageDigest

/**
 * Sliding-window detector for a run that has stopped getting anywhere.
 *
 * ## Why this exists beside the ceilings
 *
 * The run ceilings (`RunBudgetLedger`) bound what a run may *spend*. They stop
 * a loop, eventually, by starving it — after it has burnt the whole allowance,
 * and without ever saying that a loop is what it was. For a background run
 * firing at 3am against somebody's own cloud key, "eventually" is a flat
 * battery or an invoice, and "without saying why" is a user who reads
 * *Stopped by a safety limit* and raises the limit.
 *
 * This detector bounds what a run may *repeat*. The two are complementary and
 * deliberately not ranked against each other: expenditure is the ceiling's
 * job, repetition is this one's. A loop that says something new every
 * iteration is not stuck — it is expensive, and the ceiling is the right
 * mechanism for it.
 *
 * ## What can actually loop
 *
 * Less than the phrase "infinite loop" suggests. `PipelineGraph.isValidDAG()`
 * drops every edge whose target is a `QUEUE_PROCESSOR` and then rejects any
 * remaining cycle, and the engine refuses to run an invalid graph — so an
 * `EVALUATION` retry edge back to an earlier node, or an `IF_CONDITION` loop,
 * cannot be saved or run at all. Sub-pipeline recursion is bounded separately
 * by the nesting-depth limit. What is left is the queue back-edge: a queue
 * whose item branch re-seeds a queue, or a graph with two queue nodes in a
 * cycle — a graph doing the same work on the same input, over and over.
 *
 * That is what this detector is tuned for, and it is why the rules below are
 * written the way they are: **a draining queue must never trip it**, however
 * repetitive its answers, because a queue is being handed a different item
 * every time. A run that keeps answering identically while still being asked
 * something new is not stuck — it is working through a list, and it is the
 * ceilings' business if that list is too long.
 *
 * ## Progress, defined operationally
 *
 * A checkpoint makes progress iff it produced an output fingerprint never seen
 * before in this run *and* the node did more than forward its input. Anything
 * else — a repeat, or a pass-through — increments [stepsSinceProgress]. That
 * counter is the measured quantity the whole detector rests on, and it is
 * derived only from values the engine already writes to the run trace at every
 * checkpoint, so a person can always open the console and see the same
 * evidence the detector saw.
 *
 * The rule is deliberately generous in one direction: a fingerprint first seen
 * on a pass-through does not enter the ledger, so a later node that genuinely
 * produces that same text still counts as progress. Being wrong that way costs
 * a late stop; being wrong the other way kills a healthy run.
 *
 * ## Bounded by construction
 *
 * The window holds at most [windowSize] observations, and the two novelty
 * ledgers — outputs produced, inputs seen — hold at most one fingerprint per
 * observation each. Observations are bounded by the step ceiling in the same
 * tree (100 at its maximum), and a fingerprint is a fixed 64 characters however
 * long the text behind it was. Nothing here grows with the length of a run, or
 * with the size of the answers a run is generating.
 *
 * The detector is a small mutable object shared by one execution tree and
 * passed by reference, exactly like `RunBudgetLedger`: the engine's walk is the
 * only writer, and recursion into a sub-pipeline is strictly synchronous within
 * one coroutine, so no synchronisation is needed. Sharing it is what makes a
 * parent calling one child five times with the same input *one* loop rather
 * than five unrelated runs.
 *
 * @property windowSize How many recent observations are compared. Default
 *   [WINDOW_SIZE].
 * @property repeatThreshold How many identical steps inside the window count as
 *   a repetition. Default [REPEAT_THRESHOLD].
 * @property staleStreak How many consecutive checkpoints without progress count
 *   as a stall. Default [STALE_STREAK].
 * @property graceSteps How many further steps the run is given to break the
 *   pattern after being nudged. Default [GRACE_STEPS].
 */
class GraphStuckDetector(
    private val windowSize: Int = WINDOW_SIZE,
    private val repeatThreshold: Int = REPEAT_THRESHOLD,
    private val staleStreak: Int = STALE_STREAK,
    private val graceSteps: Int = GRACE_STEPS,
) {

    init {
        // A non-positive window silently disables REPEATED_STEP entirely (the
        // deque is emptied on every record, so `signal` short-circuits on an
        // absent last step) and a non-positive threshold fires it on the first
        // step. Both are the detector *appearing* to work while doing the
        // opposite, which is exactly the failure a test tightening a fixture
        // would read as a passing repetition scenario.
        require(windowSize > 0) { "windowSize must be positive, was $windowSize" }
        require(repeatThreshold > 1) { "repeatThreshold must exceed 1, was $repeatThreshold" }
        require(staleStreak > 0) { "staleStreak must be positive, was $staleStreak" }
        require(graceSteps > 0) { "graceSteps must be positive, was $graceSteps" }
    }

    private val window: ArrayDeque<RunStepObservation> = ArrayDeque()

    /** Every output fingerprint a producing step has emitted in this run. */
    private val producedOutputs: MutableSet<String> = mutableSetOf()

    /** Every input fingerprint a step has been executed on in this run. */
    private val seenInputs: MutableSet<String> = mutableSetOf()

    /** Checkpoints since the last one that produced something new. */
    var stepsSinceProgress: Int = 0
        private set

    /** Checkpoints since the last one that was asked something new. */
    private var stepsSinceNewInput: Int = 0

    /** Observations recorded so far, used only to measure the grace period. */
    private var observed: Int = 0

    /** [observed] at the moment the run was nudged, or `null` while it has not been. */
    private var nudgedAt: Int? = null

    /** Set once a [StuckVerdict.Stop] has been returned, so it is returned only once. */
    private var stopped: Boolean = false

    /**
     * Records a step that actually executed and says what to do about it.
     *
     * **A run is warned once, and stays armed once warned.** If it breaks the
     * pattern, recovers, and relapses fifty steps later, it is stopped without
     * a second warning. That asymmetry is deliberate: the alternative — rearming
     * the whole escalation on every recovery — is what a loop that alternates
     * three repetitions with one productive step would exploit forever, which
     * is precisely the shape of a queue re-seeding itself. A run gets one
     * chance to take the advice, not one chance per episode.
     *
     * @param step The step, already fingerprinted.
     * @return [StuckVerdict.Healthy] while the run is getting somewhere,
     *   [StuckVerdict.Nudge] the first time it looks stuck, and
     *   [StuckVerdict.Stop] when the nudge has been given time to work and has
     *   not.
     */
    fun observe(step: RunStepObservation): StuckVerdict {
        record(step)
        if (stopped) return StuckVerdict.Healthy
        val signal = signal() ?: return StuckVerdict.Healthy

        val warnedAt = nudgedAt
        return when {
            warnedAt == null -> {
                nudgedAt = observed
                StuckVerdict.Nudge(signal)
            }
            // A replayed prefix can have armed this, in which case the run is
            // past its grace before its first live step — see [replay].
            // The nudge reaches the model only through the *next* node that
            // composes a prompt, and a graph may take a few steps to get to
            // one. Stopping before then would punish the run for advice it had
            // not been given yet.
            observed - warnedAt >= graceSteps -> {
                stopped = true
                StuckVerdict.Stop(signal)
            }
            else -> StuckVerdict.Healthy
        }
    }

    /**
     * Records a step that is being replayed from the run trace rather than
     * executed.
     *
     * A resumed run re-walks its recorded prefix to rebuild control flow, and
     * the detector rebuilds its window from the same walk — otherwise a run
     * that parks (which every answered background approval does) would start
     * every attempt blind to everything it had already repeated, and a loop
     * that parks would be invisible for exactly the reason the persisted spend
     * counters exist.
     *
     * Replayed steps are recorded but never *decided*: the original attempt did
     * not stop on this prefix, and returning a stop from it now would rewrite
     * what already happened. But the escalation they earned **is** carried
     * forward, and that distinction is load-bearing.
     *
     * Answering a background approval is a resume. A loop that raises one gate
     * per iteration therefore comes back through here on every answer, and if
     * the escalation restarted each time, a loop that parks more often than
     * [GRACE_STEPS] would never be stopped by the detector at all — which is
     * exactly the escape that made the per-execution step budget useless before
     * its counters were moved onto the run record. So a prefix that already
     * earned a nudge leaves the run armed: its first live repetition is
     * stopped, not warned again.
     *
     * `stopped` is deliberately *not* set from a replay, however far the prefix
     * goes. The original attempt did not end here, and pre-empting a verdict it
     * never reached would silence the detector for the rest of the run.
     *
     * **Carrying the escalation forward obliges the caller to carry the advice
     * with it.** A note is live-only, so an attempt that raised one and then
     * parked destroyed it — and a resumed run that inherits only the *clock*
     * would be stopped for ignoring advice that no longer exists. The return
     * value exists so the caller can re-queue the note for the first live node
     * that can read it; the run is then genuinely warned before it is ended,
     * which is the whole shape of a stepped recovery.
     *
     * The return value does not distinguish "the note was lost with the park"
     * from "the note had already been delivered before the park", because the
     * trace cannot tell the two apart. Re-queueing in the second case costs one
     * repeated line of advice in one prompt; not re-queueing in the first costs
     * a run stopped without ever being warned. The asymmetry decides it.
     *
     * @param step The replayed step, already fingerprinted.
     * @return `true` when this step armed the escalation — the caller owes the
     *   run its note. `false` otherwise, including when the escalation was
     *   already armed.
     */
    fun replay(step: RunStepObservation): Boolean {
        record(step)
        if (nudgedAt != null || signal() == null) return false
        nudgedAt = observed
        return true
    }

    private fun record(step: RunStepObservation) {
        observed++
        // Short-circuit order matters: a pass-through must not enter the
        // ledger, or the first node to forward a text would claim credit for
        // producing it and the real producer would later look like a repeat.
        val madeProgress = !step.isPassThrough && producedOutputs.add(step.outputFingerprint)
        stepsSinceProgress = if (madeProgress) 0 else stepsSinceProgress + 1
        // Tracked separately from progress, and deliberately not merged into
        // it: a novel input is not an achievement, it is only evidence that the
        // run is being asked something it has not been asked before. See
        // [signal] for the one place that distinction is used.
        stepsSinceNewInput = if (seenInputs.add(step.inputFingerprint)) 0 else stepsSinceNewInput + 1
        window.addLast(step)
        while (window.size > windowSize) window.removeFirst()
    }

    /**
     * The signal the window currently shows, or `null` when it shows none.
     *
     * Every signal is gated on the run having made no progress since the last
     * checkpoint, and evidence is counted only over the steps since that
     * happened.
     *
     * That gate alone is **not** what keeps a healthy `QUEUE_PROCESSOR` out of
     * the net, and it is worth saying so, because it reads as though it were.
     * A queue whose items all answer alike produces no progress at all after
     * the first, so the gate opens on it immediately. What actually protects it
     * is the second condition on [StuckSignal.NO_NEW_OUTPUT] below — that the
     * run has also stopped being *asked* anything new. Delete that conjunct as
     * redundant and a run marking twelve recipients through one tool is stopped
     * on its seventh.
     */
    private fun signal(): StuckSignal? {
        if (stepsSinceProgress == 0) return null
        val last = window.lastOrNull() ?: return null
        // Only evidence gathered *since the run last got somewhere* counts. A
        // repetition on the far side of real progress is history, not a loop —
        // and the distinction is not academic: a graph that alternates a stable
        // pass-through (a condition on a flag that does not change) with a node
        // producing fresh output repeats that pass-through on every pass while
        // the run advances the whole time. Counting across the progress would
        // stop it on its third iteration.
        val sinceProgress = window.takeLast(stepsSinceProgress)
        val identical = sinceProgress.count {
            it.nodeId == last.nodeId &&
                it.inputFingerprint == last.inputFingerprint &&
                it.outputFingerprint == last.outputFingerprint
        }
        return when {
            identical >= repeatThreshold -> StuckSignal.REPEATED_STEP
            // The weaker signal carries the extra condition, because on its own
            // it accuses a perfectly healthy queue. Draining twelve recipients
            // through a tool that answers `{"status":"ok"}` for each produces
            // one novel output and eleven repeats — no progress by the rule
            // above, and a stall by any streak worth setting. What separates
            // that run from a loop is that it is being asked something new
            // every time: the item changes even though the answer does not.
            //
            // So this fires only when the run has stopped being asked anything
            // new *as well as* having stopped saying anything new. The cost is
            // that a loop re-posing itself with an ever-growing context is left
            // to the ceilings — which is the right way round to be wrong, since
            // that run is spending and the ceilings measure spend, while the
            // other mistake kills a run that is doing its job.
            stepsSinceProgress >= staleStreak && stepsSinceNewInput >= staleStreak -> StuckSignal.NO_NEW_OUTPUT
            else -> null
        }
    }

    /**
     * The shipped thresholds, and the fingerprinting the caller needs to reduce
     * a step to something comparable.
     *
     * The numbers are constants rather than settings on purpose. A ceiling is a
     * budget, and a budget is the user's to set; "how many identical steps count
     * as a loop" is not a preference, it is a claim about when repetition stops
     * being coincidence — and a wrong answer here does not overspend, it kills a
     * healthy run. Exposing it as a slider would invite a user to widen it until
     * the detector stopped protecting them.
     */
    companion object {
        /**
         * How many recent steps are compared.
         *
         * Below the default step ceiling (15) on purpose. A window wider than
         * the ceiling could only ever fire after the ceiling had already
         * stopped the run, which would make the detector an elaborate way of
         * never being reached — and being reached first, with a better
         * explanation, is the entire reason it exists.
         */
        const val WINDOW_SIZE: Int = 12

        /**
         * Identical steps inside the window that count as a repetition.
         *
         * Two is a coincidence — a graph legitimately revisits a node — and
         * three is a pattern. Counted only over the steps since the run last
         * made progress, so a loop reached cold is caught on its *fourth* pass:
         * the first pass produces its answer for the first time, which is real
         * progress by any definition worth having. Even so the whole escalation
         * — first pass, three repetitions, four steps of grace — finishes well
         * inside the default ceiling of fifteen.
         */
        const val REPEAT_THRESHOLD: Int = 3

        /**
         * Consecutive checkpoints without progress that count as a stall.
         *
         * Higher than [REPEAT_THRESHOLD] because this signal is the weaker
         * evidence: nothing here repeated exactly, the run merely stopped
         * saying anything new. Six is comfortably above the longest chain of
         * pass-through nodes a real pipeline strings together (`INPUT` →
         * router → condition → echo `OUTPUT` is four) so an ordinary short run
         * cannot reach it.
         */
        const val STALE_STREAK: Int = 6

        /**
         * Steps the run is given to break the pattern after being nudged.
         *
         * The nudge is delivered into the next prompt-composing node's input,
         * and control-flow nodes do not compose one — so a graph can take
         * several steps to actually hand the advice to a model. Four leaves
         * room for that without letting a genuinely stuck run spin much longer.
         *
         * A graph made only of control-flow nodes never delivers it at all, and
         * is stopped anyway. That is the right outcome rather than a gap: a
         * loop with no model in it has nobody to take the advice, and the stop
         * is the only thing left that can end it.
         */
        const val GRACE_STEPS: Int = 4

        /** Digest algorithm backing [fingerprint]. */
        private const val HASH_ALGORITHM = "SHA-256"

        /**
         * Reduces one of a step's texts to the form the detector compares.
         *
         * Hashed rather than kept because the alternative is holding every
         * prompt and every answer of a run in memory for as long as the window
         * is open, on a device, for a run that may be looping precisely because
         * it is generating a great deal of text. The digest is the same
         * algorithm `PipelineGraph.contentHash` already uses, for the same
         * reason: a collision must be a thing nobody has to reason about.
         *
         * @param text The text to fingerprint.
         * @return Its lower-case hexadecimal digest.
         */
        fun fingerprint(text: String): String = MessageDigest.getInstance(HASH_ALGORITHM)
            .digest(text.toByteArray(Charsets.UTF_8))
            .joinToString(separator = "") { byte -> "%02x".format(byte) }
    }
}
