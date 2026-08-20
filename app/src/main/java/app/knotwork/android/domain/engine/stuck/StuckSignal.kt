package app.knotwork.android.domain.engine.stuck

/**
 * What the stuck-detector saw when it decided a run had stopped getting
 * anywhere.
 *
 * The signal is diagnostic, not a second vocabulary: a detector stop always
 * terminates the run with the one shared
 * [app.knotwork.android.domain.models.RunTerminationReason.NoProgress], and the
 * signal only says *which* observation produced that verdict. It reaches the
 * run console and the live notice; it never reaches the user's sentence, which
 * is resolved once from the termination reason in the presentation layer.
 *
 * **On the signal that is not here.** The detector was specified with a third
 * signal, "the same error repeating". It is not implemented because it is
 * unobservable by construction, not because it was skipped: a node that reports
 * an error ends the whole run at that first occurrence
 * (`GraphExecutionEngine`'s `if (nodeResult?.error != null) { … return@flow }`),
 * for every node type including `TOOL` and `PIPELINE`. An error therefore
 * cannot occur twice in one run, and a signal counting repetitions of it would
 * be a branch no run can reach. The failure it was meant to catch — a graph
 * that keeps re-doing work that keeps not working — is caught by
 * [REPEATED_STEP], because a step that fails the same way produces the same
 * output.
 *
 * @property diagnostic Terse, stable, greppable form for the run console. Same
 *   contract as `RunTerminationReason.diagnostic()`: one line, lower case, no
 *   trailing punctuation.
 */
enum class StuckSignal(val diagnostic: String) {
    /**
     * The same node executed on the same input and returned the same output,
     * over and over, inside the window.
     *
     * Deliberately keyed on the output as well as the input. A node re-entered
     * with identical input that answers *differently* each time is making
     * progress — it is a queue draining, or a model exploring — and a detector
     * that fired on the input alone would kill it. Keying on the triple makes
     * the signal mean "nothing about this step changed", which is the only
     * version of it that is safe to act on.
     */
    REPEATED_STEP("repeated-step"),

    /**
     * No checkpoint produced anything new for a whole streak, without any one
     * step repeating exactly.
     *
     * The catch-all of the two: a loop can shuffle between several nodes and
     * never repeat a single triple while still saying nothing new. See
     * [GraphStuckDetector] for what "anything new" means operationally.
     */
    NO_NEW_OUTPUT("no-new-output"),
}
