package app.knotwork.android.presentation.ui.files

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.knotwork.android.domain.models.WorkspaceResult
import app.knotwork.android.domain.usecases.workspace.DeleteWorkspaceFilesUseCase
import app.knotwork.android.domain.usecases.workspace.ExportWorkspaceFileUseCase
import app.knotwork.android.domain.usecases.workspace.ImportFileToWorkspaceUseCase
import app.knotwork.android.domain.usecases.workspace.ImportMode
import app.knotwork.android.domain.usecases.workspace.ListWorkspaceUseCase
import app.knotwork.android.domain.usecases.workspace.PreviewWorkspaceFileUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import timber.log.Timber
import java.io.InputStream
import java.io.OutputStream
import javax.inject.Inject

/**
 * ViewModel for the Files screen — the user-facing window over the agent
 * workspace.
 *
 * Owns the workspace listing + quota, the transient selection / preview / dialog
 * state, and the orchestration of the file operations. Operations that need an
 * Android `Context` (SAF pickers, the share sheet) are delegated to `FilesScreen`
 * via the [events] flow; the actual byte movement always flows through the
 * workspace use cases so containment and quotas are enforced in one place.
 *
 * @property listUseCase Loads the workspace listing + usage.
 * @property previewUseCase Reads a bounded text preview.
 * @property deleteUseCase Deletes one or more files.
 * @property importUseCase Imports an external document with a collision policy.
 * @property exportUseCase Streams a file out (save-as / share staging).
 */
