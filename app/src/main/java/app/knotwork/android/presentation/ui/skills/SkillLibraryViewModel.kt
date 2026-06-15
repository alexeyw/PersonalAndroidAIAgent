package app.knotwork.android.presentation.ui.skills

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.knotwork.android.R
import app.knotwork.android.domain.models.NodeContextConfig
import app.knotwork.android.domain.models.Skill
import app.knotwork.android.domain.models.ToolRisk
import app.knotwork.android.domain.prompt.PromptVariableProvider
import app.knotwork.android.domain.repositories.SkillRepository
import app.knotwork.android.domain.repositories.ToolRepository
import app.knotwork.android.domain.usecases.FindPipelinesUsingSkillUseCase
import app.knotwork.android.presentation.ui.common.UiText
import app.knotwork.design.components.chips.Risk
import app.knotwork.design.screens.skills.SkillLibraryTab
import app.knotwork.design.screens.skills.SkillToolMode
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * ViewModel for the Skill Library screen. Sources skills from
 * [SkillRepository] (bundled + user), the active tool catalogue from
 * [ToolRepository] for the editor's allowlist, and the registered prompt
 * variables for the editor's Insert row.
 *
 * Bundled skills are read-only — they can be duplicated into an editable copy
 * but never edited or deleted. Deleting a user skill routes through
 * [FindPipelinesUsingSkillUseCase] so the confirm dialog can warn about
 * dependent pipelines (dormant until the SKILL node ships).
 */
