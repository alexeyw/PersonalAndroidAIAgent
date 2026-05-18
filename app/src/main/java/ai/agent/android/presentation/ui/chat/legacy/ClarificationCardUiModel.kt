package ai.agent.android.presentation.ui.chat.legacy

/**
 * UI projection of a `ClarificationRequest` rendered as a card inside the chat
 * timeline. Lives only in [ChatUiState] (not persisted to the database) and tracks
 * whether the user has answered, the request timed out, or it is still pending.
 *
 * @property id Stable identifier matching `ClarificationRequest.id`. Used both as a
 *   Compose key and as the correlation id when forwarding the user's reply to
 *   `ClarificationRepository.submitClarification`.
 * @property question Human-readable prompt to display to the user.
 * @property options Predefined answer choices. When `null` (or empty) the UI must
 *   render a free-form text input instead of option buttons.
 * @property timeoutMs Total time, in milliseconds, the agent will wait for the user
 *   before falling back to a default answer. Drives the visual countdown and is also
 *   enforced authoritatively by `ClarificationRepository`.
 * @property startedAtMs Monotonic timestamp (`SystemClock.uptimeMillis()`) captured
 *   when the card was first appended to the UI state. The countdown is rendered as
 *   `(startedAtMs + timeoutMs) - now`, which is robust to recompositions because the
 *   start moment is fixed.
 * @property status Current lifecycle stage of the card.
 * @property answer The answer text once [status] becomes [Status.ANSWERED] or
 *   [Status.TIMED_OUT]; `null` while pending.
 */
data class ClarificationCardUiModel(
    val id: String,
    val question: String,
    val options: List<String>?,
    val timeoutMs: Long,
    val startedAtMs: Long,
    val status: Status = Status.PENDING,
    val answer: String? = null,
) {
    /**
     * Lifecycle stages of a clarification card.
     */
    enum class Status {
        /** The card is showing its question and is awaiting user input. */
        PENDING,

        /** The user submitted a reply; the card is collapsed to a non-editable summary. */
        ANSWERED,

        /** The visual countdown elapsed without a user reply; the agent fell back to the default answer. */
        TIMED_OUT,
    }
}
