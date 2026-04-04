package ai.agent.android.presentation.ui.prompts

import ai.agent.android.domain.models.PromptTemplate
import ai.agent.android.domain.usecases.DeletePromptTemplateUseCase
import ai.agent.android.domain.usecases.GetPromptTemplatesUseCase
import ai.agent.android.domain.usecases.SavePromptTemplateUseCase
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
 */
@HiltViewModel
class PromptLibraryViewModel @Inject constructor(
    private val getPromptTemplatesUseCase: GetPromptTemplatesUseCase,
    private val savePromptTemplateUseCase: SavePromptTemplateUseCase,
    private val deletePromptTemplateUseCase: DeletePromptTemplateUseCase
) : ViewModel() {

    private val _uiState = MutableStateFlow(PromptLibraryUiState())
    val uiState: StateFlow<PromptLibraryUiState> = _uiState.asStateFlow()

    init {
        loadPrompts()
    }

    private fun loadPrompts() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            getPromptTemplatesUseCase()
                .catch { e ->
                    _uiState.update { it.copy(isLoading = false, errorMessage = e.message) }
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
                _uiState.update { it.copy(errorMessage = e.message) }
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
                _uiState.update { it.copy(errorMessage = e.message) }
            }
        }
    }

    fun clearError() {
        _uiState.update { it.copy(errorMessage = null) }
    }
}
