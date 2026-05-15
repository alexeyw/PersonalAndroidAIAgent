package ai.agent.android.presentation.ui.onboarding

import ai.agent.android.domain.repositories.SettingsRepository
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * ViewModel for the onboarding flow.
 *
 * Phase 21 / Task 4 ships a single-screen stub; the full 4-step
 * [HorizontalPager](https://developer.android.com/jetpack/compose/pager)
 * landing in Task 10 will continue to use this ViewModel as the
 * "completion" sink.
 *
 * Responsibilities:
 *  - Flip `SettingsRepository.hasCompletedOnboarding` to `true` exactly
 *    once when the user finishes (or skips) onboarding. The persisted
 *    flag is what
 *    [AppNavGraph][ai.agent.android.presentation.ui.navigation.AppNavGraph]
 *    reads on the next cold start to decide whether to show onboarding.
 *
 *    NB: this is a separate flag from `isFirstLaunch` — that one is
 *    cleared by `InitializeAppUseCase` during cold-start init (before
 *    the splash hands control to the nav-graph), so it cannot be the
 *    gate for the onboarding UI. See `SettingsRepository.kt` for the
 *    split rationale.
 */
@HiltViewModel
class OnboardingViewModel @Inject constructor(private val settingsRepository: SettingsRepository) : ViewModel() {

    /**
     * Mark onboarding as completed. Persists `hasCompletedOnboarding = true`
     * so subsequent launches go straight to the Chat tab.
     */
    fun completeOnboarding() {
        viewModelScope.launch {
            settingsRepository.setHasCompletedOnboarding(true)
        }
    }
}
