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
 * [SettingsUiState.capAutonomousSteps]). Shares the ViewModel's [scope] and
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
    }

    /**
     * Persists the cap on autonomous planner cycles. The repository coerces the
     * value into the sanctioned range.
     */
    fun setCapAutonomousSteps(steps: Int) {
        scope.launch { settingsRepository.setPipelineMaxSteps(steps) }
    }
}
