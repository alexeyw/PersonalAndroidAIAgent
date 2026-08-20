package app.knotwork.android.domain.models

/**
 * Why a run in flight is showing the user an advisory.
 *
 * The counterpart of [RunTerminationReason] for runs that have **not** stopped:
 * something about the run is worth saying out loud while there is still time to
 * act on it. The remedy is always the same — wind this up — so the surface is
 * one calm, non-modal label above the composer; the cause is what decides its
 * glyph and its sentence.
 *
 * Modelled as a sealed type with a single variant on purpose. The graph
 * stuck-detector adds the second one, and it should be able to do that by
 * *adding a variant* rather than by changing the shape of the state that
 * carries it — the notice surface was designed with both causes in view
 * precisely so the second arrival does not re-open it.
 *
 * Deliberately **live-only**: a notice is never persisted and never survives
 * the process. It carries the numbers behind the decision, which is exactly
 * what a persisted form could not do — the limit in force is not stored, and
 * re-reading the setting later could report a number the user has since
 * changed. A notice that can only exist while the run is in memory is a notice
 * that can always tell the truth.
 */
sealed interface RunNoticeCause {

    /**
     * The run crossed the soft threshold on one axis — 75 % of the hard
     * ceiling — and is still going.
     *
     * Raised once per axis per run tree. A warning repeated on every node is
     * noise that teaches the reader to ignore it.
     *
     * @property axis Which ceiling is being approached.
     * @property spent What the run tree had accounted for on that axis when the
     *   threshold was crossed.
     * @property hardLimit The ceiling that will stop the run if it keeps going.
     */
    data class ApproachingCeiling(val axis: RunCeilingAxis, val spent: Int, val hardLimit: Int) : RunNoticeCause
}

/**
 * Terse, log-oriented rendering of a notice cause, for the run console and the
 * persisted run trace.
 *
 * The same split as `RunTerminationReason.diagnostic()`: this is the line an
 * engineer greps, not the sentence a user reads. The sentence is resolved from
 * the typed cause in the presentation layer.
 *
 * @return A single line, lower-case, no trailing punctuation.
 */
fun RunNoticeCause.diagnostic(): String = when (this) {
    is RunNoticeCause.ApproachingCeiling ->
        "soft-ceiling: $spent/$hardLimit ${axis.name.lowercase()}"
}
