package ai.agent.android.presentation.ui.prompts

import ai.agent.android.R
import ai.agent.android.domain.models.PromptTemplate
import ai.agent.android.domain.prompt.PromptTemplateEngine
import ai.agent.android.domain.prompt.PromptVariableProvider
import ai.agent.android.domain.usecases.DeletePromptTemplateUseCase
import ai.agent.android.domain.usecases.GetPromptTemplatesUseCase
import ai.agent.android.domain.usecases.SavePromptTemplateUseCase
import ai.agent.android.presentation.ui.common.UiText
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * ViewModel for managing prompt templates.
 *
 * In addition to CRUD over saved templates, this ViewModel exposes the list of
 * available `$VARIABLE` tokens and a preview pipeline so the editor can show the
 * rendered prompt with substituted values without the user having to run a pipeline.
 */
@HiltViewModel
class PromptLibraryViewModel @Inject constructor(
    private val getPromptTemplatesUseCase: GetPromptTemplatesUseCase,
    private val savePromptTemplateUseCase: SavePromptTemplateUseCase,
    private val deletePromptTemplateUseCase: DeletePromptTemplateUseCase,
    private val promptTemplateEngine: PromptTemplateEngine,
    private val promptVariableProviders: Set<@JvmSuppressWildcards PromptVariableProvider>,
) : ViewModel() {

    private val _uiState = MutableStateFlow(
        PromptLibraryUiState(availableVariables = computeAvailableVariables(promptVariableProviders)),
    )
    val uiState: StateFlow<PromptLibraryUiState> = _uiState.asStateFlow()

    init {
        loadPrompts()
    }

    private fun loadPrompts() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            getPromptTemplatesUseCase()
                .catch { e ->
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            errorMessage =
                            e.message?.let { UiText.Dynamic(it) } ?: UiText(R.string.errors_generic_unexpected),
                        )
                    }
                }
                .collect { templates ->
                    _uiState.update { state ->
                        state.copy(isLoading = false, promptTemplates = templates, errorMessage = null)
                    }
                }
        }
    }

    /**
     * Saves a prompt template.
     */
    fun savePrompt(prompt: PromptTemplate) {
        viewModelScope.launch {
            try {
                savePromptTemplateUseCase(prompt)
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        errorMessage =
                        e.message?.let { UiText.Dynamic(it) } ?: UiText(R.string.errors_generic_unexpected),
                    )
                }
            }
        }
    }

    /**
     * Deletes a prompt template.
     */
    fun deletePrompt(id: Long) {
        viewModelScope.launch {
            try {
                deletePromptTemplateUseCase(id)
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        errorMessage =
                        e.message?.let { UiText.Dynamic(it) } ?: UiText(R.string.errors_generic_unexpected),
                    )
                }
            }
        }
    }

    /**
     * Renders [template] through [PromptTemplateEngine] and exposes the resulting
     * segments via [PromptLibraryUiState.previewState] for display in the bottom sheet.
     *
     * @param template raw prompt that may contain `$VARIABLE` placeholders.
     */
    fun requestPromptPreview(template: String) {
        _uiState.update { it.copy(previewState = PromptPreviewState.Loading) }
        viewModelScope.launch {
            val segments = promptTemplateEngine.renderSegments(
                template,
                promptVariableProviders.toList(),
            )
            _uiState.update { it.copy(previewState = PromptPreviewState.Ready(segments)) }
        }
    }

    /**
     * Closes the prompt-preview bottom sheet, returning the UI to its idle state.
     */
    fun dismissPromptPreview() {
        _uiState.update { it.copy(previewState = PromptPreviewState.Hidden) }
    }

    fun clearError() {
        _uiState.update { it.copy(errorMessage = null) }
    }

    private companion object {
        /**
         * See `OrchestratorViewModel.computeAvailableVariables` for the rationale: a
         * provider whose `key()` throws is silently skipped so a misbehaving DI binding
         * cannot empty the chip row.
         */
        private fun computeAvailableVariables(providers: Set<PromptVariableProvider>): List<String> = providers
            .mapNotNull { runCatching { it.key() }.getOrNull() }
            .distinct()
            .sorted()
            .map { "$$it" }
    }
}
