package app.knotwork.android.domain.usecases

import app.knotwork.android.domain.engine.TaskQueueManager
import app.knotwork.android.domain.models.AgentOrchestratorState
import app.knotwork.android.domain.models.AgentTask
import app.knotwork.android.domain.models.RunOrigin
import app.knotwork.android.domain.models.TaskPriority
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Use case that orchestrates the AI Agent's execution flow.
 * It reads the user's input and delegates the execution to the [TaskQueueManager].
 */
@Singleton
class AgentOrchestratorUseCase @Inject constructor(private val taskQueueManager: TaskQueueManager) {

    /**
     * A global state flow representing the current processing state of the agent.
     */
    val globalState: StateFlow<AgentOrchestratorState> = taskQueueManager.globalState

    /**
     * Resumes the suspended execution cycle after the user has made a decision on tool usage.
     *
     * @param sessionId The session ID that was waiting for approval.
     * @param isApproved True if the user allowed the action, false otherwise.
     */
    fun resumeWithApproval(sessionId: String, isApproved: Boolean) {
        taskQueueManager.resumeWithApproval(sessionId, isApproved)
    }

    /**
     * Subscribes to the execution state of [sessionId] without enqueueing a
     * new task. Backs the chat reattach protocol: when a session is reopened
     * while its run is still in flight (started earlier in this process and
     * kept alive by the singleton task queue), the UI re-attaches to the live
     * stream instead of restarting the pipeline. The underlying per-session
     * flow replays its latest state on subscription, so the collector
     * immediately observes where the run currently stands.
     *
     * @param sessionId The chat session whose execution state to observe.
     * @return A [Flow] of [AgentOrchestratorState] for the given session.
     */
    fun observe(sessionId: String): Flow<AgentOrchestratorState> = taskQueueManager.observeTaskState(sessionId)

    /**
     * Returns the tool-approval request the run of [sessionId] is currently
     * suspended on, or `null` when no approval gate is active.
     *
     * Counterpart of [resumeWithApproval] for the reattach path: a session
     * whose persistent run record reads `WAITING_APPROVAL` restores its HITL
     * confirmation card from this snapshot rather than from the state flow's
     * replay cache (which console events overwrite while the run waits).
     *
     * @param sessionId The session ID whose pending approval is queried.
     * @return The pending [AgentOrchestratorState.WaitingForApproval], or `null`.
     */
    fun pendingApprovalFor(sessionId: String): AgentOrchestratorState.WaitingForApproval? =
        taskQueueManager.pendingApproval(sessionId)

    /**
     * Starts the orchestration cycle for a given user prompt by queueing a task.
     *
     * @param sessionId The current chat session ID.
     * @param userPrompt The new prompt from the user.
     * @param pipelineId Identifier of the pipeline that should run this task. Pass
     *   the value of `ChatSession.pipelineId` so each chat executes against its
     *   own bound pipeline. `null` defers to the application-wide
     *   default pipeline.
     * @return A [Flow] of [AgentOrchestratorState] emitting the progress of the agent.
     */
    operator fun invoke(
        sessionId: String,
        userPrompt: String,
        pipelineId: String? = null,
    ): Flow<AgentOrchestratorState> {
        val task = AgentTask(
            sessionId = sessionId,
            prompt = userPrompt,
            priority = TaskPriority.HIGH, // High priority for active UI requests
            pipelineId = pipelineId,
        )
        taskQueueManager.enqueueTask(task)
        return taskQueueManager.observeTaskState(sessionId)
    }

    /**
     * Enqueues a scheduler-origin run and returns its run id instead of a
     * state flow.
     *
     * Background callers (the WorkManager-driven `AgentWorker`) must not track
     * completion through [observe]: the per-session flow replays its latest
     * state on subscription, so a worker firing into a session that already
     * finished an earlier run would observe the stale terminal state and
     * report completion before its own run even started. Returning the run id
     * (identical to the persistent `PipelineRun` record id) lets the caller
     * await the terminal status on `PipelineRunRepository` — the persistent
     * source of truth that carries run identity.
     *
     * The task is enqueued with [TaskPriority.NORMAL] so a scheduled run never
     * preempts an interactive one, and with [RunOrigin.SCHEDULER] so the
     * persistent run record attributes the execution to the scheduler.
     *
     * @param sessionId Chat session the run lands its messages in.
     * @param userPrompt The stored prompt of the scheduled task.
     * @return Id of the enqueued task, equal to the id of its persistent
     *   `PipelineRun` record.
     */
    fun enqueueScheduled(sessionId: String, userPrompt: String): String {
        val task = AgentTask(
            sessionId = sessionId,
            prompt = userPrompt,
            priority = TaskPriority.NORMAL,
            origin = RunOrigin.SCHEDULER,
        )
        taskQueueManager.enqueueTask(task)
        return task.id
    }
}
