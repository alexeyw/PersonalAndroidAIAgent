package ai.agent.android.presentation.ui.prompts

import ai.agent.android.R
import ai.agent.android.domain.constants.PromptPresetConstants
import ai.agent.android.domain.models.NodeType
import ai.agent.android.domain.prompt.PromptTemplateEngine
import ai.agent.android.domain.prompt.PromptVariableProvider
import ai.agent.android.domain.repositories.PromptPresetRepository
import ai.agent.android.domain.usecases.SavePromptAsPresetUseCase
import ai.agent.android.presentation.ui.common.UiText
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * ViewModel for the Prompt Library screen — Phase 24 / Task 5 swaps the
 * source from the legacy `PromptTemplate` plumbing over to
 * [PromptPresetRepository], so the same catalogue surfaced in the editor
 * picker is now editable here too.
 *
 * Bundled presets are read-only. User presets can be edited (in-place
 * upsert via [SavePromptAsPresetUseCase] with an `existingId`), duplicated
 * (`(copy)` suffix), and deleted (through the repository).
 */
@HiltViewModel
@Suppress("TooManyFunctions") // Library + editor combine many discrete callbacks; collapsing hides intent.
class PromptLibraryViewModel @Inject constructor(
    private val promptPresetRepository: PromptPresetRepository,
    private val savePromptAsPresetUseCase: SavePromptAsPresetUseCase,
    private val promptTemplateEngine: PromptTemplateEngine,
    private val promptVariableProviders: Set<@JvmSuppressWildcards PromptVariableProvider>,
) : ViewModel() {

    private val _uiState = MutableStateFlow(
        PromptLibraryUiState(availableVariables = computeAvailableVariables(promptVariableProviders)),
    )
    val uiState: StateFlow<PromptLibraryUiState> = _uiState.asStateFlow()

    init {
        loadPresets()
    }

    /**
     * Re-runs the preset load flow and clears the previous `errorMessage`.
     * Wired to the catalog `PromptLibraryContent` error-state Retry CTA so a
     * failed load is recoverable in-place.
     */
    fun retry() {
        _uiState.update { it.copy(errorMessage = null) }
        loadPresets()
    }

    private fun loadPresets() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            combine(
                promptPresetRepository.getBundledPresets(),
                promptPresetRepository.getUserPresets(),
            ) { bundled, user -> bundled to user }
                .catch { e ->
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            errorMessage = e.message?.let(UiText::Dynamic)
                                ?: UiText(R.string.errors_generic_unexpected),
                        )
                    }
                }
                .collect { (bundled, user) ->
                    _uiState.update { state ->
                        state.copy(
                            isLoading = false,
                            bundledPresets = bundled,
                            userPresets = user,
                            errorMessage = null,
                        )
                    }
                }
        }
    }

    /**
     * Deletes the user-saved preset with [id]. No-op for bundled presets
     * — those ship inside the APK and are read-only; the catalog row's
     * delete icon is still wired to this method, so we short-circuit
     * silently rather than surface an error for a UX that's already
     * unambiguous via the "bundled" connotation.
     */
    fun deletePrompt(id: String) {
        val isBundled = _uiState.value.bundledPresets.any { it.id == id }
        if (isBundled) return
        viewModelScope.launch {
            try {
                promptPresetRepository.deleteUserPreset(id)
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        errorMessage = e.message?.let(UiText::Dynamic)
                            ?: UiText(R.string.errors_generic_unexpected),
                    )
                }
            }
        }
    }

    /**
     * Renders [template] through [PromptTemplateEngine] and exposes the resulting
     * segments via [PromptLibraryUiState.previewState] for display in the bottom sheet.
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

    /** Closes the prompt-preview bottom sheet, returning the UI to its idle state. */
    fun dismissPromptPreview() {
        _uiState.update { it.copy(previewState = PromptPreviewState.Hidden) }
    }

    fun clearError() {
        _uiState.update { it.copy(errorMessage = null) }
    }

    /** Selects [category] as the active tab on the library screen. */
    fun selectCategory(category: String) {
        _uiState.update { it.copy(selectedCategory = category) }
    }

    /**
     * Opens the bottom-sheet editor.
     *
     * @param promptId `null` to start a fresh draft (pre-filled with the
     *   currently-selected category); non-null to edit an existing user
     *   preset. Editing a bundled preset is a no-op — bundled rows are
     *   read-only by contract.
     */
    fun openEditor(promptId: String?) {
        val draft = if (promptId == null) {
            // Fresh draft inherits the category currently in view so the
            // resulting preset surfaces in the tab the user is browsing.
            val initialCategory = _uiState.value.selectedCategory.orEmpty()
            PromptEditorDraft(category = initialCategory)
        } else {
            val source = _uiState.value.userPresets.firstOrNull { it.id == promptId } ?: return
            PromptEditorDraft(
                id = source.id,
                name = source.name,
                category = source.nodeType.name,
                body = source.systemPrompt,
                description = source.description,
                tags = source.tags,
            )
        }
        _uiState.update { it.copy(editorDraft = draft) }
    }

    /** Closes the bottom-sheet editor without persisting changes. */
    fun closeEditor() {
        _uiState.update { it.copy(editorDraft = null) }
    }

    /** Updates the editor draft's Name field. */
    fun onEditorNameChange(value: String) {
        _uiState.update { state -> state.copy(editorDraft = state.editorDraft?.copy(name = value)) }
    }

    /** Updates the editor draft's Category field. */
    fun onEditorCategoryChange(value: String) {
        _uiState.update { state -> state.copy(editorDraft = state.editorDraft?.copy(category = value)) }
    }

    /** Updates the editor draft's Body field. */
    fun onEditorBodyChange(value: String) {
        _uiState.update { state -> state.copy(editorDraft = state.editorDraft?.copy(body = value)) }
    }

    /** Appends [token] at the end of the editor body (poor-man's "insert at cursor"). */
    fun onEditorVariableInsert(token: String) {
        _uiState.update { state ->
            val draft = state.editorDraft ?: return@update state
            val separator = if (draft.body.isEmpty() || draft.body.last().isWhitespace()) "" else " "
            state.copy(editorDraft = draft.copy(body = draft.body + separator + token))
        }
    }

    /**
     * Persists the editor draft via [SavePromptAsPresetUseCase] and closes
     * the sheet on success. Draft validation (name length, blank body,
     * LLM-driven node type) is delegated to the use case; failures surface
     * via [PromptLibraryUiState.errorMessage].
     */
    fun saveEditor() {
        val draft = _uiState.value.editorDraft ?: return
        val nodeType = runCatching { NodeType.valueOf(draft.category) }
            .getOrNull()
            ?.takeIf { it in PromptPresetConstants.LLM_DRIVEN_NODE_TYPES }
            ?: run {
                _uiState.update {
                    it.copy(errorMessage = UiText(R.string.prompts_editor_category_invalid))
                }
                return
            }
        viewModelScope.launch {
            val result = savePromptAsPresetUseCase(
                systemPrompt = draft.body,
                name = draft.name,
                description = draft.description,
                nodeType = nodeType,
                tags = draft.tags,
                existingId = draft.id,
            )
            result.fold(
                onSuccess = {
                    _uiState.update { it.copy(editorDraft = null) }
                },
                onFailure = { e ->
                    _uiState.update {
                        it.copy(
                            errorMessage = e.message?.let(UiText::Dynamic)
                                ?: UiText(R.string.errors_generic_unexpected),
                        )
                    }
                },
            )
        }
    }

    /**
     * Duplicates a preset (bundled or user) into a new user preset with a
     * `(copy)` suffix on the name. Bundled presets are duplicated as user
     * presets — that's how a user starts customising a bundled template
     * without losing the original.
     */
    fun duplicatePrompt(id: String) {
        val all = _uiState.value.bundledPresets + _uiState.value.userPresets
        val source = all.firstOrNull { it.id == id } ?: return
        viewModelScope.launch {
            val result = savePromptAsPresetUseCase(
                systemPrompt = source.systemPrompt,
                name = source.name + " (copy)",
                description = source.description,
                nodeType = source.nodeType,
                tags = source.tags,
            )
            if (result.isFailure) {
                _uiState.update {
                    it.copy(
                        errorMessage = result.exceptionOrNull()?.message?.let(UiText::Dynamic)
                            ?: UiText(R.string.errors_generic_unexpected),
                    )
                }
            }
        }
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
