package ai.agent.android.presentation.ui.prompts

import ai.agent.android.domain.models.PromptTemplate
import ai.agent.android.domain.prompt.PromptSegment

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
    val errorMessage: String? = null,
    val availableVariables: List<String> = emptyList(),
    val previewState: PromptPreviewState = PromptPreviewState.Hidden,
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
