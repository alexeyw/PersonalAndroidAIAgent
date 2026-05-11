package ai.agent.android.domain.usecases

import ai.agent.android.data.services.AgentWorker
import androidx.work.Constraints
import androidx.work.Data
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import timber.log.Timber
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Use case to schedule a future or periodic task for the agent.
 * Parses the interval and enqueues a work request to the WorkManager.
 */
@Singleton
class ScheduleTaskUseCase @Inject constructor(private val workManager: WorkManager) {

    /**
     * Schedules a task.
     *
     * @param prompt The prompt or task description for the agent to execute.
     * @param intervalHours The interval in hours. If > 0, it schedules a periodic task.
     *                      If 0, it schedules a one-time task to execute immediately (or later if we added delay).
     * @param delayMinutes Optional delay in minutes for one-time tasks.
     * @return A success message or an error message.
     */
    operator fun invoke(prompt: String, intervalHours: Long = 0, delayMinutes: Long = 0): String = try {
        val inputData = Data.Builder()
            .putString(AgentWorker.KEY_PROMPT, prompt)
            .build()

        val constraints = Constraints.Builder()
            .setRequiresBatteryNotLow(true) // Native WorkManager way to pause on low battery
            .build()

        if (intervalHours > 0) {
            // Periodic task
            val request = PeriodicWorkRequestBuilder<AgentWorker>(intervalHours, TimeUnit.HOURS)
                .setInputData(inputData)
                .setConstraints(constraints)
                .build()
            workManager.enqueue(request)
            Timber.d("Scheduled periodic task every \$intervalHours hours with prompt: \$prompt")
            "Task successfully scheduled to run every \$intervalHours hours."
        } else {
            // One-time task
            val requestBuilder = OneTimeWorkRequestBuilder<AgentWorker>()
                .setInputData(inputData)
                .setConstraints(constraints)

            if (delayMinutes > 0) {
                requestBuilder.setInitialDelay(delayMinutes, TimeUnit.MINUTES)
            }

            workManager.enqueue(requestBuilder.build())
            Timber.d("Scheduled one-time task with delay \$delayMinutes minutes. Prompt: \$prompt")
            "One-time task successfully scheduled with \$delayMinutes minutes delay."
        }
    } catch (e: Exception) {
        Timber.e(e, "Failed to schedule task")
        "Failed to schedule task: \${e.message}"
    }
}
