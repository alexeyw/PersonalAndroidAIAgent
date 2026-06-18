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
 * @property isResume When `true`, this task resumes an interrupted run
 *   instead of starting a fresh one: [id] is the id of the existing
 *   [PipelineRun] record (already flipped back to queued by
 *   `PipelineRunRepository.markResumed`), the queue worker skips re-saving
 *   the user message and re-creating the run record, resolves the pipeline
 *   strictly by the run's recorded pipeline id, and drives the engine in
 *   checkpoint-replay mode from the persisted trace.
 * @property attachment The image attached to the originating user message, or
 *   `null` when none. Carried so the queue worker can persist it onto the saved
 *   user [ChatMessage]; by the phase contract only [prompt] text travels the
 *   pipeline graph, so the attachment rides the message but does not flow
 *   between nodes here.
 * @property displayContent Text persisted on the saved user [ChatMessage] when
 *   it must differ from [prompt]. Used for an image-only message: the bubble
 *   shows just the thumbnail (empty display content) while [prompt] carries the
 *   internal default instruction that travels the graph. `null` means the saved
 *   message uses [prompt] verbatim (the common case).
 */
data class AgentTask(
    val id: String = UUID.randomUUID().toString(),
    val sessionId: String,
    val prompt: String,
    val priority: TaskPriority = TaskPriority.NORMAL,
    val timestamp: Long = System.currentTimeMillis(),
    val pipelineId: String? = null,
    val origin: RunOrigin = RunOrigin.CHAT,
    val isResume: Boolean = false,
    val attachment: MessageAttachment? = null,
    val displayContent: String? = null,
)
