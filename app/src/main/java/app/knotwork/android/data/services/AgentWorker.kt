package app.knotwork.android.data.services

import android.app.NotificationManager
import android.content.Context
import android.content.pm.ServiceInfo
import androidx.core.app.NotificationChannelCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import app.knotwork.android.R
import app.knotwork.android.domain.constants.NotificationChannels
import app.knotwork.android.domain.engine.LlmInferenceEngine
import app.knotwork.android.domain.models.ChatSession
import app.knotwork.android.domain.models.PipelineRun
import app.knotwork.android.domain.models.PipelineRunStatus
import app.knotwork.android.domain.models.Role
import app.knotwork.android.domain.repositories.ChatRepository
import app.knotwork.android.domain.repositories.PipelineRunRepository
import app.knotwork.android.domain.services.ScheduledTaskNotifier
import app.knotwork.android.domain.usecases.AgentOrchestratorUseCase
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.flow.onEach
import timber.log.Timber
import java.util.UUID

/**
 * Worker that executes a scheduled agent task in the background through the
 * exact same path as an interactive chat message.
 *
 * The prompt is enqueued via [AgentOrchestratorUseCase.enqueueScheduled] —
 * `TaskQueueManager` → `GraphExecutionEngine` — so everything the engine
 * persists for interactive runs (the user message, intermediate `isFinal =
 * false` node messages, the final `isFinal = true` answer, the run record,
 * the trace) lands identically for scheduled runs: opening the bound session
 * later shows the conversation as if the run had happened on screen.
 *
 * Completion is tracked through the persistent `pipeline_runs` record rather
 * than the per-session state flow: the flow replays its latest state on
 * subscription, so a worker firing into a session with an earlier finished
 * run would mistake the stale terminal state for its own. The run id returned
 * by `enqueueScheduled` carries unambiguous identity.
 *
 * Outcome announcement is delegated to [ScheduledTaskNotifier]
 * ("Task completed" / "Task failed" with a deep-link into the session).
 * Cancelled and interrupted runs are not announced — the user (or the system)
 * already intervened.
 */
