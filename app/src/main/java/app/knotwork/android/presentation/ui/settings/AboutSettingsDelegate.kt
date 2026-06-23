package app.knotwork.android.presentation.ui.settings

import android.content.Context
import app.knotwork.android.R
import app.knotwork.android.domain.constants.SettingsDefaults
import app.knotwork.android.domain.repositories.CrashReportingRepository
import app.knotwork.android.domain.repositories.IdentityRepository
import app.knotwork.android.domain.usecases.ResetToRecommendedDefaultsUseCase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * About category delegate of [SettingsViewModel].
 *
 * Loads the read-only identity card on construction and owns the
 * "Reset all settings" action. Shares the ViewModel's [scope] and single
 * [SettingsUiState] reducer.
 *
 * @property scope The ViewModel's `viewModelScope`.
 * @property state The ViewModel's single source-of-truth state flow.
 * @property appContext Application context for the identity placeholder + snackbar copy.
 * @property identityRepository Source of the identity-card snapshot.
 * @property resetToRecommendedDefaultsUseCase Single atomic write restoring every
 *   tunable preference to its recommended default.
 * @property crashReportingRepository Live crash-collector consent sink, flipped on
 *   reset so Crashlytics actually stops (the persisted flag alone does not).
 */
class AboutSettingsDelegate(
    private val scope: CoroutineScope,
    private val state: MutableStateFlow<SettingsUiState>,
    private val appContext: Context,
    private val identityRepository: IdentityRepository,
    private val resetToRecommendedDefaultsUseCase: ResetToRecommendedDefaultsUseCase,
    private val crashReportingRepository: CrashReportingRepository,
) {

    init {
        loadIdentity()
    }

    private fun loadIdentity() {
        scope.launch {
            val anonymous = appContext.getString(R.string.settings_identity_display_name)
            val identity = identityRepository.getIdentity(anonymous)
            state.update { it.copy(identity = identity) }
        }
    }

    /**
     * Restores every tunable preference to its recommended default via
     * [ResetToRecommendedDefaultsUseCase] (a single atomic write), then mirrors
     * the reset crash-reporting consent into [CrashReportingRepository] so
     * Crashlytics collection actually stops — the persisted flag alone does not
     * flip the live collector. User data (memory, chats, pipelines, prompts,
     * connections, secrets) is deliberately left untouched; the scope contract
     * lives on `SettingsRepository.resetToRecommendedDefaults`.
     */
    fun performResetSettings() {
        scope.launch {
            resetToRecommendedDefaultsUseCase()
            crashReportingRepository.setEnabled(SettingsDefaults.CRASH_REPORTING_ENABLED_DEFAULT)
            state.update { it.copy(snackbarMessage = appContext.getString(R.string.settings_reset_button)) }
        }
    }
}
