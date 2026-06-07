package app.knotwork.android.domain.usecases

import app.knotwork.android.domain.repositories.SettingsRepository
import javax.inject.Inject

/**
 * Resets the local-generation sampling parameters back to
 * [app.knotwork.android.domain.constants.SettingsDefaults] values. Backs the
 * "Reset to defaults" header action inside the Settings → LLM parameters
 * card.
 *
 * Thin wrapper over [SettingsRepository.resetSamplingDefaults] so callers
 * can express intent through a typed use case (and so the surface is
 * mockable in `SettingsViewModelTest`).
 */
class ResetSamplingDefaultsUseCase @Inject constructor(private val settingsRepository: SettingsRepository) {
    /** Resets temperature / top-K / top-P / repetition-penalty / max-context / max-steps. */
    suspend operator fun invoke() {
        settingsRepository.resetSamplingDefaults()
    }
}
