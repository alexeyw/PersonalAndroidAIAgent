package app.knotwork.design.components.chat

/**
 * Immutable payload backing a [ClarificationCard] embedded inside a
 * [ChatContent.Clarification] message.
 *
 * @property question free-text question the assistant is asking — wraps
 * naturally inside the assistant bubble.
 * @property quickReplies zero to four canned answers rendered as
 * `KnotworkChip(style = Tonal)` in a `FlowRow`. Tapping a chip submits
 * that exact label as the reply.
 * @property freeformPlaceholder placeholder text for the free-form
 * `TextField` rendered beneath the quick replies.
 * @property replied non-null when the user has answered — the card
 * collapses to a one-line "Replied: …" summary and stops accepting input.
 */
data class ClarificationCardModel(
    val question: String,
    val quickReplies: List<String> = emptyList(),
    val freeformPlaceholder: String = "Your answer…",
    val replied: String? = null,
)