@HiltViewModel
@Suppress("TooManyFunctions") // Library + editor combine many discrete callbacks; collapsing hides intent.
class SkillLibraryViewModel @Inject constructor(
    private val skillRepository: SkillRepository,
    private val toolRepository: ToolRepository,
    private val findPipelinesUsingSkillUseCase: FindPipelinesUsingSkillUseCase,
    private val promptVariableProviders: Set<@JvmSuppressWildcards PromptVariableProvider>,
) : ViewModel() {

    private val _uiState = MutableStateFlow(
        SkillLibraryUiState(availableVariables = computeAvailableVariables(promptVariableProviders)),
    )
    val uiState: StateFlow<SkillLibraryUiState> = _uiState.asStateFlow()

    init {
        loadTools()
        observeSkills()
    }

    private fun observeSkills() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            combine(
                skillRepository.getBundledSkills(),
                skillRepository.getUserSkills(),
            ) { bundled, user -> bundled to user }
                .catch { e -> _uiState.update { it.copy(isLoading = false, errorMessage = e.toUiText()) } }
                .collect { (bundled, user) ->
                    _uiState.update {
                        it.copy(isLoading = false, bundledSkills = bundled, userSkills = user, errorMessage = null)
                    }
                }
        }
    }

    private fun loadTools() {
        viewModelScope.launch {
            val tools = try {
                toolRepository.getAvailableTools()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _uiState.update { it.copy(errorMessage = e.toUiText()) }
                return@launch
            }
            val entries = tools.map { tool ->
                SkillToolCatalogEntry(id = tool.name, risk = tool.risk?.toCatalogRisk(), description = tool.description)
            }
            _uiState.update { it.copy(availableTools = entries) }
        }
    }

    /** Re-runs the load and clears the previous error. */
    fun retry() {
        _uiState.update { it.copy(errorMessage = null) }
        loadTools()
        observeSkills()
    }

    /** Clears the transient error banner. */
    fun clearError() {
        _uiState.update { it.copy(errorMessage = null) }
    }

    /** Selects [tab] as the active sub-catalogue. */
    fun selectTab(tab: SkillLibraryTab) {
        _uiState.update { it.copy(tab = tab, openMenuSkillId = null) }
    }

    /** Opens the overflow menu for the row with [id]. */
    fun openRowMenu(id: String) {
        _uiState.update { it.copy(openMenuSkillId = id) }
    }

    /** Dismisses any open overflow menu. */
    fun dismissRowMenu() {
        _uiState.update { it.copy(openMenuSkillId = null) }
    }

    // ── Editor ──────────────────────────────────────────────────────────────

    /** Opens the editor for a fresh draft. */
    fun openNewSkill() {
        _uiState.update {
            it.copy(
                openMenuSkillId = null,
                editor = SkillEditorDraft(
                    id = null,
                    createdAt = 0L,
                    fromBundled = false,
                    name = "",
                    description = "",
                    instruction = "",
                    toolMode = SkillToolMode.All,
                    selectedToolIds = emptySet(),
                    contextConfig = NodeContextConfig.ALL_ENABLED,
                ),
            )
        }
    }

    /**
     * Opens the editor for the user skill with [id]. No-op for a bundled skill
     * — bundled rows expose Duplicate, not Edit.
     */
    fun openEditSkill(id: String) {
        val source = _uiState.value.userSkills.firstOrNull { it.id == id } ?: return
        _uiState.update { it.copy(openMenuSkillId = null, editor = source.toDraft(fromBundled = false)) }
    }

    /** Closes the editor without persisting. */
    fun closeEditor() {
        _uiState.update { it.copy(editor = null) }
    }

    /** Updates the editor draft's Name. */
    fun onEditorNameChange(value: String) = _uiState.update { it.copy(editor = it.editor?.copy(name = value)) }

    /** Updates the editor draft's Description. */
    fun onEditorDescriptionChange(value: String) =
        _uiState.update { it.copy(editor = it.editor?.copy(description = value)) }

    /** Updates the editor draft's Instruction. */
    fun onEditorInstructionChange(value: String) =
        _uiState.update { it.copy(editor = it.editor?.copy(instruction = value)) }

    /** Appends [token] to the end of the instruction (poor-man's insert-at-cursor). */
    fun onEditorVariableInsert(token: String) {
        _uiState.update { state ->
            val draft = state.editor ?: return@update state
            val separator = if (draft.instruction.isEmpty() || draft.instruction.last().isWhitespace()) "" else " "
            state.copy(editor = draft.copy(instruction = draft.instruction + separator + token))
        }
    }

    /** Changes the tri-state tool mode. */
    fun onEditorToolModeChange(mode: SkillToolMode) =
        _uiState.update { it.copy(editor = it.editor?.copy(toolMode = mode)) }

    /** Toggles a tool's membership in the Restrict allowlist. */
    fun onEditorToolToggle(toolId: String) {
        _uiState.update { state ->
            val draft = state.editor ?: return@update state
            val next = draft.selectedToolIds.toMutableSet().apply {
                if (!add(toolId)) remove(toolId)
            }
            state.copy(editor = draft.copy(selectedToolIds = next))
        }
    }

    /** Clears the Restrict allowlist selection. */
    fun onEditorToolsClear() = _uiState.update { it.copy(editor = it.editor?.copy(selectedToolIds = emptySet())) }

    /** Toggles one of the five context flags identified by [key]. */
    fun onEditorContextToggle(key: String) {
        _uiState.update { state ->
            val draft = state.editor ?: return@update state
            state.copy(editor = draft.copy(contextConfig = draft.contextConfig.toggle(key)))
        }
    }

    /** Persists the editor draft as a user skill and closes the editor on success. */
    fun saveEditor() {
        val draft = _uiState.value.editor ?: return
        if (!draft.canSave) return
        val skill = Skill(
            id = draft.id ?: java.util.UUID.randomUUID().toString(),
            name = draft.name.trim(),
            description = draft.description.trim(),
            instruction = draft.instruction.trim(),
            toolAllowlist = draft.resolveAllowlist(),
            contextConfig = draft.contextConfig,
            isBundled = false,
            createdAt = draft.createdAt,
            updatedAt = draft.createdAt,
        )
        viewModelScope.launch {
            try {
                skillRepository.saveUserSkill(skill)
                _uiState.update { it.copy(editor = null) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _uiState.update { it.copy(errorMessage = e.toUiText()) }
            }
        }
    }

    /** Duplicates a skill (bundled or user) into an editable user copy. */
    fun duplicateSkill(id: String) {
        _uiState.update { it.copy(openMenuSkillId = null) }
        viewModelScope.launch {
            try {
                skillRepository.duplicateSkill(id)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _uiState.update { it.copy(errorMessage = e.toUiText()) }
            }
        }
    }

    // ── Delete ──────────────────────────────────────────────────────────────

    /** Opens the delete dialog for the user skill with [id], resolving dependents. */
    fun requestDelete(id: String) {
        val skill = _uiState.value.userSkills.firstOrNull { it.id == id } ?: return
        _uiState.update { it.copy(openMenuSkillId = null) }
        viewModelScope.launch {
            val dependents = try {
                findPipelinesUsingSkillUseCase(id).map { it.name }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                emptyList()
            }
            _uiState.update {
                it.copy(
                    deleteTarget = SkillDeleteTarget(id = id, name = skill.name, dependentPipelineNames = dependents),
                )
            }
        }
    }

    /** Confirms the pending delete and removes the user skill. */
    fun confirmDelete() {
        val target = _uiState.value.deleteTarget ?: return
        _uiState.update { it.copy(deleteTarget = null) }
        viewModelScope.launch {
            try {
                skillRepository.deleteUserSkill(target.id)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _uiState.update { it.copy(errorMessage = e.toUiText()) }
            }
        }
    }

    /** Dismisses the delete dialog without deleting. */
    fun cancelDelete() {
        _uiState.update { it.copy(deleteTarget = null) }
    }

    // ── Mapping helpers ───────────────────────────────────────────────────────

    /** Builds an editor draft from an existing [Skill]. */
    private fun Skill.toDraft(fromBundled: Boolean): SkillEditorDraft = SkillEditorDraft(
        id = id,
        createdAt = createdAt,
        fromBundled = fromBundled,
        name = name,
        description = description,
        instruction = instruction,
        toolMode = when {
            toolAllowlist == null -> SkillToolMode.All
            toolAllowlist.isEmpty() -> SkillToolMode.None
            else -> SkillToolMode.Restrict
        },
        selectedToolIds = toolAllowlist?.toSet().orEmpty(),
        contextConfig = contextConfig,
    )

    private fun Throwable.toUiText(): UiText =
        message?.let(UiText::Dynamic) ?: UiText(R.string.errors_generic_unexpected)

    private companion object {
        /**
         * Mirrors `PromptLibraryViewModel.computeAvailableVariables`: a provider
         * whose `key()` throws is skipped so a misbehaving binding cannot empty
         * the Insert row.
         */
        private fun computeAvailableVariables(providers: Set<PromptVariableProvider>): List<String> = providers
            .mapNotNull { runCatching { it.key() }.getOrNull() }
            .distinct()
            .sorted()
            .map { "$$it" }
    }
}

