package app.knotwork.android.domain.models

/**
 * Advice the run has been given but has not yet been able to read.
 *
 * Two guards write here — the soft ceiling ("you are close to your limit") and
 * the stuck-detector ("you are repeating yourself") — and both face the same
 * delivery problem: a note can only reach a model through a node that composes
 * a prompt, and the node that *raised* the note usually is not one. So the note
 * waits, and the next composing node picks it up.
 *
 * **With one exception the reader has to know about: `OUTPUT` never drains.**
 * It composes a prompt when it has a system prompt, so it looks like a valid
 * recipient — but its executor persists its own input verbatim as the agent's
 * chat message whenever the model returns nothing, which would print this
 * holder's contents to the user as their answer. The exclusion is on the node
 * type at every depth, because a nested `OUTPUT`'s text becomes the parent's
 * and can still reach a pass-through root. The consequence to plan for: when
 * the only composing node left after a warning is an `OUTPUT`, the note is
 * never delivered — the run is finishing anyway, and advice to wrap up has no
 * reader at the node that is the wrapping up.
 *
 * **Shared across the whole run tree**, on the same terms as `RunBudgetLedger`
 * and `GraphStuckDetector`, and for a reason that is not symmetry. The
 * detector's grace period counts steps at *every* depth, so a nudge raised just
 * before a `PIPELINE` node has its clock run down by the child's steps — while
 * a note held in the parent's own invocation could never reach the child at
 * all. A run would then be stopped for ignoring advice nobody had given it.
 * Sharing the holder is what makes the delivery scope and the clock scope the
 * same scope.
 *
 * A list rather than a single slot because there are two writers and a long
 * repeating run trips both: with one slot, whichever spoke second silently
 * erased the other's advice. Bounded by construction — each ceiling axis warns
 * once per *attempt* and the detector nudges once, so at most three notes can
 * ever be waiting. (Per attempt, not per tree: the ceiling's claim set lives on
 * a ledger that is rebuilt on every resume, so a run that parks past its soft
 * threshold warns again — a known inconsistency with the detector, whose
 * escalation is carried across a resume. Bounding is unaffected either way,
 * since a note only waits until the next composing node.)
 *
 * Deliberately **live-only**: notes are never persisted. A note is advice about
 * what to do next, and a run being resumed from a checkpoint is re-deciding
 * that from scratch.
 *
 * The holder is a small mutable object passed by reference, like the ledger:
 * the engine's walk is the only writer and the recursion into a sub-pipeline is
 * strictly synchronous within one coroutine, so no synchronisation is needed.
 */
class RunContextNotes {

    private val pending: MutableList<String> = mutableListOf()

    /**
     * Queues a note for the next node that composes a prompt.
     *
     * @param note The system notice, already worded for a model to act on.
     */
    fun add(note: String) {
        pending += note
    }

    /**
     * Takes everything waiting, leaving the holder empty.
     *
     * Draining rather than reading: a note is advice to act on now, and one
     * repeated into every subsequent prompt would push the actual task further
     * down the context on each step.
     *
     * @return The notes joined in the order they were raised, or `null` when
     *   nothing is waiting — so a caller can skip composing anything at all.
     */
    fun drain(): String? {
        if (pending.isEmpty()) return null
        val joined = pending.joinToString(separator = "\n\n")
        pending.clear()
        return joined
    }
}
