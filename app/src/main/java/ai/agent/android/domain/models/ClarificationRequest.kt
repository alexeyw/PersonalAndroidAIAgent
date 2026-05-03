package ai.agent.android.domain.models

/**
 * A request from the agent to the user asking for clarification or additional input
 * before continuing pipeline execution.
 *
 * The request is published by a node executor and consumed by the UI; the agent
 * coroutine suspends until the user replies via
 * [ai.agent.android.domain.repositories.ClarificationRepository.submitClarification]
 * or until [timeoutMs] elapses (in which case a default answer is used).
 *
 * @property id Unique identifier of this request, used to correlate the user's reply
 *   with the suspended coroutine. Typically a UUID generated at request creation.
 * @property question Human-readable question to display to the user.
 * @property options Predefined answer choices. When `null` the UI must offer a
 *   free-form text input. An empty list is treated equivalently to `null` for
 *   default-answer purposes (the timeout default becomes an empty string).
 * @property timeoutMs Maximum time, in milliseconds, the agent will wait for the
 *   user's response before falling back to the default answer.
 */
data class ClarificationRequest(
    val id: String,
    val question: String,
    val options: List<String>?,
    val timeoutMs: Long,
)
