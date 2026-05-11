package ai.agent.android.domain.models

/**
 * Defines the priority of a task in the execution queue.
 */
enum class TaskPriority {
    /** Active, user-visible chat — drained first to keep the foreground responsive. */
    HIGH,

    /** Background chats and standard agent requests. */
    NORMAL,

    /** Scheduled background tasks that may be deferred under load. */
    LOW,
}
