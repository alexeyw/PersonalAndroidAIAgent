package app.knotwork.android.presentation.ui.settings

import app.knotwork.android.domain.repositories.PipelineRepository
import app.knotwork.android.domain.repositories.SettingsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Background-&-triggers category delegate of [SettingsViewModel].
 *
 * Owns the two notification toggles, the checkpoint-resume and
 * background-approval windows, and the per-surface entry-point pipeline bindings
 * (share target, Quick Settings tile) plus the bindable-pipeline list backing
 * their pickers. Observes the persisted flows into the shared [state] and routes
 * edits back through [settingsRepository]. Shares the ViewModel's [scope] and
 * single [SettingsUiState] reducer.
 *
 * @property scope The ViewModel's `viewModelScope`.
 * @property state The ViewModel's single source-of-truth state flow.
 * @property settingsRepository Persistence for the notification + window + binding settings.
 * @property pipelineRepository Source of the bindable-pipeline list for the pickers.
 */
class BackgroundSettingsDelegate(
    private val scope: CoroutineScope,
    private val state: MutableStateFlow<SettingsUiState>,
    private val settingsRepository: SettingsRepository,
    private val pipelineRepository: PipelineRepository,
) {

    init {
        settingsRepository.longRunningTaskNotificationsEnabled.onEach { value ->
            state.update { it.copy(longRunningTaskNotificationsEnabled = value) }
        }.launchIn(scope)

        settingsRepository.scheduledTaskNotificationsEnabled.onEach { value ->
            state.update { it.copy(scheduledTaskNotificationsEnabled = value) }
        }.launchIn(scope)

        settingsRepository.resumeMaxAgeHours.onEach { value ->
            state.update { it.copy(resumeMaxAgeHours = value) }
        }.launchIn(scope)

        settingsRepository.backgroundApprovalWindowHours.onEach { value ->
            state.update { it.copy(backgroundApprovalWindowHours = value) }
        }.launchIn(scope)

        settingsRepository.shareTargetPipelineId.onEach { value ->
            state.update { it.copy(shareTargetPipelineId = value) }
        }.launchIn(scope)

        settingsRepository.quickSettingsTilePipelineId.onEach { value ->
            state.update { it.copy(quickSettingsTilePipelineId = value) }
        }.launchIn(scope)

        pipelineRepository.getAllPipelines().onEach { pipelines ->
            state.update { current ->
                current.copy(bindablePipelines = pipelines.map { PipelineBindingOption(it.id, it.name) })
            }
        }.launchIn(scope)
    }

    /** Persists (or clears, with `null`) the pipeline bound to the share target. */
    fun setShareTargetPipelineId(pipelineId: String?) {
        scope.launch { settingsRepository.setShareTargetPipelineId(pipelineId) }
    }

    /** Persists (or clears, with `null`) the pipeline bound to the Quick Settings tile. */
    fun setQuickSettingsTilePipelineId(pipelineId: String?) {
        scope.launch { settingsRepository.setQuickSettingsTilePipelineId(pipelineId) }
    }

    /** Persists the "ping me when a pipeline runs long in the background" toggle. */
    fun setLongRunningTaskNotificationsEnabled(enabled: Boolean) {
        scope.launch { settingsRepository.setLongRunningTaskNotificationsEnabled(enabled) }
    }

    /** Persists the "notify me when a scheduled task fires" toggle. */
    fun setScheduledTaskNotificationsEnabled(enabled: Boolean) {
        scope.launch { settingsRepository.setScheduledTaskNotificationsEnabled(enabled) }
    }

    /**
     * Persists the checkpoint-resume window (hours). The repository coerces the
     * value into the sanctioned 1–168 range.
     *
     * @param hours The new window picked on the slider.
     */
    fun setResumeMaxAgeHours(hours: Int) {
        scope.launch { settingsRepository.setResumeMaxAgeHours(hours) }
    }

    /**
     * Persists the background-approval window (hours) during which a run parked
     * on an unanswered HITL request waits for the user's response. The
     * repository coerces the value into the sanctioned 1–168 range.
     *
     * @param hours The new window picked on the slider.
     */
    fun setBackgroundApprovalWindowHours(hours: Int) {
        scope.launch { settingsRepository.setBackgroundApprovalWindowHours(hours) }
    }
}
