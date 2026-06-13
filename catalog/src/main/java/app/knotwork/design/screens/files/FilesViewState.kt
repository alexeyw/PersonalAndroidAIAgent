package app.knotwork.design.screens.files

/**
 * High-level visual state of the Files screen body. Overlays (preview sheet,
 * dialogs) and the selection mode are driven by separate fields on
 * [FilesViewState] so, for example, opening a preview while a search-free list
 * is shown does not change the underlying body.
 */
enum class FilesVisualState {
    /** Workspace has no files — the teaching empty state is shown. */
    Empty,

    /** Workspace has at least one file — the list + quota header are shown. */
    Populated,

    /** Listing the workspace failed (I/O) — the error state is shown. */
    Error,
}

/** Whether a file row is a previewable text file or an opaque binary blob. */
enum class FileKind {
    /** UTF-8 text — previewable in the monospace sheet. */
    Text,

    /** Binary — not previewable; only share / save-as / delete apply. */
    Binary,
}

/** Tone ramp for the quota indicator as the workspace fills up. */
enum class QuotaTone {
    /** Plenty of headroom — neutral. */
    Normal,

    /** Near the limit (≥ ~90%) — amber. */
    Warn,

    /** At/over the limit — red; the agent's writes are being refused. */
    Over,
}

/**
 * One file in the workspace listing, projected for display. The path prefix and
 * basename are pre-split so the row can dim the directory and bold the name,
 * keeping the recursive layout legible in a flat list.
 *
 * @property path The workspace-relative path; also the stable list key.
 * @property dir The directory prefix with a trailing slash (dimmed), or empty
 *   for a root-level file.
 * @property name The basename (emphasised).
 * @property sizeLabel Pre-formatted size, e.g. `"12.4 KB"`.
 * @property dateLabel Pre-formatted relative modified time, e.g. `"2h"`.
 * @property kind Text (previewable) or binary.
 * @property isFresh `true` to flag a recently-written file with a "new" marker.
 */
data class FileRowItem(
    val path: String,
    val dir: String,
    val name: String,
    val sizeLabel: String,
    val dateLabel: String,
    val kind: FileKind,
    val isFresh: Boolean = false,
)

/**
 * The header quota indicator: a file count, a `used / limit` line, a fill bar,
 * and a tone that ramps as the workspace fills.
 *
 * @property count Number of files in the workspace.
 * @property usageText Pre-formatted usage line, e.g. `"1.5 MB / 64 MB"` or
 *   `"58.6 MB / 64 MB · 92%"`.
 * @property fraction Fill fraction in `[0, 1]` for the bar.
 * @property tone Drives the bar/text colour.
 * @property full `true` to show the "workspace full" warning banner under the bar.
 */
data class QuotaView(
    val count: Int,
    val usageText: String,
    val fraction: Float,
    val tone: QuotaTone = QuotaTone.Normal,
    val full: Boolean = false,
)

/**
 * Read-only preview of a text file shown in the bottom sheet.
 *
 * @property path The previewed file's workspace-relative path.
 * @property dir Directory prefix (dimmed) of the title.
 * @property name Basename of the title.
 * @property sizeLabel The file's full size, e.g. `"48.1 KB"`.
 * @property body The (possibly truncated) text to display, monospace.
 * @property truncated `true` when [body] is only the leading slice of the file.
 * @property shownBytesLabel When [truncated], the size of the shown slice, e.g.
 *   `"64 KB"`; ignored otherwise.
 */
data class PreviewView(
    val path: String,
    val dir: String,
    val name: String,
    val sizeLabel: String,
    val body: String,
    val truncated: Boolean = false,
    val shownBytesLabel: String = "",
)

/**
 * Confirmation request for a single or bulk delete.
 *
 * @property names Basenames to delete (listed for bulk).
 * @property count Number of files; `1` renders the single-file copy.
 */
data class DeleteDialogView(val names: List<String>, val count: Int)

