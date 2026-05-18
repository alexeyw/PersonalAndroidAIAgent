package ai.agent.android.presentation.ui.onboarding

import ai.agent.android.domain.repositories.SettingsRepository
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.knotwork.design.screens.onboarding.OnboardingCloudProvider
import app.knotwork.design.screens.onboarding.OnboardingDefaultPipelinePreview
import app.knotwork.design.screens.onboarding.OnboardingLiteRtModel
import app.knotwork.design.screens.onboarding.OnboardingStep
import app.knotwork.design.screens.onboarding.OnboardingViewState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * ViewModel for the redesigned onboarding flow (Phase 21 / Task 10, second
 * mockup pass).
 *
 * Drives the 4-step pager:
 *  - Step 1 / Welcome — pure presentation.
 *  - Step 2 / LiteRT model — user picks a model id; the screen-level CTA
 *    triggers the download via the host (`onFinishModel` is the same as
 *    `onNext` for now — the actual download is wired post-v0.1).
 *  - Step 3 / Cloud keys — list of providers with `Configure` links; the
 *    real key-entry sheet lives in Settings, so this surface only records
 *    which providers the user opened (handy for analytics later).
 *  - Step 4 / Ready — recap screen with the default pipeline preview.
 *    Committing flips `hasCompletedOnboarding` and lets the host
 *    navigate to Chat.
 *
 * @property settingsRepository persists the `hasCompletedOnboarding` flag.
 */
@HiltViewModel
class OnboardingViewModel @Inject constructor(private val settingsRepository: SettingsRepository) : ViewModel() {

    private val _state: MutableStateFlow<OnboardingViewState> = MutableStateFlow(
        OnboardingViewState(defaultPipelinePreview = DEFAULT_PIPELINE_PREVIEW),
    )

    /** Externally-observable view state passed to `OnboardingContent`. */
    val state: StateFlow<OnboardingViewState> = _state.asStateFlow()

    /** Advances to the next step. Idempotent at the final step. */
    fun next() {
        _state.update { current ->
            val nextStep = OnboardingStep.entries.getOrNull(current.step.pageIndex + 1) ?: current.step
            current.copy(step = nextStep)
        }
    }

    /** Steps back; idempotent on step 1. */
    fun back() {
        _state.update { current ->
            val previousStep = OnboardingStep.entries.getOrNull(current.step.pageIndex - 1) ?: current.step
            current.copy(step = previousStep)
        }
    }

    /** Records the user's LiteRT model pick on step 2. */
    fun pickLiteRtModel(model: OnboardingLiteRtModel) {
        _state.update { it.copy(liteRtModel = model) }
    }

    /**
     * Records that the user opened the configure flow for a cloud
     * provider on step 3. The actual key entry happens in Settings.
     */
    fun markCloudProviderConfigured(provider: OnboardingCloudProvider) {
        _state.update { current ->
            current.copy(configuredCloudProviders = current.configuredCloudProviders + provider.id)
        }
    }

    /**
     * Final-step action — persists the `hasCompletedOnboarding` flag and
     * lets the host navigate to Chat.
     */
    fun finishOnboarding() {
        viewModelScope.launch {
            settingsRepository.setHasCompletedOnboarding(true)
        }
    }

    /**
     * Skip button (visible on steps 1-3). Persists `hasCompletedOnboarding
     * = true` exactly like [finishOnboarding] so the user isn't prompted
     * again next launch.
     */
    fun skipOnboarding() {
        finishOnboarding()
    }

    /** Legacy alias preserved so existing call sites keep compiling. */
    fun completeOnboarding() {
        finishOnboarding()
    }

    companion object {
        /**
         * Hardcoded default-pipeline recap rendered on step 4. The catalog
         * draws the chips from this projection; the actual default
         * pipeline lives in `DefaultPipelineFactory`. Keep the labels in
         * sync — six nodes, seven edges, IF picks up the green accent.
         */
        private val DEFAULT_PIPELINE_PREVIEW = OnboardingDefaultPipelinePreview(
            nodes = listOf("INPUT", "LITE_RT", "IF", "TOOL", "LITE_RT", "OUTPUT"),
            nodeCount = 6,
            edgeCount = 7,
            accentNodeName = "IF",
        )
    }
}
