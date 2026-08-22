package app.knotwork.android.presentation.ui.prompts

import app.knotwork.android.domain.models.NodeType
import app.knotwork.android.domain.models.PromptPackCandidate
import app.knotwork.android.domain.models.PromptPackImportNotes
import app.knotwork.android.domain.models.PromptPackParseError
import app.knotwork.android.domain.models.PromptPreset
import app.knotwork.android.domain.prompt.PromptSegment
import app.knotwork.android.domain.usecases.promptpack.ExportedPromptPack
import app.knotwork.android.presentation.ui.common.UiText

/**
 * Represents the UI state for the Prompt Library screen. This screen surfaces
 * [PromptPreset]s (bundled + user).
 *
 * @property bundledPresets Bundled, read-only presets that ship inside the
 *   APK (`assets/presets/prompts`). Surfaced grouped by [NodeType].
 * @property userPresets User-saved presets persisted in Room
 *   (`prompt_presets` table). Mutable through the editor sheet.
 * @property isLoading Whether the initial catalogue load is in progress.
 * @property errorMessage Any error message to display.
 * @property availableVariables Tokens (`$KEY`) of every prompt variable
 *   currently registered in the DI graph. Drives the chip row in the
 *   prompt editor.
 * @property previewState Current state of the prompt-preview bottom sheet.
 * @property selectedCategory Currently-selected category tab; the category
 *   string is a [NodeType] name (e.g. `"LITE_RT"`). `null` means "first
 *   available".
 * @property editorDraft Editor draft when the bottom sheet is open; `null`
 *   when closed.
 * @property importDialog The import outcome awaiting acknowledgement or a
 *   decision; `null` when no dialog is open.
 * @property snackbar One-shot confirmation for the outcomes that need no
 *   decision (imported, exported, nothing changed, unreadable file); `null`
 *   when nothing is pending.
 * @property pendingExport A rendered prompt waiting for the create-document
 *   picker to return a destination; `null` when no export is in flight.
 */
data class PromptLibraryUiState(
    val bundledPresets: List<PromptPreset> = emptyList(),
    val userPresets: List<PromptPreset> = emptyList(),
    val isLoading: Boolean = false,
    val errorMessage: UiText? = null,
    val availableVariables: List<String> = emptyList(),
    val previewState: PromptPreviewState = PromptPreviewState.Hidden,
    val selectedCategory: String? = null,
    val editorDraft: PromptEditorDraft? = null,
    val importDialog: PromptImportDialog? = null,
    val snackbar: PromptLibrarySnackbar? = null,
    val pendingExport: ExportedPromptPack? = null,
)

/**
 * A one-shot confirmation shown as a snackbar.
 *
 * @property text What happened.
 * @property showCategory Category tab to switch to when the user taps the
 *   action, or `null` for a snackbar with no action. A prompt imported into a
 *   category the user is not looking at is otherwise invisible, which is the
 *   only reason this action exists.
 */
data class PromptLibrarySnackbar(val text: UiText, val showCategory: String? = null)

/**
 * The import dialog currently open.
 *
 * Three shapes, one visual family: something was imported and there is a
 * caveat to state, nothing could be imported, or a question has to be
 * answered before anything happens.
 */
sealed class PromptImportDialog {

    /**
     * The prompt landed, and something about the file has to be said —
     * a version this build does not emit, keys it does not know, or a
     * request for a capability a prompt cannot grant.
     *
     * @property presetName Display name of the imported prompt.
     * @property notes What was left out.
     */
    data class Reported(val presetName: String, val notes: PromptPackImportNotes) : PromptImportDialog()

    /**
     * Nothing was imported.
     *
     * @property cause Which recognised failure occurred.
     */
    data class Failed(val cause: PromptPackParseError) : PromptImportDialog()

    /**
     * A prompt with this id is already saved and differs from the file.
     *
     * @property candidate The prompt as read from the file.
     * @property existingName Display name of the prompt already saved.
     * @property notes Anything that also has to be reported once the user
     *   decides, carried across the question rather than shown before it.
     */
    data class Collision(
        val candidate: PromptPackCandidate,
        val existingName: String,
        val notes: PromptPackImportNotes?,
    ) : PromptImportDialog()
}

/**
 * Working copy of the preset being edited in the bottom sheet. Lives on
 * the UI state (not the catalog ViewState) so it survives configuration
 * changes — the catalog `PromptEditorState` is recomputed from this
 * each render.
 *
 * @property id Stable preset id when editing an existing user preset, or
 *   `null` when creating a brand-new draft (the use case generates a UUID
 *   on first save).
 * @property name Display name.
 * @property category Target [NodeType.name]. Rendered as the form's category
 *   value; the user can switch it via the catalog's category dropdown.
 * @property body Raw `systemPrompt` template — may carry $-prefixed
 *   placeholder tokens.
 * @property description Free-form description (persisted but not edited from
 *   the current catalog editor — preserved on edit).
 * @property tags Tags preserved on edit; not exposed in the current editor
 *   surface but kept in the draft so a future richer editor can edit them.
 */
data class PromptEditorDraft(
    val id: String? = null,
    val name: String = "",
    val category: String = "",
    val body: String = "",
    val description: String = "",
    val tags: List<String> = emptyList(),
)

/**
 * State of the prompt-preview bottom sheet for the Prompt Library editor. Mirrors the
 * orchestrator's `PromptPreviewState` but lives in this package to keep the prompts UI
 * self-contained — both states intentionally share the same shape so the bottom-sheet
 * composable can be reused as-is.
 */
sealed interface PromptPreviewState {

    /** Sheet is closed, no preview is being computed. */
    data object Hidden : PromptPreviewState

    /** A preview was requested and the engine is currently rendering segments. */
    data object Loading : PromptPreviewState

    /**
     * Segments have been produced and the sheet should be shown. [segments] is the
     * ordered output of `PromptTemplateEngine.renderSegments`.
     */
    data class Ready(val segments: List<PromptSegment>) : PromptPreviewState
}