/**
 * "Name already exists" request shown when an import collides with a file.
 *
 * @property name The colliding basename.
 * @property keepBothName Preview of the free `name (1).ext` variant offered by
 *   the Keep-both option.
 */
data class CollisionView(val name: String, val keepBothName: String)

/**
 * Immutable input to `FilesContent` — the Files screen over the agent workspace.
 *
 * @property visualState Empty / Populated / Error body selector.
 * @property quota Header quota indicator (always present except in [Error]).
 * @property files The file rows, path-sorted.
 * @property selectionMode `true` when the contextual multi-select bar is active.
 * @property selectedPaths Paths currently selected (only meaningful in selection
 *   mode).
 * @property refreshing `true` to show the pull-to-refresh spinner.
 * @property preview Non-null shows the preview bottom sheet.
 * @property deleteDialog Non-null shows the delete-confirmation dialog.
 * @property collisionDialog Non-null shows the import-collision dialog.
 */
data class FilesViewState(
    val visualState: FilesVisualState = FilesVisualState.Empty,
    val quota: QuotaView = QuotaView(count = 0, usageText = "", fraction = 0f),
    val files: List<FileRowItem> = emptyList(),
    val selectionMode: Boolean = false,
    val selectedPaths: Set<String> = emptySet(),
    val refreshing: Boolean = false,
    val preview: PreviewView? = null,
    val deleteDialog: DeleteDialogView? = null,
    val collisionDialog: CollisionView? = null,
)

/**
 * Callback bundle for `FilesContent`. All validation, I/O and SAF launching live
 * in the host `FilesViewModel` / `FilesScreen`; the catalog only signals intent.
 *
 * @property onBack Navigate back.
 * @property onRefresh Pull-to-refresh / refresh action.
 * @property onImport Launch the system file picker to import a file.
 * @property onRowClick Tap a row: preview a text file, or toggle it in selection
 *   mode.
 * @property onRowLongClick Long-press a row: enter selection mode and select it.
 * @property onSelectAll Select every file (selection mode).
 * @property onExitSelection Leave selection mode.
 * @property onDeleteSelected Request deletion of the current selection.
 * @property onShareSelected Share the current selection.
 * @property onFilePreview Preview a single text file by path.
 * @property onFileShare Share a single file by path.
 * @property onFileSaveAs Save a single file out via the document picker.
 * @property onFileDelete Request deletion of a single file by path.
 * @property onClosePreview Dismiss the preview sheet.
 * @property onDeleteConfirm Confirm the pending delete.
 * @property onDeleteCancel Dismiss the delete dialog.
 * @property onCollisionKeepBoth Import under the free `name (N)` variant.
 * @property onCollisionReplace Overwrite the existing file.
 * @property onCollisionCancel Abandon the colliding import.
 * @property onErrorRetry Retry listing after an error.
 */
class FilesCallbacks(
    val onBack: () -> Unit = {},
    val onRefresh: () -> Unit = {},
    val onImport: () -> Unit = {},
    val onRowClick: (path: String) -> Unit = {},
    val onRowLongClick: (path: String) -> Unit = {},
    val onSelectAll: () -> Unit = {},
    val onExitSelection: () -> Unit = {},
    val onDeleteSelected: () -> Unit = {},
    val onShareSelected: () -> Unit = {},
    val onFilePreview: (path: String) -> Unit = {},
    val onFileShare: (path: String) -> Unit = {},
    val onFileSaveAs: (path: String) -> Unit = {},
    val onFileDelete: (path: String) -> Unit = {},
    val onClosePreview: () -> Unit = {},
    val onDeleteConfirm: () -> Unit = {},
    val onDeleteCancel: () -> Unit = {},
    val onCollisionKeepBoth: () -> Unit = {},
    val onCollisionReplace: () -> Unit = {},
    val onCollisionCancel: () -> Unit = {},
    val onErrorRetry: () -> Unit = {},
)

/** Convenience factory returning a callbacks bundle that ignores every event. */
fun noopFilesCallbacks(): FilesCallbacks = FilesCallbacks()
