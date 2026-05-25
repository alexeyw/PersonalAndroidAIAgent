package app.knotwork.design.screens.taskmonitor

/** Visual variant of the Task Monitor surface. */
enum class TaskMonitorVisualState {
    Loading,
    Empty,
    Default,
    Error,
}

/**
 * Task lifecycle stage as rendered by the trailing status pill.
 *
 * Mirrors the app-side `TaskStatus` enum but lives in the catalog so the
 * catalog package stays Android-resource-free.
 */
enum class TaskRowStatus { Queued, Running, Success, Cancelled, Failed }

/**
 * Top-level filter chip applied to the list.
 */
enum class TaskFilterKind(val displayName: String) {
    All(displayName = "All"),
    Active(displayName = "Active"),
    Background(displayName = "Background"),
    Completed(displayName = "Completed"),
}

/**
 * One task surfaced as a row.
 *
 * @property id stable identifier (typically the WorkManager request id or
 * chat session id).
 * @property title display label (chat title or worker name).
 * @property subtitle optional mono subtitle (typically the pipeline stage).
 * @property status trailing status pill driver.
 * @property progress 0..1 fraction shown as a determinate bar when the
 * status is [TaskRowStatus.Running]; null hides the bar (indeterminate
 * is rendered as a steady accent fill).
 * @property isCancellable when true, the row supports swipe-to-cancel.
 */
data class TaskMonitorRow(
    val id: String,
    val title: String,
    val subtitle: String? = null,
    val status: TaskRowStatus,
    val progress: Float? = null,
    val isCancellable: Boolean = false,
)

/**
 * Detail payload populating the `ModalBottomSheet` opened on row tap.
 *
 * @property logs human-readable log lines (mono).
 */
data class TaskMonitorDetail(
    val id: String,
    val title: String,
    val subtitle: String?,
    val status: TaskRowStatus,
    val logs: List<String>,
)

/**
 * Top-level immutable input to `TaskMonitorContent`.
 */
data class TaskMonitorViewState(
    val visualState: TaskMonitorVisualState,
    val filter: TaskFilterKind = TaskFilterKind.All,
    val rows: List<TaskMonitorRow> = emptyList(),
    val expandedDetail: TaskMonitorDetail? = null,
    val errorMessage: String? = null,
) {
    init {
        require((visualState == TaskMonitorVisualState.Error) == (errorMessage != null)) {
            "errorMessage must be non-null iff visualState == Error"
        }
    }
}

/** One-shot callbacks consumed by `TaskMonitorContent`. */
@Suppress("LongParameterList")
class TaskMonitorCallbacks(
    val onBack: () -> Unit = {},
    val onFilterChanged: (TaskFilterKind) -> Unit = {},
    val onRowClick: (String) -> Unit = {},
    val onRowCancel: (String) -> Unit = {},
    val onDetailDismiss: () -> Unit = {},
    val onDetailOpenChat: (String) -> Unit = {},
    val onRetry: () -> Unit = {},
)

/** Convenience factory returning a no-op callback bundle. */
fun noopTaskMonitorCallbacks(): TaskMonitorCallbacks = TaskMonitorCallbacks()
