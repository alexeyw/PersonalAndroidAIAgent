package ai.agent.android.presentation.ui.onboarding

import androidx.activity.compose.LocalActivity
import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.knotwork.design.R
import app.knotwork.design.components.buttons.KnotworkPrimaryButton
import app.knotwork.design.components.buttons.KnotworkTextButton
import app.knotwork.design.screens.onboarding.OnboardingCallbacks
import app.knotwork.design.screens.onboarding.OnboardingContent
import app.knotwork.design.screens.onboarding.OnboardingStep
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch

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
 * ### Predictive-back handling (Phase 22 / Task 13)
 *
 * On steps 2–4 a system back gesture rewinds the pager one step via
 * [OnboardingViewModel.back] — matching the visual progress segments
 * filling in reverse. On step 1 there is nowhere to rewind to, so the
 * gesture raises a typed-confirm `AlertDialog`; confirming finishes the
 * activity (the user will land back here on next launch because
 * `hasCompletedOnboarding` has not flipped). The host wires
 * [PredictiveBackHandler] so the Android 14+ edge-swipe animates the
 * surface; the trailing `collect { }` keeps the handler alive for the
 * duration of the gesture and the dispatch fires on completion.
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
    val activity = LocalActivity.current
    val scope = rememberCoroutineScope()
    var exitConfirmVisible by rememberSaveable { mutableStateOf(value = false) }

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

    // PredictiveBackHandler runs *after* the user has fully committed the
    // back gesture (the trailing `collect {}` keeps the handler alive
    // during the swipe so Android renders the predictive transition). On
    // commit we either rewind the pager or raise the exit-confirm dialog
    // on step 1; cancellation falls through silently.
    PredictiveBackHandler { progress: Flow<androidx.activity.BackEventCompat> ->
        runCatching { progress.collect { /* observe to keep the handler alive */ } }
        scope.launch {
            if (state.step == OnboardingStep.Welcome) {
                exitConfirmVisible = true
            } else {
                viewModel.back()
            }
        }
    }

    OnboardingContent(
        state = state,
        callbacks = callbacks,
        modifier = Modifier
            .fillMaxSize()
            .safeDrawingPadding()
            .testTag(tag = ONBOARDING_ROOT_TEST_TAG),
    )

    if (exitConfirmVisible) {
        AlertDialog(
            onDismissRequest = { exitConfirmVisible = false },
            title = { Text(text = stringResource(R.string.knotwork_onboarding_exit_title)) },
            text = { Text(text = stringResource(R.string.knotwork_onboarding_exit_body)) },
            confirmButton = {
                KnotworkPrimaryButton(
                    text = stringResource(R.string.knotwork_onboarding_exit_confirm),
                    onClick = {
                        exitConfirmVisible = false
                        activity?.finish()
                    },
                )
            },
            dismissButton = {
                KnotworkTextButton(
                    text = stringResource(R.string.knotwork_onboarding_exit_cancel),
                    onClick = { exitConfirmVisible = false },
                )
            },
        )
    }
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
