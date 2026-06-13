package app.knotwork.android.presentation.ui.files

import app.knotwork.android.domain.models.WorkspaceFile
import app.knotwork.android.domain.models.WorkspaceTextPreview
import app.knotwork.android.domain.models.WorkspaceUsage

/**
 * UI state of the Files screen, owned by [FilesViewModel] and projected to the
 * catalog `FilesViewState` by `FilesScreen`.
 *
 * Holds the raw domain data (the workspace listing and usage) plus the screen's
 * transient interaction state (selection, the open preview, and the two
 * confirmation dialogs). One-shot actions that need an Android `Context` — SAF
 * pickers, the share sheet, snackbars — travel as [FilesEvent]s, not state.
 *
 * @property files The workspace listing, path-sorted; empty drives the empty
 *   state.
 * @property usage Storage-budget snapshot for the quota indicator.
 * @property loading `true` during the very first load, before any listing has
 *   arrived.
 * @property refreshing `true` while a pull-to-refresh / manual refresh is in
 *   flight.
 * @property loadFailed `true` when the last listing attempt failed (drives the
 *   error state).
 * @property selectionMode `true` when the multi-select bar is active.
 * @property selectedPaths Paths currently selected (only meaningful in selection
 *   mode).
 * @property preview The open text preview (domain slice + its path), or `null`.
 * @property pendingDelete Paths queued for the delete-confirmation dialog, or
 *   `null` when no dialog is showing.
 * @property collision The pending import collision (display data), or `null`.
 */
data class FilesUiState(
    val files: List<WorkspaceFile> = emptyList(),
    val usage: WorkspaceUsage = WorkspaceUsage(usedBytes = 0L, limitBytes = 0L),
    val loading: Boolean = true,
    val refreshing: Boolean = false,
    val loadFailed: Boolean = false,
    val selectionMode: Boolean = false,
    val selectedPaths: Set<String> = emptySet(),
    val preview: FilePreviewState? = null,
    val pendingDelete: List<String>? = null,
    val collision: CollisionState? = null,
)

/**
 * A currently-open file preview.
 *
 * @property path The previewed file's workspace-relative path.
 * @property preview The bounded text slice returned by the workspace.
 */
data class FilePreviewState(val path: String, val preview: WorkspaceTextPreview)

/**
 * Display data for the import name-collision dialog.
 *
 * @property name The colliding basename.
 * @property keepBothName The free `name (N).ext` variant the Keep-both option
 *   would use.
 */
data class CollisionState(val name: String, val keepBothName: String)

/**
 * One-shot effects emitted by [FilesViewModel] for `FilesScreen` to carry out,
 * since they require an Android `Context` / activity-result launcher.
 */
sealed interface FilesEvent {
    /** Launch the document picker to import a file. */
    data object LaunchImport : FilesEvent

    /**
     * Launch the "create document" picker to save a workspace file out.
     *
     * @property suggestedName Default file name offered to the picker.
     */
    data class LaunchSaveAs(val suggestedName: String) : FilesEvent

    /**
     * Stage the given workspace files and offer them to the system share sheet.
     *
     * @property paths Workspace-relative paths to share.
     */
    data class ShareFiles(val paths: List<String>) : FilesEvent

    /**
     * Show a transient message.
     *
     * @property kind Which message to show (the screen maps it to a string).
     */
    data class ShowMessage(val kind: FilesMessage) : FilesEvent
}

/** Transient messages the Files screen can surface in a snackbar. */
enum class FilesMessage {
    /** An import failed (quota, too large, or I/O). */
    ImportFailed,

    /** A save-as / export failed. */
    ExportFailed,

    /** A preview could not be opened (binary or I/O). */
    PreviewFailed,

    /** Some files in a bulk delete could not be removed. */
    DeletePartial,
}
