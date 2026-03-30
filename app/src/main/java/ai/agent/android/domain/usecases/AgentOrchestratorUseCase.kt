package ai.agent.android.domain.usecases

import ai.agent.android.domain.engine.TaskQueueManager
import ai.agent.android.domain.models.AgentOrchestratorState
import ai.agent.android.domain.models.AgentTask
import ai.agent.android.domain.models.TaskPriority
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Use case that orchestrates the AI Agent's execution flow.
 * It reads the user's input and delegates the execution to the [TaskQueueManager].
 */
@Singleton
class AgentOrchestratorUseCase @Inject constructor(
    private val taskQueueManager: TaskQueueManager
) {

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
     * Starts the orchestration cycle for a given user prompt by queueing a task.
     *
     * @param sessionId The current chat session ID.
     * @param userPrompt The new prompt from the user.
     * @return A [Flow] of [AgentOrchestratorState] emitting the progress of the agent.
     */
    operator fun invoke(sessionId: String, userPrompt: String): Flow<AgentOrchestratorState> {
        val task = AgentTask(
            sessionId = sessionId,
            prompt = userPrompt,
            priority = TaskPriority.HIGH // High priority for active UI requests
        )
        taskQueueManager.enqueueTask(task)
        return taskQueueManager.observeTaskState(sessionId)
    }
}
