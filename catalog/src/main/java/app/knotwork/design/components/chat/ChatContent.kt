package app.knotwork.design.components.chat

/**
 * Status of a [ChatContent.ToolCall] surfaced inline in the chat surface.
 *
 * Drives the tile's left accent strip colour and the trailing glyph.
 */
enum class ToolCallStatus {
    /** Tool is currently executing. Renders an inline `KnotworkLoader` tail. */
    Running,

    /** Tool returned successfully. Renders a check glyph in `signalSuccess`. */
    Success,

    /** Tool errored. Renders the error glyph + message in `signalError`. */
    Failed,
}

/**
 * Sealed body of a [ChatMessage]. Every variant is rendered by [ChatMessage];
 * adding a new variant requires extending the `when` block there. The base
 * variants match the chat surface.
 *
 * Catalog API takes already-prepared strings (no JSON / Markdown parsing) so
 * `:catalog` stays self-contained — interpretation of JSON args, AST building
 * for Markdown, and tool-result formatting are presentation-layer concerns.
 */
sealed interface ChatContent {

    /**
     * Plain-text body. Rendered with `KnotworkTextStyles.BodyBase`.
     *
     * @property text user-visible text.
     */
    data class Text(val text: String) : ChatContent

    /**
     * Markdown body. Rendered as plain text in catalog v0 (no markdown
     * library dependency). The `:app` integration
     * upgrades this to a real renderer; the catalog API is forward-compatible
     * because callers already pass the raw source.
     *
     * @property source raw markdown source.
     */
    data class Markdown(val source: String) : ChatContent

    /**
     * HITL confirmation prompt — `HitlConfirmationCard` rendered inside the
     * assistant bubble. Typed-confirm input is hoisted to the screen via
     * [ChatMessage]'s `confirmation*` callbacks (see [ChatMessage]).
     *
     * @property model immutable payload describing the prompt.
     */
    data class Confirmation(val model: HitlConfirmationModel) : ChatContent

    /**
     * Clarification request — `ClarificationCard` rendered inside the
     * assistant bubble.
     *
     * @property model immutable payload describing the question + replies.
     */
    data class Clarification(val model: ClarificationCardModel) : ChatContent

    /**
     * Error tile — the message failed at the runtime layer (LLM error,
     * network error, validation failure). Renders with a `signalError`
     * border and an optional retry CTA.
     *
     * @property message user-visible error description.
     * @property retry optional retry action; when `null`, the retry button
     * is hidden.
     */
    data class Error(val message: String, val retry: (() -> Unit)? = null) : ChatContent

    /**
     * Tool invocation surfaced inline as a compact `mono` tile. Useful for
     * keeping the user informed about side-effecting tools (file reads,
     * MCP fetches, …) without dumping raw JSON into the chat.
     *
     * @property toolName fully-qualified tool id (e.g. `"fs.read_file"`).
     * @property argsJson already-serialised arguments string.
     * @property result tool result preview; `null` while [status] is
     * [ToolCallStatus.Running].
     * @property status execution status driving the tile accent.
     */
    data class ToolCall(val toolName: String, val argsJson: String, val result: String?, val status: ToolCallStatus) :
        ChatContent
}
