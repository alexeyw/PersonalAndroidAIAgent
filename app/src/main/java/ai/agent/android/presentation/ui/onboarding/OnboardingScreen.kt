package ai.agent.android.presentation.ui.onboarding

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.knotwork.design.screens.onboarding.OnboardingCallbacks
import app.knotwork.design.screens.onboarding.OnboardingContent

/**
 * Onboarding entry point — 4-step pager (`Welcome → LiteRtModel →
 * CloudKeys → Ready`) backed by [OnboardingViewModel].
 *
 * The visual surface lives in `:catalog` (`OnboardingContent`); this
 * screen threads the ViewModel state and forwards finish/skip into the
 * nav-graph. The skip-flow snackbar hint is *not* rendered here — the
 * screen pops off the back-stack on `onCompleted`, so a screen-local
 * `SnackbarHost` would be unmounted before the snackbar appears. The
 * VM publishes the hint to
 * [ai.agent.android.presentation.state.TransientMessageRelay] instead,
 * and the activity-level host inside `AppShellScaffold` renders it on
 * top of whichever destination is current after navigation settles.
 *
 * @param onCompleted Pop onboarding off the back-stack and navigate to the
 * Chat tab. Invoked exactly once when the user taps `Open chat` on step 4
 * or `Skip` on steps 1-3.
 * @param viewModel Hilt-injected ViewModel; defaults to [hiltViewModel] so
 * tests can supply a fake.
 */
@Composable
fun OnboardingScreen(onCompleted: () -> Unit, viewModel: OnboardingViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val callbacks = remember(viewModel) {
        OnboardingCallbacks(
            onNext = viewModel::next,
            onBack = viewModel::back,
            onSkip = {
                viewModel.skipOnboarding()
                onCompleted()
            },
            onFinish = {
                viewModel.finishOnboarding()
                onCompleted()
            },
            onLiteRtModelPick = viewModel::pickLiteRtModel,
            onConfigureCloudProvider = viewModel::markCloudProviderConfigured,
            onStartDownload = viewModel::startDownload,
            onCustomDownloadUrlChanged = viewModel::onCustomDownloadUrlChanged,
        )
    }
    OnboardingContent(
        state = state,
        callbacks = callbacks,
        modifier = Modifier
            .fillMaxSize()
            .safeDrawingPadding()
            .testTag(tag = ONBOARDING_ROOT_TEST_TAG),
    )
}

/** Stable test tag for the onboarding screen root — used by instrumented tests. */
const val ONBOARDING_ROOT_TEST_TAG: String = "onboarding_root"

/**
 * Stable test tag for the "Get started" CTA — kept for backwards compatibility
 * with existing instrumented tests; the new pager wraps the CTA in
 * [OnboardingContent], so tests should now target the primary button via
 * its accessibility text rather than this tag.
 */
const val ONBOARDING_CTA_TEST_TAG: String = "onboarding_get_started_cta"
