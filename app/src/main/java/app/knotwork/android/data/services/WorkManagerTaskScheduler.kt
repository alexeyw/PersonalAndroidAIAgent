package app.knotwork.android.data.services

import androidx.work.Constraints
import androidx.work.Data
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkRequest
import app.knotwork.android.domain.models.RunOrigin
import app.knotwork.android.domain.services.ScheduledTaskConstraints
import app.knotwork.android.domain.services.ScheduledTaskKind
import app.knotwork.android.domain.services.ScheduledTaskTag
import app.knotwork.android.domain.services.TaskScheduler
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * [TaskScheduler] implementation backed by Jetpack `WorkManager`.
 *
 * Translates the domain scheduling calls into `WorkManager` requests targeting
 * [AgentWorker]: it builds the input [Data] (prompt + bound session id), maps
 * [ScheduledTaskConstraints] onto a `WorkManager` [Constraints], and applies
 * the recurring-work de-duplication policy. All `androidx.work` knowledge lives
 * here so that [app.knotwork.android.domain.usecases.ScheduleTaskUseCase] stays
 * framework-free.
 */
@Singleton
class WorkManagerTaskScheduler @Inject constructor(private val workManager: WorkManager) : TaskScheduler {

    override fun scheduleOneTime(
        prompt: String,
        delayMinutes: Long,
        sessionId: String?,
        constraints: ScheduledTaskConstraints,
        pipelineId: String?,
        origin: RunOrigin,
        runId: String?,
    ) {
        val requestBuilder = OneTimeWorkRequestBuilder<AgentWorker>()
            .setInputData(buildInputData(prompt, sessionId, pipelineId, origin, runId))
            .setConstraints(constraints.toWorkConstraints())
        tagAsScheduledTask(requestBuilder, ScheduledTaskKind.ONE_TIME, intervalHours = 0, sessionId, prompt)

        if (delayMinutes > 0) {
            requestBuilder.setInitialDelay(delayMinutes, TimeUnit.MINUTES)
        }

        workManager.enqueue(requestBuilder.build())
    }

    override fun schedulePeriodic(
        prompt: String,
        intervalHours: Long,
        sessionId: String?,
        constraints: ScheduledTaskConstraints,
    ) {
        val inputData = buildInputData(prompt, sessionId, pipelineId = null, origin = RunOrigin.SCHEDULER, runId = null)
        val requestBuilder = PeriodicWorkRequestBuilder<AgentWorker>(intervalHours, TimeUnit.HOURS)
            .setInputData(inputData)
            .setConstraints(constraints.toWorkConstraints())
        tagAsScheduledTask(requestBuilder, ScheduledTaskKind.PERIODIC, intervalHours, sessionId, prompt)
        val request = requestBuilder.build()

        // Keyed by (intervalHours, sessionId, trimmed prompt) so re-scheduling
        // the same recurring agent task into the same session does not stack
        // duplicate PeriodicWorkRequests, while two schedules bound to different
        // sessions stay independent. `KEEP` means the first schedule wins; a
        // later identical call short-circuits.
        workManager.enqueueUniquePeriodicWork(
            uniquePeriodicName(prompt, intervalHours, sessionId),
            ExistingPeriodicWorkPolicy.KEEP,
            request,
        )
    }

    override fun cancelAllScheduled() {
        workManager.cancelAllWorkByTag(ScheduledTaskTag.MARKER)
    }

    /**
     * Attaches the scheduled-task marker and the human-readable label to
     * [builder].
     *
     * Both are needed because the runtime does not expose a queued request's
     * input data: the marker is what "cancel every scheduled task" matches on
     * (and what keeps trigger / tile work out of that blast radius), while the
     * label is the only way the task monitor can say which task a row is.
     *
     * @param builder The request under construction.
     * @param kind One-shot or repeating.
     * @param intervalHours Repeat interval in hours; `0` for a one-time task.
     * @param sessionId Bound chat session, or `null`.
     * @param prompt The task's prompt (truncated into the label's preview).
     */
    private fun tagAsScheduledTask(
        builder: WorkRequest.Builder<*, *>,
        kind: ScheduledTaskKind,
        intervalHours: Long,
        sessionId: String?,
        prompt: String,
    ) {
        builder.addTag(ScheduledTaskTag.MARKER)
        builder.addTag(ScheduledTaskTag.encode(kind, intervalHours, sessionId, prompt))
    }

    /**
     * Builds the worker input data carrying the prompt, the (optional) bound
     * session id, the (optional) explicit pipeline binding, the run origin and
     * the (optional) pre-minted run id read by [AgentWorker].
     */
    private fun buildInputData(
        prompt: String,
        sessionId: String?,
        pipelineId: String?,
        origin: RunOrigin,
        runId: String?,
    ): Data = Data.Builder()
        .putString(AgentWorker.KEY_PROMPT, prompt)
        .putString(AgentWorker.KEY_SESSION_ID, sessionId)
        .putString(AgentWorker.KEY_PIPELINE_ID, pipelineId)
        .putString(AgentWorker.KEY_ORIGIN, origin.name)
        .putString(AgentWorker.KEY_RUN_ID, runId)
        .build()

    /**
     * Maps the domain [ScheduledTaskConstraints] onto a `WorkManager`
     * [Constraints] instance.
     */
    private fun ScheduledTaskConstraints.toWorkConstraints(): Constraints = Constraints.Builder()
        .setRequiresBatteryNotLow(requiresBatteryNotLow)
        .build()

    /**
     * Derives the unique-work name for a recurring task from its interval, bound
     * session and prompt, enforcing the de-duplication contract of
     * [TaskScheduler.schedulePeriodic].
     *
     * The prompt is whitespace-trimmed so cosmetically different strings
     * (e.g. a stray trailing space) collapse to one schedule; the verbatim
     * prompt is still what the worker executes (see [buildInputData]). The
     * [sessionId] is part of the key so schedules bound to different sessions do
     * not collapse onto each other; a `null` session maps to the empty string
     * (the legacy fresh-session path). The prompt is placed last as the only
     * free-form field, while the numeric interval and UUID session id never
     * contain the `|` separator — so the key is unambiguous.
     */
    private fun uniquePeriodicName(prompt: String, intervalHours: Long, sessionId: String?): String =
        "agent-periodic|${intervalHours}h|${sessionId.orEmpty()}|${prompt.trim()}"
}
