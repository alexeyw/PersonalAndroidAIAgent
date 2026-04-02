package ai.agent.android.presentation.ui.taskmonitor

/**
 * Represents the type of a task in the monitoring screen.
 */
enum class TaskType {
    SESSION,
    BACKGROUND_WORK
}

/**
 * Represents the current status of a task.
 */
enum class TaskStatus {
    RUNNING,
    QUEUED,
    FAILED,
    COMPLETED
}

/**
 * Filter types for the task monitoring list.
 *
 * @property displayName The text to display in the UI for this filter.
 */
enum class TaskFilterType(val displayName: String) {
    ALL("All"),
    ACTIVE("Active"),
    BACKGROUND("Background"),
    COMPLETED("Completed")
}

/**
 * Data model for a single task card.
 *
 * @property id Unique identifier for the task or session.
 * @property title The display title.
 * @property status The current execution status.
 * @property progress The progress from 0 to 1, or null if indeterminate/not applicable.
 * @property type The type of task (session vs background work).
 */
data class TaskItem(
    val id: String,
    val title: String,
    val status: TaskStatus,
    val progress: Float?,
    val type: TaskType
)

/**
 * UI State for the Task Monitor screen.
 *
 * @property tasks The filtered list of tasks to display.
 * @property filter The currently selected filter.
 * @property isLoading True if initial data is still loading.
 */
data class TaskMonitorState(
    val tasks: List<TaskItem> = emptyList(),
    val filter: TaskFilterType = TaskFilterType.ALL,
    val isLoading: Boolean = true
)
