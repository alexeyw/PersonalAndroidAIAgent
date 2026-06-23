package app.knotwork.android.presentation.ui.settings

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
 * Owns the two notification toggles plus the checkpoint-resume and
 * background-approval windows. Observes their persisted flows into the shared
 * [state] and routes edits back through [settingsRepository]. Shares the
 * ViewModel's [scope] and single [SettingsUiState] reducer.
 *
 * @property scope The ViewModel's `viewModelScope`.
 * @property state The ViewModel's single source-of-truth state flow.
 * @property settingsRepository Persistence for the notification + window settings.
 */
class BackgroundSettingsDelegate(
    private val scope: CoroutineScope,
    private val state: MutableStateFlow<SettingsUiState>,
    private val settingsRepository: SettingsRepository,
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
