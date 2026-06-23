package app.knotwork.android.presentation.ui.settings

import app.knotwork.android.domain.repositories.SettingsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Pipelines-&-structured-output category delegate of [SettingsViewModel].
 *
 * Owns the autonomous-step safety cap (`pipelineMaxSteps`, surfaced as
 * [SettingsUiState.capAutonomousSteps]), the PIPELINE-node nesting-depth cap and
 * the structured-output repair budget. Shares the ViewModel's [scope] and
 * single [SettingsUiState] reducer.
 *
 * @property scope The ViewModel's `viewModelScope`.
 * @property state The ViewModel's single source-of-truth state flow.
 * @property settingsRepository Persistence for the pipeline step cap.
 */
class PipelinesSettingsDelegate(
    private val scope: CoroutineScope,
    private val state: MutableStateFlow<SettingsUiState>,
    private val settingsRepository: SettingsRepository,
) {

    init {
        settingsRepository.pipelineMaxSteps.onEach { value ->
            state.update { it.copy(capAutonomousSteps = value) }
        }.launchIn(scope)
        settingsRepository.pipelineMaxNestingDepth.onEach { value ->
            state.update { it.copy(pipelineMaxNestingDepth = value) }
        }.launchIn(scope)
        settingsRepository.structuredOutputMaxRepairs.onEach { value ->
            state.update { it.copy(structuredOutputMaxRepairs = value) }
        }.launchIn(scope)
    }

    /**
     * Persists the cap on autonomous planner cycles. The repository coerces the
     * value into the sanctioned range.
     */
    fun setCapAutonomousSteps(steps: Int) {
        scope.launch { settingsRepository.setPipelineMaxSteps(steps) }
    }

    /**
     * Persists the maximum PIPELINE-node nesting depth. The repository coerces
     * the value into the sanctioned range.
     */
    fun setPipelineMaxNestingDepth(depth: Int) {
        scope.launch { settingsRepository.setPipelineMaxNestingDepth(depth) }
    }

    /**
     * Persists the structured-output repair budget. The repository coerces the
     * value into the sanctioned range.
     */
    fun setStructuredOutputMaxRepairs(count: Int) {
        scope.launch { settingsRepository.setStructuredOutputMaxRepairs(count) }
    }
}
