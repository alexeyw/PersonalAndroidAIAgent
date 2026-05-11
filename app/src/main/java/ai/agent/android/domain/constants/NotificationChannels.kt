package ai.agent.android.domain.constants

/**
 * Canonical registry of every Android `NotificationChannel` id used by the app.
 *
 * Channel ids are persisted by the system: once registered, they survive process death
 * and uninstalls (until the user deletes app data). Mistyping a channel id at any one of
 * the registration / posting call sites silently produces an "Other" / "Miscellaneous"
 * channel that bypasses the user's importance settings. Centralising the ids here keeps
 * every Builder, channel-creation call, and (future) `cancelChannel` lookup consistent.
 *
 * Lives in `domain/constants` because both the data-layer foreground service and the
 * presentation-layer approval manager need to reference the same ids; the file itself
 * carries no Android imports, so the domain-layer "no Android SDK" rule is preserved.
 *
 * Wire form (the string the system stores) is intentionally preserved as-is so this
 * refactor does not invalidate already-registered channels on user devices.
 */
object NotificationChannels {
    /** Channel for the long-running foreground service notification (status, "Agent is thinking…"). */
    const val AGENT_FOREGROUND: String = "AgentForegroundServiceChannel"

    /** High-importance channel for "approve / deny tool execution" prompts (Human-in-the-loop). */
    const val AGENT_APPROVAL: String = "AgentApprovalChannel"
}