@HiltWorker
class AgentWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted workerParams: WorkerParameters,
    private val agentOrchestratorUseCase: AgentOrchestratorUseCase,
    private val chatRepository: ChatRepository,
    private val pipelineRunRepository: PipelineRunRepository,
    private val scheduledTaskNotifier: ScheduledTaskNotifier,
    private val llmEngine: LlmInferenceEngine,
) : CoroutineWorker(context, workerParams) {

    companion object {
        /** Input-data key carrying the stored prompt of the scheduled task. */
        const val KEY_PROMPT = "agent_prompt"

        /**
         * Input-data key carrying the id of the chat session the run should
         * land its result in. Optional: when absent (work enqueued before the
         * key existed) or when the session was deleted before the task fired,
         * the worker creates a fresh auto-named session instead.
         */
        const val KEY_SESSION_ID = "agent_session_id"

        /** Progress key exposing the node currently executing (or the run status). */
        const val KEY_CURRENT_STAGE = "current_stage"

        /** Notification id of the worker's own foreground promotion (distinct from `AgentForegroundService`). */
        private const val FOREGROUND_NOTIFICATION_ID = 102

        /** Maximum number of prompt characters used for the auto-generated session name. */
        private const val SESSION_NAME_PROMPT_LENGTH = 40
    }

    override suspend fun doWork(): Result {
        val prompt = inputData.getString(KEY_PROMPT)

        if (prompt.isNullOrBlank()) {
            Timber.e("AgentWorker failed: Prompt is null or empty.")
            return Result.failure()
        }

        promoteToForeground()

        // Pre-enqueue failures (session lookup, queue write) may be transient
        // infrastructure problems — let WorkManager retry them.
        val sessionId: String
        val runId: String
        try {
            sessionId = resolveSession(inputData.getString(KEY_SESSION_ID), prompt)
            runId = agentOrchestratorUseCase.enqueueScheduled(sessionId, prompt)
            Timber.d("AgentWorker enqueued scheduled run %s into session %s", runId, sessionId)
        } catch (e: CancellationException) {
            // WorkManager cancels the worker by cancelling this coroutine —
            // mapping the cancellation to `retry()` would resurrect a job the
            // system (or the user) just killed.
            throw e
        } catch (e: Exception) {
            Timber.e(e, "AgentWorker failed before the run was enqueued.")
            return Result.retry()
        }

        // Post-enqueue, the run executes in the singleton task queue
        // independently of this coroutine: the user message is already
        // persisted, so a worker retry would duplicate it. Failures past this
        // point are logged but never mapped to retry().
        try {
            val run = awaitTerminalRun(sessionId, runId)
            announceOutcome(run)
            releaseEngineIfUnowned()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Timber.e(e, "AgentWorker failed while tracking run %s.", runId)
        }
        return Result.success()
    }

    /**
     * Builds the foreground promotion descriptor required for long-running
     * workers: the dedicated worker notification on the (idempotently
     * registered) foreground channel, typed `specialUse` — the same FGS
     * subtype the app's own `AgentForegroundService` is approved for.
     */
    override suspend fun getForegroundInfo(): ForegroundInfo {
        ensureForegroundChannel()
        val notification = NotificationCompat.Builder(applicationContext, NotificationChannels.AGENT_FOREGROUND)
            .setContentTitle(applicationContext.getString(R.string.notifications_scheduled_running_title))
            .setContentText(applicationContext.getString(R.string.notifications_scheduled_running_body))
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setOngoing(true)
            .build()
        return ForegroundInfo(
            FOREGROUND_NOTIFICATION_ID,
            notification,
            ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE,
        )
    }

    /**
     * Tries to promote the worker to a foreground service so a long inference
     * is not subject to the ~10-minute background-job runtime cap. The
     * promotion is best-effort: when the app is deep in the background the
     * system may forbid the foreground-service start
     * (`ForegroundServiceStartNotAllowedException`, an [IllegalStateException]
     * subtype) — the worker then simply continues non-foreground within the
     * standard quota.
     */
    private suspend fun promoteToForeground() {
        try {
            setForeground(getForegroundInfo())
        } catch (e: CancellationException) {
            // CancellationException extends IllegalStateException — re-throw it
            // before the broad catch so a worker stop is never swallowed.
            throw e
        } catch (e: IllegalStateException) {
            Timber.w(e, "AgentWorker could not enter foreground; continuing within background quota.")
        }
    }

    /**
     * Registers the foreground-status channel the promotion notification posts
     * to. Normally `AgentForegroundService` registers it, but in a headless
     * process (woken by WorkManager, no activity ever created) that service
     * never runs — without this call the promotion notification would target
     * an unregistered channel.
     */
    private fun ensureForegroundChannel() {
        val channel = NotificationChannelCompat.Builder(
            NotificationChannels.AGENT_FOREGROUND,
            NotificationManager.IMPORTANCE_LOW,
        )
            .setName(applicationContext.getString(R.string.notifications_agent_foreground_channel_name))
            .build()
        NotificationManagerCompat.from(applicationContext).createNotificationChannel(channel)
    }

    /**
     * Resolves the chat session the run lands in. The requested session is
     * used when it still exists; otherwise (deleted before the task fired, or
     * legacy work without the key) a fresh session is created, auto-named from
     * the truncated task prompt with an explicit scheduled-source mark.
     */
    private suspend fun resolveSession(requestedSessionId: String?, prompt: String): String {
        if (requestedSessionId != null && chatRepository.getSessionById(requestedSessionId) != null) {
            return requestedSessionId
        }
        val session = ChatSession(
            id = UUID.randomUUID().toString(),
            name = applicationContext.getString(
                R.string.scheduled_session_name,
                prompt.take(SESSION_NAME_PROMPT_LENGTH),
            ),
            updatedAt = System.currentTimeMillis(),
        )
        chatRepository.saveSession(session)
        Timber.d("AgentWorker created session %s for orphaned scheduled task.", session.id)
        return session.id
    }

    /**
     * Suspends until the run identified by [runId] reaches a terminal status,
     * mirroring node progress into WorkManager's progress data along the way.
     */
    private suspend fun awaitTerminalRun(sessionId: String, runId: String): PipelineRun =
        pipelineRunRepository.observeRunsForSession(sessionId)
            .mapNotNull { runs -> runs.firstOrNull { it.id == runId } }
            .onEach { run ->
                setProgress(
                    Data.Builder()
                        .putString(KEY_CURRENT_STAGE, run.currentNodeId ?: run.status.name)
                        .build(),
                )
            }
            .first { it.status.isTerminal }

    /** Posts the outcome notification for terminal statuses that warrant one. */
    private suspend fun announceOutcome(run: PipelineRun) {
        when (run.status) {
            PipelineRunStatus.COMPLETED ->
                scheduledTaskNotifier.notifyCompleted(run.sessionId, finalAnswerPreview(run.sessionId))
            PipelineRunStatus.FAILED ->
                scheduledTaskNotifier.notifyFailed(
                    run.sessionId,
                    run.errorMessage ?: applicationContext.getString(R.string.notifications_task_failed_title),
                )
            else -> Unit // CANCELLED / INTERRUPTED: the user or the system already intervened.
        }
    }

    /**
     * Returns the first non-blank line of the final agent answer, used as the
     * completion-notification body.
     */
    private suspend fun finalAnswerPreview(sessionId: String): String {
        val finalMessage = chatRepository.getMessagesForSession(sessionId).first()
            .lastOrNull { it.role == Role.AGENT && it.isFinal }
        return finalMessage?.content
            ?.lineSequence()
            ?.firstOrNull { it.isNotBlank() }
            ?.trim()
            .orEmpty()
    }

    /**
     * Unloads the LLM engine after the run when nothing else owns its
     * lifecycle. With `AgentForegroundService` alive its `AgentIdleManager`
     * unloads the model on idle timeout; in a headless process no such owner
     * exists, and leaving the model resident would pin hundreds of megabytes
     * until the OS kills the process. Skipped while other sessions still have
     * active runs in the queue.
     */
    private suspend fun releaseEngineIfUnowned() {
        if (AgentForegroundService.isRunning) return
        if (pipelineRunRepository.observeActiveRunSessionIds().first().isNotEmpty()) return
        if (llmEngine.isInitialized) {
            Timber.d("AgentWorker unloading LLM engine after scheduled run (no foreground service).")
            llmEngine.unload()
        }
    }
}
