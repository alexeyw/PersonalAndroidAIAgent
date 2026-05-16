package app.knotwork.design.components.chat

/**
 * Delivery status of a [ChatMessage] as surfaced in [ChatMetadata.status].
 *
 * Drives the trailing micro-glyph in the assistant / user bubble footer and
 * the error treatment when the message fails to send. The catalog component
 * does not perform retries — screens own that — but it renders distinct
 * visuals so users see the state at a glance.
 */
enum class ChatMessageStatus {
    /** Optimistically rendered while the network round-trip is in flight. */
    Pending,

    /** Acknowledged by the backend / on-device runtime. Default for stored messages. */
    Sent,

    /** Send failed; the screen surfaces a retry affordance. */
    Failed,
}

/**
 * Metadata footer rendered beneath a [ChatMessage] bubble.
 *
 * Stored separately from [ChatContent] so the same content (text, markdown,
 * tool call, …) can carry the right metadata without sealing the content
 * hierarchy.
 *
 * @property timestamp pre-formatted local time string (e.g. "12:42"). Catalog
 * components never format dates themselves — the screen passes a localised
 * string.
 * @property model optional model display name (e.g. "Gemma 2 2B"); shown for
 * assistant messages only.
 * @property tokens optional token count attributed to this message; rendered
 * as a `MonoSm` chip when non-null.
 * @property status delivery status driving the trailing glyph.
 */
data class ChatMetadata(
    val timestamp: String,
    val model: String? = null,
    val tokens: Int? = null,
    val status: ChatMessageStatus = ChatMessageStatus.Sent,
)
