package ai.agent.android.domain.models

/**
 * Defines the priority of a task in the execution queue.
 */
enum class TaskPriority {
    HIGH,   // Used for active open chats
    NORMAL, // Used for background chats or standard requests
    LOW     // Used for scheduled background tasks
}