/**
 * Resolves the [SkillEditorDraft]'s tri-state mode into a domain allowlist:
 * `null` (all tools), empty list (no tools), or the selected subset.
 */
private fun SkillEditorDraft.resolveAllowlist(): List<String>? = when (toolMode) {
    SkillToolMode.All -> null
    SkillToolMode.None -> emptyList()
    SkillToolMode.Restrict -> selectedToolIds.toList().sorted()
}

/** Flips the context flag identified by the catalog [key]. */
private fun NodeContextConfig.toggle(key: String): NodeContextConfig = when (key) {
    KEY_CHAT -> copy(chatHistory = !chatHistory)
    KEY_TASK -> copy(originalTask = !originalTask)
    KEY_INPUT -> copy(nodeInput = !nodeInput)
    KEY_MEMORY -> copy(longTermMemory = !longTermMemory)
    KEY_TOOLS -> copy(toolResults = !toolResults)
    else -> this
}

/** Maps a domain [ToolRisk] to the catalog [Risk] tier rendered in the picker. */
private fun ToolRisk.toCatalogRisk(): Risk = when (this) {
    ToolRisk.READ_ONLY -> Risk.Readonly
    ToolRisk.SENSITIVE -> Risk.Sensitive
    ToolRisk.DESTRUCTIVE -> Risk.Destructive
}

internal const val KEY_CHAT = "chat"
internal const val KEY_TASK = "task"
internal const val KEY_INPUT = "input"
internal const val KEY_MEMORY = "memory"
internal const val KEY_TOOLS = "tools"
