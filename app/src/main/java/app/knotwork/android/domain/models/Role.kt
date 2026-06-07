package app.knotwork.android.domain.models

/**
 * Represents the role of the message sender in a chat session.
 */
enum class Role {
    /**
     * The human user.
     */
    USER,

    /**
     * The AI agent.
     */
    AGENT,

    /**
     * A system prompt or internal message.
     */
    SYSTEM,
}
