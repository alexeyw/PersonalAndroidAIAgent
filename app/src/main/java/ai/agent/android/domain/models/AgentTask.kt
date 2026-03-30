package ai.agent.android.domain.models

import java.util.UUID

/**
 * Represents a task to be processed by the AI agent.
 *
 * @property id Unique identifier of the task.
 * @property sessionId The ID of the chat session this task belongs to.
 * @property prompt The user prompt or system instruction.
 * @property priority The priority of the task.
 * @property timestamp The time the task was created.
 */
data class AgentTask(
    val id: String = UUID.randomUUID().toString(),
    val sessionId: String,
    val prompt: String,
    val priority: TaskPriority = TaskPriority.NORMAL,
    val timestamp: Long = System.currentTimeMillis()
)
