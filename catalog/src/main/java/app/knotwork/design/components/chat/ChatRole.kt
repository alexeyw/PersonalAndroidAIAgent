package app.knotwork.design.components.chat

/**
 * Conversational role of a [ChatMessage] sender in the Knotwork chat surface.
 *
 * Catalog-local on purpose — the `:app` module owns the analogous
 * `ai.agent.android.domain.models.Role` enum; the design system must not
 * depend on `domain` (`:catalog` is consumed by `:app`, not the other way
 * around). Screens map between the two enums at the presentation boundary.
 */
enum class ChatRole {
    /** Human user. Rendered on the trailing side with the user bubble palette. */
    User,

    /** AI agent. Rendered on the leading side with the assistant bubble palette. */
    Assistant,

    /** System message (errors, status announcements). Rendered centred with no bubble. */
    System,

    /** Tool invocation surfaced inline. Rendered as a mono `ToolCall` tile. */
    Tool,
}
