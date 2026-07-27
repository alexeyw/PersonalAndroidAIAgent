package app.knotwork.android.domain.models

/**
 * At-a-glance background-reliability state of a single [Trigger], derived from
 * its trigger-evaluation journal and surfaced as the health-badge on the trigger
 * list row.
 *
 * The state answers the two questions a user has about a background automation
 * they cannot see running: *"is it still actually being checked?"* and *"did the
 * last thing it ran succeed?"*. It is only meaningful for a bound, evaluated
 * trigger — an unbound (inert) trigger is never evaluated and therefore carries
 * no health state (`null`), not a "healthy" one.
 */
enum class TriggerHealthStatus {
    /**
     * The trigger is being evaluated within its expected cadence and its most
     * recent fired run (if any) succeeded — nothing is wrong.
     */
    HEALTHY,

    /**
     * No evaluation has been recorded for longer than the trigger's expected
     * cadence allows for. This is the direct symptom of the platform starving the
     * background poll (an OEM background killer or Doze), so it reads as a mild
     * warning rather than a hard error — the trigger itself is fine, the phone
     * simply is not checking it.
     */
    STALE,

    /**
     * The trigger is being evaluated on cadence, but its most recent *fired* run
     * ended in a non-success terminal outcome (a failure, a system cancellation,
     * or a human-in-the-loop timeout). Distinct from [STALE]: the background is
     * healthy, the run is not.
     */
    ERRORED,
}

/**
 * The two journal-derived facts needed to compute a trigger's
 * [TriggerHealthStatus], collapsed to one value per trigger so the list can
 * derive every row's badge from a single map lookup.
 *
 * @property latestEvaluatedAt Epoch-millis of the trigger's most recent
 *   evaluation of *any* verdict — drives the staleness check (how long since the
 *   background last looked at this trigger).
 * @property latestFiredOutcome Terminal outcome of the trigger's most recent
 *   *fired* run, or `null` when it has never fired **or** the latest fired run
 *   has not settled yet (still pending). A `null` therefore never counts as an
 *   error — only a settled non-success outcome does.
 */
data class TriggerHealthInputs(val latestEvaluatedAt: Long, val latestFiredOutcome: TriggerRunOutcome? = null)
