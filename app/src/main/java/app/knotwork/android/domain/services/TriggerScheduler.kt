package app.knotwork.android.domain.services

import app.knotwork.android.domain.models.Trigger

/**
 * Domain port abstracting the background registration of [Trigger]s away from
 * any concrete scheduling framework.
 *
 * Introduced so the trigger use cases can decide *which* triggers should be
 * watched without importing `androidx.work`: the implementation
 * (`data/services/WorkManagerTriggerScheduler`) maps each active trigger onto a
 * constraint-gated WorkManager request that wakes
 * `data/services/TriggerWatchWorker` when the trigger's condition is satisfied.
 *
 * The mapping from a [Trigger] to the concrete background constraints
 * (charging / network type / periodic interval / time-of-day delay), the
 * unique-work key and the existing-work policy all live behind this boundary.
 */
interface TriggerScheduler {

    /**
     * Reconciles the registered background watches to exactly [activeTriggers].
     *
     * Idempotent and edge-safe: triggers in the set are (re)registered, any
     * previously-registered trigger absent from the set is cancelled. This is
     * the single entry point used on app start and after any trigger
     * create/edit/enable/delete, so the registered set always matches the
     * persisted active set.
     *
     * @param activeTriggers The triggers that should currently be watched —
     *   exactly the [Trigger.isActive] subset (enabled and bound).
     */
    fun syncAll(activeTriggers: List<Trigger>)

    /**
     * Registers (or replaces) the background watch for a single active trigger.
     *
     * @param trigger The trigger to watch. Callers pass only [Trigger.isActive]
     *   triggers; passing an inactive one is treated as [cancel].
     */
    fun register(trigger: Trigger)

    /**
     * Cancels the background watch for a trigger, by id. Idempotent.
     *
     * @param triggerId The id of the trigger to stop watching.
     */
    fun cancel(triggerId: String)
}