@HiltViewModel
class FilesViewModel @Inject constructor(
    private val listUseCase: ListWorkspaceUseCase,
    private val previewUseCase: PreviewWorkspaceFileUseCase,
    private val deleteUseCase: DeleteWorkspaceFilesUseCase,
    private val importUseCase: ImportFileToWorkspaceUseCase,
    private val exportUseCase: ExportWorkspaceFileUseCase,
) : ViewModel() {

    private val _uiState = MutableStateFlow(FilesUiState())
    val uiState: StateFlow<FilesUiState> = _uiState.asStateFlow()

    private val _events = MutableSharedFlow<FilesEvent>(extraBufferCapacity = EVENT_BUFFER)
    val events: SharedFlow<FilesEvent> = _events.asSharedFlow()

    /** A picked-but-not-yet-imported document, kept so a collision can be re-resolved. */
    private var pendingImport: PendingImport? = null

    /** The path queued for a save-as destination chosen via the document picker. */
    private var pendingSavePath: String? = null

    init {
        refresh()
    }

    /** (Re)loads the workspace listing and usage. */
    fun refresh() {
        viewModelScope.launch {
            _uiState.update { it.copy(refreshing = !it.loading) }
            when (val result = listUseCase()) {
                is WorkspaceResult.Success -> _uiState.update {
                    it.copy(
                        files = result.value.files,
                        usage = result.value.usage,
                        loading = false,
                        refreshing = false,
                        loadFailed = false,
                    )
                }
                is WorkspaceResult.Failure -> {
                    Timber.w("Workspace listing failed: %s", result.error)
                    _uiState.update { it.copy(loading = false, refreshing = false, loadFailed = true) }
                }
            }
        }
    }

    // ── Row interactions ──────────────────────────────────────────────────

    /** Tap a row: toggle in selection mode, otherwise preview a text file. */
    fun onRowClick(path: String) {
        if (_uiState.value.selectionMode) {
            toggleSelection(path)
            return
        }
        val file = _uiState.value.files.firstOrNull { it.relativePath == path } ?: return
        if (file.isText) openPreview(path)
    }

    /** Long-press a row: enter selection mode with this row selected. */
    fun onRowLongClick(path: String) {
        _uiState.update { it.copy(selectionMode = true, selectedPaths = it.selectedPaths + path) }
    }

    private fun toggleSelection(path: String) {
        _uiState.update {
            val next = if (path in it.selectedPaths) it.selectedPaths - path else it.selectedPaths + path
            // Leaving the last item deselected exits selection mode, matching MemoryScreen.
            if (next.isEmpty()) {
                it.copy(
                    selectionMode = false,
                    selectedPaths = emptySet(),
                )
            } else {
                it.copy(selectedPaths = next)
            }
        }
    }

    /** Select every listed file. */
    fun selectAll() {
        _uiState.update { it.copy(selectedPaths = it.files.map { f -> f.relativePath }.toSet()) }
    }

    /** Leave selection mode, clearing the selection. */
    fun exitSelection() {
        _uiState.update { it.copy(selectionMode = false, selectedPaths = emptySet()) }
    }

    // ── Preview ───────────────────────────────────────────────────────────

    private fun openPreview(path: String) {
        viewModelScope.launch {
            when (val result = previewUseCase(path)) {
                is WorkspaceResult.Success ->
                    _uiState.update { it.copy(preview = FilePreviewState(path = path, preview = result.value)) }
                is WorkspaceResult.Failure -> {
                    Timber.w("Workspace preview failed for %s: %s", path, result.error)
                    emit(FilesEvent.ShowMessage(FilesMessage.PreviewFailed))
                }
            }
        }
    }

    /** Dismiss the preview sheet. */
    fun closePreview() {
        _uiState.update { it.copy(preview = null) }
    }

    // ── Delete ────────────────────────────────────────────────────────────

    /** Request deletion of a single file (opens the confirm dialog). */
    fun requestDelete(path: String) {
        _uiState.update { it.copy(pendingDelete = listOf(path)) }
    }

    /** Request deletion of the current selection (opens the confirm dialog). */
    fun requestDeleteSelected() {
        val selected = _uiState.value.selectedPaths.toList()
        if (selected.isNotEmpty()) _uiState.update { it.copy(pendingDelete = selected) }
    }

    /** Dismiss the delete-confirmation dialog without deleting. */
    fun cancelDelete() {
        _uiState.update { it.copy(pendingDelete = null) }
    }

    /** Confirm and execute the pending delete. */
    fun confirmDelete() {
        val targets = _uiState.value.pendingDelete ?: return
        viewModelScope.launch {
            val summary = deleteUseCase(targets)
            // Closing the dialog and any open preview of a now-deleted file, then refreshing.
            _uiState.update {
                val previewGone = it.preview?.path in targets
                it.copy(
                    pendingDelete = null,
                    selectionMode = false,
                    selectedPaths = emptySet(),
                    preview = if (previewGone) null else it.preview,
                )
            }
            if (!summary.allSucceeded) emit(FilesEvent.ShowMessage(FilesMessage.DeletePartial))
            refresh()
        }
    }

    // ── Import ────────────────────────────────────────────────────────────

    /** Ask the screen to launch the document picker. */
    fun requestImport() {
        emit(FilesEvent.LaunchImport)
    }

    /**
     * A document was picked. Decides between importing directly and prompting for
     * a name collision, keeping the re-openable stream factory so the user's
     * choice can be honoured without re-picking.
     *
     * @param displayName The picker's display name for the document.
     * @param openStream Re-openable factory for the document's bytes.
     */
    fun onImportPicked(displayName: String, openStream: () -> InputStream?) {
        val name = ImportFileToWorkspaceUseCase.sanitize(displayName)
        pendingImport = PendingImport(name = name, openStream = openStream)
        val existing = _uiState.value.files.map { it.relativePath }.toSet()
        if (name in existing) {
            _uiState.update {
                it.copy(
                    collision = CollisionState(
                        name = name,
                        keepBothName = ImportFileToWorkspaceUseCase.freeName(name, existing),
                    ),
                )
            }
        } else {
            performImport(ImportMode.CreateOrFail)
        }
    }

    /** Resolve a collision by importing under a free `name (N)` variant. */
    fun resolveCollisionKeepBoth() {
        _uiState.update { it.copy(collision = null) }
        performImport(ImportMode.KeepBoth)
    }

    /** Resolve a collision by overwriting the existing file. */
    fun resolveCollisionReplace() {
        _uiState.update { it.copy(collision = null) }
        performImport(ImportMode.Overwrite)
    }

    /** Abandon a colliding import. */
    fun cancelCollision() {
        pendingImport = null
        _uiState.update { it.copy(collision = null) }
    }

    private fun performImport(mode: ImportMode) {
        val pending = pendingImport ?: return
        viewModelScope.launch {
            try {
                val stream = pending.openStream()
                if (stream == null) {
                    emit(FilesEvent.ShowMessage(FilesMessage.ImportFailed))
                    return@launch
                }
                val result = stream.use { importUseCase(pending.name, it, mode) }
                if (result is WorkspaceResult.Failure) {
                    Timber.w("Workspace import failed for %s: %s", pending.name, result.error)
                    emit(FilesEvent.ShowMessage(FilesMessage.ImportFailed))
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Timber.w(e, "Workspace import threw for %s", pending.name)
                emit(FilesEvent.ShowMessage(FilesMessage.ImportFailed))
            } finally {
                pendingImport = null
            }
            refresh()
        }
    }

    // ── Export (save-as + share staging) ──────────────────────────────────

    /** Ask the screen to launch the save-as document picker for a single file. */
    fun requestSaveAs(path: String) {
        pendingSavePath = path
        emit(FilesEvent.LaunchSaveAs(suggestedName = path.substringAfterLast('/')))
    }

    /**
     * Completes a save-as by streaming the previously-chosen file into [sink].
     * Suspends until the export finishes so the caller can keep [sink] open
     * (and close it) for the whole write. The caller owns and closes [sink].
     */
    suspend fun completeSaveAs(sink: OutputStream) {
        val path = pendingSavePath ?: return
        pendingSavePath = null
        if (!exportTo(path, sink)) emit(FilesEvent.ShowMessage(FilesMessage.ExportFailed))
    }

    /** Ask the screen to stage + share a single file. */
    fun requestShare(path: String) {
        emit(FilesEvent.ShareFiles(listOf(path)))
    }

    /** Ask the screen to stage + share the current selection. */
    fun requestShareSelected() {
        val selected = _uiState.value.selectedPaths.toList()
        if (selected.isNotEmpty()) emit(FilesEvent.ShareFiles(selected))
        exitSelection()
    }

    /**
     * Streams the workspace file at [path] into [sink] through the export use
     * case. Used by `FilesScreen` for both save-as and share staging.
     *
     * @return `true` on success, `false` on a typed failure or I/O error.
     */
    suspend fun exportTo(path: String, sink: OutputStream): Boolean = try {
        exportUseCase(path, sink) is WorkspaceResult.Success
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Timber.w(e, "Workspace export threw for %s", path)
        false
    }

    private fun emit(event: FilesEvent) {
        _events.tryEmit(event)
    }

    /** A picked document awaiting import; the stream factory can be re-opened per attempt. */
    private data class PendingImport(val name: String, val openStream: () -> InputStream?)

    private companion object {
        const val EVENT_BUFFER = 4
    }
}
