package app.knotwork.android.domain.constants

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

    /**
     * High-importance channel for "approve / deny" prompts on `SENSITIVE` /
     * `READ_ONLY` (the latter only when the user has globally opted into "ask on
     * every tool call") tool executions.
     */
    const val AGENT_APPROVAL: String = "AgentApprovalChannel"

    /**
     * Separate high-importance channel for `DESTRUCTIVE` approvals. Splitting the
     * channel lets the user tune visibility independently (e.g. allow heads-up only
     * for destructive actions) and shows a distinct icon / title in the shade so
     * irreversible operations are easy to spot among other approval prompts.
     */
    const val AGENT_APPROVAL_DESTRUCTIVE: String = "AgentApprovalDestructiveChannel"

    /**
     * Low-importance channel for informational pings when a backgrounded
     * pipeline run exceeds the long-running threshold. Backs the Settings →
     * Notifications → "Long-running tasks" toggle: the channel is created
     * once at app start and `LongRunningTaskNotifier` posts to it when the
     * user-controlled flag is on.
     */
    const val LONG_RUNNING_TASKS: String = "LongRunningTasksChannel"
}
