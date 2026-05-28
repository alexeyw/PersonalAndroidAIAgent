package ai.agent.android.presentation.ui.prompts

import ai.agent.android.domain.models.NodeType
import ai.agent.android.domain.models.PromptPreset
import ai.agent.android.domain.prompt.PromptSegment
import ai.agent.android.presentation.ui.common.UiText

/**
 * Represents the UI state for the Prompt Library screen — Phase 24 / Task 5
 * rewires this screen to surface [PromptPreset]s (bundled + user) instead of
 * the legacy [ai.agent.android.domain.models.PromptTemplate] catalogue.
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
    /** Live search query (case-insensitive substring match on `PromptPreset.name`). */
    val searchQuery: String = "",
    /** `true` when the inline search field is expanded under the tab bar. */
    val searchOpen: Boolean = false,
)

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
