package app.knotwork.android.domain.usecases

import app.knotwork.android.domain.services.ScheduledTaskConstraints
import app.knotwork.android.domain.services.TaskScheduler
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Use case to schedule a future or periodic task for the agent.
 *
 * Decides between a one-time and a recurring schedule based on the interval and
 * delegates the actual enqueueing to the [TaskScheduler] domain port, keeping
 * this use case free of any background-execution framework dependency.
 */
@Singleton
class ScheduleTaskUseCase @Inject constructor(private val taskScheduler: TaskScheduler) {

    /**
     * Schedules a task.
     *
     * @param prompt The prompt or task description for the agent to execute.
     * @param intervalHours The interval in hours. If > 0, it schedules a periodic task.
     *                      If 0, it schedules a one-time task to execute immediately (or later if we added delay).
     * @param delayMinutes Optional delay in minutes for one-time tasks.
     * @param sessionId Id of the chat session the scheduled run should land its
     *   result in. Comes from the engine-side `ToolExecutionContext` (never from
     *   LLM arguments). `null` — e.g. for work enqueued before this parameter
     *   existed — makes `AgentWorker` create a fresh auto-named session per run.
     * @return A success message or an error message.
     */
    operator fun invoke(
        prompt: String,
        intervalHours: Long = 0,
        delayMinutes: Long = 0,
        sessionId: String? = null,
    ): String = try {
        val constraints = ScheduledTaskConstraints(requiresBatteryNotLow = true)

        if (intervalHours > 0) {
            taskScheduler.schedulePeriodic(prompt, intervalHours, sessionId, constraints)
            Timber.d("Scheduled periodic task every $intervalHours hours with prompt: $prompt")
            "Task successfully scheduled to run every $intervalHours hours."
        } else {
            taskScheduler.scheduleOneTime(prompt, delayMinutes, sessionId, constraints)
            Timber.d("Scheduled one-time task with delay $delayMinutes minutes. Prompt: $prompt")
            "One-time task successfully scheduled with $delayMinutes minutes delay."
        }
    } catch (e: Exception) {
        Timber.e(e, "Failed to schedule task")
        "Failed to schedule task: ${e.message}"
    }
}
