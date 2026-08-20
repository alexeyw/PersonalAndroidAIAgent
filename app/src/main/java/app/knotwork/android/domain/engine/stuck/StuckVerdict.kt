package app.knotwork.android.domain.engine.stuck

/**
 * What the stuck-detector wants done after observing one step.
 *
 * The three values are the stepped recovery, in order: say nothing, warn the
 * run so it can wind itself up, end it. Modelled as a sealed type rather than a
 * nullable signal because the *stage* is the decision — the same signal means
 * "nudge" the first time and "stop" the second, and a caller that had to
 * re-derive which one it was would be re-implementing the escalation.
 */
sealed interface StuckVerdict {

    /** Nothing to say: the run is making progress, or has not repeated enough. */
    data object Healthy : StuckVerdict

    /**
     * The run looks stuck and is being told so, once.
     *
     * The caller injects a note into the next node's context and shows the live
     * notice. The run keeps going — a graph that recovers on its own is the
     * outcome this stage exists to produce, and it is the common one: a model
     * told that it is repeating itself usually stops.
     *
     * @property signal What was observed.
     */
    data class Nudge(val signal: StuckSignal) : StuckVerdict

    /**
     * The nudge did not work and the run must be ended.
     *
     * Raised only after the run has been warned and given
     * [GraphStuckDetector.GRACE_STEPS] further steps to break the pattern, so a
     * single unlucky observation can never end a run on its own.
     *
     * @property signal What was observed the second time.
     */
    data class Stop(val signal: StuckSignal) : StuckVerdict
}
