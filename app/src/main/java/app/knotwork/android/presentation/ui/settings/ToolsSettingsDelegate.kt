package app.knotwork.android.presentation.ui.settings

import app.knotwork.android.domain.models.ToolApprovalPolicy
import app.knotwork.android.domain.repositories.SettingsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Tools-&-workspace category delegate of [SettingsViewModel].
 *
 * Owns the HITL approval policy and the two safety guardrails (block
 * destructive tools / block network from the local model). Observes their
 * persisted flows into the shared [state] and routes edits back through
 * [settingsRepository]. Shares the ViewModel's [scope] and single
 * [SettingsUiState] reducer (the Phase-34 `ChatHome*Delegate` pattern).
 *
 * @property scope The ViewModel's `viewModelScope`.
 * @property state The ViewModel's single source-of-truth state flow.
 * @property settingsRepository Persistence for the tool-restriction toggles.
 */
class ToolsSettingsDelegate(
    private val scope: CoroutineScope,
    private val state: MutableStateFlow<SettingsUiState>,
    private val settingsRepository: SettingsRepository,
) {

    init {
        settingsRepository.toolApprovalPolicy.onEach { value ->
            state.update { it.copy(toolApprovalPolicy = value) }
        }.launchIn(scope)

        settingsRepository.blockDestructiveTools.onEach { value ->
            state.update { it.copy(blockDestructiveTools = value) }
        }.launchIn(scope)

        settingsRepository.blockNetworkFromLocalModel.onEach { value ->
            state.update { it.copy(blockNetworkFromLocalModel = value) }
        }.launchIn(scope)
    }

    /** Persists the Human-in-the-Loop approval policy. */
    fun setToolApprovalPolicy(policy: ToolApprovalPolicy) {
        scope.launch { settingsRepository.setToolApprovalPolicy(policy) }
    }

    /** Persists the "always require a typed confirm for destructive tools" guardrail. */
    fun setBlockDestructiveTools(blocked: Boolean) {
        scope.launch { settingsRepository.setBlockDestructiveTools(blocked) }
    }

    /** Persists the "local model may not reach the network" guardrail. */
    fun setBlockNetworkFromLocalModel(blocked: Boolean) {
        scope.launch { settingsRepository.setBlockNetworkFromLocalModel(blocked) }
    }
}
