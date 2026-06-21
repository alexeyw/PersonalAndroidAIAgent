package app.knotwork.android.domain.usecases

import app.knotwork.android.domain.repositories.SettingsRepository
import javax.inject.Inject

/**
 * Restores every tunable preference to its recommended default. Backs the
 * "Reset all settings" action inside the Settings → Privacy card.
 *
 * Thin wrapper over [SettingsRepository.resetToRecommendedDefaults] so the
 * presentation layer expresses intent through a typed use case (and so the
 * surface is mockable in `SettingsViewModelTest`). The scope guarantees —
 * which preferences are reset and which user data is deliberately left
 * untouched — are documented on the repository method.
 */
class ResetToRecommendedDefaultsUseCase @Inject constructor(private val settingsRepository: SettingsRepository) {
    /** Writes every tunable preference back to its [SettingsRepository]-documented default. */
    suspend operator fun invoke() {
        settingsRepository.resetToRecommendedDefaults()
    }
}
