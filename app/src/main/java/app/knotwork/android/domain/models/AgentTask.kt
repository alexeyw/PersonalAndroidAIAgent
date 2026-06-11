package app.knotwork.android.domain.models

import java.util.UUID

/**
 * Represents a task to be processed by the AI agent.
 *
 * @property id Unique identifier of the task. Doubles as the id of the
 *   persistent [PipelineRun] record created when the task is enqueued — task
 *   and run relate strictly one-to-one, so no second identifier is minted.
 * @property sessionId The ID of the chat session this task belongs to.
 * @property prompt The user prompt or system instruction.
 * @property priority The priority of the task.
 * @property timestamp The time the task was created.
 * @property pipelineId Identifier of the pipeline that should run this task. `null`
 *   means the orchestrator falls back to the application-wide default
 *   pipeline (`SettingsRepository.defaultPipelineId`); when no default is
 *   configured either, the task fails with an explicit error instead of
 *   executing an arbitrary pipeline. The id is captured at enqueue time so
 *   a later edit to `ChatSession.pipelineId` does not retroactively
 *   reroute an in-flight task.
 * @property origin What triggered the task — an interactive chat message
 *   ([RunOrigin.CHAT], the default) or the background scheduler
 *   ([RunOrigin.SCHEDULER]). Recorded into the persistent [PipelineRun].
 */
data class AgentTask(
    val id: String = UUID.randomUUID().toString(),
    val sessionId: String,
    val prompt: String,
    val priority: TaskPriority = TaskPriority.NORMAL,
    val timestamp: Long = System.currentTimeMillis(),
    val pipelineId: String? = null,
    val origin: RunOrigin = RunOrigin.CHAT,
)
