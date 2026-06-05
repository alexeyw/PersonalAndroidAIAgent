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
 * ### Predictive-back handling
 *
 * On steps 2–4 a committed system back gesture rewinds the pager one
 * step via [OnboardingViewModel.back] — matching the visual progress
 * segments filling in reverse. On step 1 there is nowhere to rewind to,
 * so the gesture raises a typed-confirm `AlertDialog`; confirming
 * finishes the activity (the user will land back here on next launch
 * because `hasCompletedOnboarding` has not flipped).
 *
 * The dispatch fires only after [PredictiveBackHandler] streams the
 * gesture to completion: when `collect` returns normally the user
 * committed; when it throws [CancellationException] the user lifted
 * before crossing the commit threshold (or another back source took
 * over) and the back action must NOT run. We deliberately do not wrap
 * the `collect` in `runCatching` because `kotlin.runCatching` swallows
 * `CancellationException` along with everything else, which would
 * collapse the cancel and commit paths into a single dispatch.
 *
 * @param onCompleted Pop onboarding off the back-stack and navigate to the
 * Chat tab. Invoked exactly once when the user taps `Open chat` on step 4
 * or `Skip` on steps 1-3.
 * @param onConfigureProvider Navigate to the per-provider API-key editor
 * for the supplied wire id. Wired by [AppNavGraph] to the
 * `settings/provider/{providerId}` route. The "Configured" pill on the
 * step-3 row that the user just tapped becomes truthful when the user
 * saves a key in the destination screen — the ViewModel observes
 * `ApiKeyRepository` and recomputes `configuredCloudProviders`
 * reactively, so no extra state-flip is needed on return.
 * @param viewModel Hilt-injected ViewModel; defaults to [hiltViewModel] so
 * tests can supply a fake.
 */
@Composable
fun OnboardingScreen(
    onCompleted: () -> Unit,
    onConfigureProvider: (providerId: String) -> Unit,
    viewModel: OnboardingViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val activity = LocalActivity.current
    var exitConfirmVisible by rememberSaveable { mutableStateOf(value = false) }

    val callbacks = remember(viewModel, onConfigureProvider) {
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
            onConfigureCloudProvider = { provider ->
                // Drive the per-provider key editor. The VM observes
                // ApiKeyRepository, so the "Configured" pill flips on
                // its own once the user saves a key in the destination
                // screen — no extra ViewModel call is needed on return.
                onConfigureProvider(provider.id)
            },
            onStartDownload = viewModel::startDownload,
            onCustomDownloadUrlChanged = viewModel::onCustomDownloadUrlChanged,
        )
    }

    // PredictiveBackHandler streams gesture events while the user is
    // swiping. The back action must dispatch on commit only — i.e. on
    // the path where `progress.collect { }` returns normally. When the
    // user lifts before crossing the commit threshold (or another back
    // source preempts us) `collect` throws `CancellationException`,
    // which propagates out of the handler lambda and skips the dispatch
    // below.
    //
    // We deliberately do NOT wrap `collect` in `runCatching` (or any
    // other catch) — `kotlin.Result` also captures
    // `CancellationException`, so doing so would collapse the commit
    // and cancel paths into a single dispatch and accidentally rewind
    // the pager on every canceled swipe.
    PredictiveBackHandler { progress: Flow<androidx.activity.BackEventCompat> ->
        progress.collect { /* observe to keep the handler alive */ }
        if (state.step == OnboardingStep.Welcome) {
            exitConfirmVisible = true
        } else {
            viewModel.back()
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
