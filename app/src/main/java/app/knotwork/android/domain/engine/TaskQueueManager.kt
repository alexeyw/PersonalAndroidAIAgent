package app.knotwork.android.domain.engine

import app.knotwork.android.domain.models.AgentOrchestratorState
import app.knotwork.android.domain.models.AgentTask
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

/**
 * Interface for managing and queueing agent tasks to ensure
 * the LLM processes one request at a time without memory issues.
 */
interface TaskQueueManager {

    /**
     * A global state flow representing the overall processing state of the agent across all tasks.
     */
    val globalState: StateFlow<AgentOrchestratorState>

    /**
     * A flow emitting the map of active session IDs to their current states.
     */
    val activeSessionsState: StateFlow<Map<String, AgentOrchestratorState>>

    /**
     * Enqueues a new task to be processed.
     *
     * @param task The [AgentTask] to add to the queue.
     */
    fun enqueueTask(task: AgentTask)

    /**
     * Observes the execution state for a specific session.
     *
     * @param sessionId The ID of the session.
     * @return A [Flow] of [AgentOrchestratorState] for the given session.
     */
    fun observeTaskState(sessionId: String): Flow<AgentOrchestratorState>

    /**
     * Resumes a paused execution cycle for a specific session.
     *
     * @param sessionId The session ID waiting for approval.
     * @param isApproved True if the user approved the action.
     */
    fun resumeWithApproval(sessionId: String, isApproved: Boolean)

    /**
     * Returns the tool-approval request the run of [sessionId] is currently
     * suspended on, or `null` when no approval gate is active for that session.
     *
     * Counterpart of [resumeWithApproval] for the chat reattach protocol: a UI
     * re-attaching to a session whose persistent run record reads
     * `WAITING_APPROVAL` restores the confirmation card from this snapshot —
     * the per-session state flow's replay cache cannot be relied on because
     * console events emitted while the run waits overwrite the
     * [AgentOrchestratorState.WaitingForApproval] emission.
     *
     * @param sessionId The session ID whose pending approval is queried.
     * @return The pending [AgentOrchestratorState.WaitingForApproval], or `null`.
     */
    fun pendingApproval(sessionId: String): AgentOrchestratorState.WaitingForApproval?
}
