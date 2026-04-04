package ai.agent.android.presentation.ui.prompts

import ai.agent.android.domain.models.PromptTemplate

/**
 * Represents the UI state for the Prompt Library screen.
 * 
 * @property promptTemplates List of all saved prompt templates.
 * @property isLoading Whether the list is currently loading.
 * @property errorMessage Any error message to display.
 */
data class PromptLibraryUiState(
    val promptTemplates: List<PromptTemplate> = emptyList(),
    val isLoading: Boolean = false,
    val errorMessage: String? = null
)
