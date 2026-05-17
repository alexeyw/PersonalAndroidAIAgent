package ai.agent.android.presentation.ui.onboarding

import ai.agent.android.domain.repositories.SettingsRepository
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.knotwork.design.screens.onboarding.OnboardingModelSource
import app.knotwork.design.screens.onboarding.OnboardingPermissionRow
import app.knotwork.design.screens.onboarding.OnboardingPermissionState
import app.knotwork.design.screens.onboarding.OnboardingStep
import app.knotwork.design.screens.onboarding.OnboardingViewState
import app.knotwork.design.screens.onboarding.PermissionIds
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * ViewModel for the onboarding flow.
 *
 * Phase 21 / Task 10 rewrites the screen as a 4-step `HorizontalPager`
 * (`Welcome → ModelSource → Permissions → SamplePipelines`) and threads
 * every per-step input through this ViewModel. The persisted result is the
 * `hasCompletedOnboarding` flag plus a thin pile of preferences captured
 * along the way (model-source choice, sample-pipeline picks); the heavy
 * downstream work (LiteRT model download, sample-pipeline materialisation)
 * is deliberately deferred to Settings / DefaultPipelineFactory so this
 * surface stays cheap and reversible.
 *
 * @property settingsRepository persists the `hasCompletedOnboarding` flag.
 */
@HiltViewModel
class OnboardingViewModel @Inject constructor(private val settingsRepository: SettingsRepository) : ViewModel() {

    private val _state: MutableStateFlow<OnboardingViewState> = MutableStateFlow(
        OnboardingViewState(permissions = initialPermissionRows()),
    )

    /** Externally-observable view state passed to `OnboardingContent`. */
    val state: StateFlow<OnboardingViewState> = _state.asStateFlow()

    /** Advances to the next step. Idempotent at the final step. */
    fun next() {
        _state.update { current ->
            val nextStep = OnboardingStep.entries.getOrNull(current.step.pageIndex + 1)
                ?: current.step
            current.copy(step = nextStep)
        }
    }

    /** Steps back; idempotent on step 1. */
    fun back() {
        _state.update { current ->
            val previousStep = OnboardingStep.entries.getOrNull(current.step.pageIndex - 1)
                ?: current.step
            current.copy(step = previousStep)
        }
    }

    /** Records the user's model-source pick in step 2. */
    fun pickModelSource(source: OnboardingModelSource) {
        _state.update { it.copy(modelSource = source, apiKeyError = null) }
    }

    /**
     * Updates the inline API-key field on step 2 and runs the cheapest
     * validation possible — non-empty values must start with `sk-` (OpenAI),
     * `anthropic` (Anthropic), or `goog` (Google). Anything else flags the
     * row but leaves the CTA enabled if the field is empty (the user hasn't
     * committed yet).
     */
    fun updateApiKey(value: String) {
        _state.update { current ->
            val error = when {
                value.isEmpty() -> null
                value.startsWith(prefix = "sk-") -> null
                value.startsWith(prefix = "anthropic") -> null
                value.startsWith(prefix = "goog") -> null
                else -> "Key doesn't look like a known provider format."
            }
            current.copy(apiKey = value, apiKeyError = error)
        }
    }

    /** Records that the user tapped `Grant now` on a permission row. */
    fun markPermissionRequested(id: String) {
        _state.update { current ->
            current.copy(
                permissions = current.permissions.map { row ->
                    if (row.id == id) row.copy(state = OnboardingPermissionState.Granted) else row
                },
            )
        }
    }

    /** Toggles a sample-pipeline pick in step 4. */
    fun toggleSample(id: String) {
        _state.update { current ->
            val nextSet = if (id in current.selectedSamples) {
                current.selectedSamples - id
            } else {
                current.selectedSamples + id
            }
            current.copy(selectedSamples = nextSet)
        }
    }

    /**
     * Final-step "Finish" action: persists the `hasCompletedOnboarding` flag
     * and lets the host navigate to Chat. The selected samples are surfaced
     * through [state] so a future hook in `InitializeAppUseCase` can read
     * them; v0.1 simply ignores extra picks because only the
     * `Local Q&A` sample is installable.
     */
    fun finishOnboarding() {
        viewModelScope.launch {
            settingsRepository.setHasCompletedOnboarding(true)
        }
    }

    /**
     * Skip button on steps 2-4. Persists `hasCompletedOnboarding = true`
     * exactly like [finishOnboarding] so the user is not prompted again on
     * the next launch.
     */
    fun skipOnboarding() {
        finishOnboarding()
    }

    /** Initial permission rows. Foreground and Storage are auto-granted on Android 16. */
    private fun initialPermissionRows(): List<OnboardingPermissionRow> = listOf(
        OnboardingPermissionRow(
            id = PermissionIds.NOTIFICATIONS,
            title = NOTIFICATIONS_TITLE,
            body = NOTIFICATIONS_BODY,
            state = OnboardingPermissionState.NotRequested,
        ),
        OnboardingPermissionRow(
            id = PermissionIds.MICROPHONE,
            title = MICROPHONE_TITLE,
            body = MICROPHONE_BODY,
            state = OnboardingPermissionState.NotRequested,
        ),
        OnboardingPermissionRow(
            id = PermissionIds.FOREGROUND,
            title = FOREGROUND_TITLE,
            body = FOREGROUND_BODY,
            state = OnboardingPermissionState.Auto,
        ),
        OnboardingPermissionRow(
            id = PermissionIds.STORAGE,
            title = STORAGE_TITLE,
            body = STORAGE_BODY,
            state = OnboardingPermissionState.Granted,
        ),
    )

    /**
     * Legacy compatibility alias for the previous single-screen onboarding
     * stub. Some test code referenced this name; keep the function pointing
     * at [finishOnboarding] so the public surface stays stable.
     */
    fun completeOnboarding() {
        finishOnboarding()
    }

    companion object {
        // Localisable strings live in the catalog (used by the
        // PermissionsStep composable) — these baseline titles are stored on
        // the VM side to keep the data class self-contained for unit tests.
        private const val NOTIFICATIONS_TITLE = "Notifications"
        private const val NOTIFICATIONS_BODY = "Tell you when long pipelines finish."
        private const val MICROPHONE_TITLE = "Microphone (optional)"
        private const val MICROPHONE_BODY = "Voice input in Chat."
        private const val FOREGROUND_TITLE = "Foreground service"
        private const val FOREGROUND_BODY = "Keep the agent running while you switch apps."
        private const val STORAGE_TITLE = "Storage (scoped)"
        private const val STORAGE_BODY = "Save and import pipelines."
    }
}
