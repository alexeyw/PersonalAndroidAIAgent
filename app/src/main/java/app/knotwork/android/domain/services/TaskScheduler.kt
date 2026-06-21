package app.knotwork.android.domain.services

/**
 * Domain-level constraints a scheduled agent task must satisfy before the
 * background runtime is allowed to execute it.
 *
 * Modelled as a pure-domain value so the scheduling policy (e.g. "do not run
 * while the battery is low") lives in the `domain` layer, while the mapping to
 * the concrete background-execution constraints is the implementation's
 * concern. Today only the battery guard is expressed; the type is the single
 * extension point for any future constraint (network, charging, idle).
 *
 * @property requiresBatteryNotLow When `true`, the task is held back while the
 *   device reports a low-battery state and released once charge recovers.
 */
data class ScheduledTaskConstraints(val requiresBatteryNotLow: Boolean)

/**
 * Domain port that abstracts the scheduling of future and recurring agent
 * tasks away from any concrete background-execution framework.
 *
 * Introduced so [app.knotwork.android.domain.usecases.ScheduleTaskUseCase] can
 * decide *what* to schedule (one-time vs periodic, with which prompt, session
 * and constraints) without importing `androidx.work` or reaching into the
 * `data` layer — preserving the Clean Architecture invariant that `domain`
 * carries zero Android/framework dependencies and never depends on `data`.
 *
 * The implementation (`data/services/WorkManagerTaskScheduler`) maps these
 * domain calls onto `WorkManager` requests. Both methods operate purely on
 * domain types; framework specifics (request builders, the unique-work key,
 * the existing-work policy) stay behind this boundary.
 */
interface TaskScheduler {

    /**
     * Schedules a task to run once.
     *
     * @param prompt The prompt the agent should execute when the task fires.
     * @param delayMinutes Delay before the task becomes eligible to run, in
     *   minutes. `0` (or less) means "as soon as the constraints allow".
     * @param sessionId Id of the chat session the run should land its result
     *   in, sourced from the engine-side execution context (never from LLM
     *   arguments). `null` lets the worker create a fresh auto-named session.
     * @param constraints Domain constraints the runtime must honour before
     *   executing the task.
     */
    fun scheduleOneTime(prompt: String, delayMinutes: Long, sessionId: String?, constraints: ScheduledTaskConstraints)

    /**
     * Schedules a task to run repeatedly on a fixed interval.
     *
     * Recurring tasks are de-duplicated by the `(intervalHours, sessionId,
     * trimmed prompt)` triple: scheduling the same recurring task into the same
     * session again keeps the existing schedule rather than stacking a
     * duplicate. The prompt is matched after whitespace-trimming (so a stray
     * leading/trailing space no longer slips past the de-dup), while the agent
     * still executes the verbatim prompt. [sessionId] is part of the key, so two
     * schedules with the same prompt and interval but different bound sessions
     * stay independent; a `null` session collapses with other `null`-session
     * schedules of the same prompt and interval. This contract is honoured by
     * the implementation.
     *
     * @param prompt The prompt the agent should execute on every run.
     * @param intervalHours The repeat interval, in hours. Must be positive;
     *   callers gate the one-time vs periodic choice before invoking this.
     * @param sessionId Id of the chat session each run should land its result
     *   in (see [scheduleOneTime]). `null` defers to the worker's fallback.
     * @param constraints Domain constraints the runtime must honour before
     *   executing each run.
     */
    fun schedulePeriodic(
        prompt: String,
        intervalHours: Long,
        sessionId: String?,
        constraints: ScheduledTaskConstraints,
    )
}
