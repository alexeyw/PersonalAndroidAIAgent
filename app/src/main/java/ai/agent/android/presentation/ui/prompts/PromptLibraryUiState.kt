package ai.agent.android.presentation.ui.prompts

import ai.agent.android.domain.models.PromptTemplate
import ai.agent.android.domain.prompt.PromptSegment
import ai.agent.android.presentation.ui.common.UiText

/**
 * Represents the UI state for the Prompt Library screen.
 *
 * @property promptTemplates List of all saved prompt templates.
 * @property isLoading Whether the list is currently loading.
 * @property errorMessage Any error message to display.
 * @property availableVariables Tokens (`$KEY`) of every prompt variable currently
 * registered in the DI graph. Drives the chip row in the prompt editor.
 * @property previewState Current state of the prompt-preview bottom sheet.
 */
data class PromptLibraryUiState(
    val promptTemplates: List<PromptTemplate> = emptyList(),
    val isLoading: Boolean = false,
    val errorMessage: UiText? = null,
    val availableVariables: List<String> = emptyList(),
    val previewState: PromptPreviewState = PromptPreviewState.Hidden,
    /** Currently-selected category tab; `null` means "first available". */
    val selectedCategory: String? = null,
    /** Editor draft when the bottom sheet is open; `null` when closed. */
    val editorDraft: PromptEditorDraft? = null,
)

/**
 * Working copy of the prompt being edited in the bottom sheet. Lives on
 * the UI state (not the catalog ViewState) so it survives configuration
 * changes — the catalog `PromptEditorState` is recomputed from this
 * each render.
 *
 * @property id `null` for a new prompt draft, non-null when editing an
 * existing template.
 */
data class PromptEditorDraft(
    val id: Long? = null,
    val name: String = "",
    val category: String = "",
    val body: String = "",
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
