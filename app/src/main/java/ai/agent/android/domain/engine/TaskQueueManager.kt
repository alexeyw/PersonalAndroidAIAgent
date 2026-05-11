package ai.agent.android.domain.engine

import ai.agent.android.domain.models.AgentOrchestratorState
import ai.agent.android.domain.models.AgentTask
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
}
