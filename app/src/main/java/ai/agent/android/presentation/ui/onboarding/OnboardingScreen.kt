package ai.agent.android.presentation.ui.onboarding

import ai.agent.android.R
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel

/**
 * Onboarding entry point.
 *
 * Phase 21 / Task 4 ships a deliberately minimal stub: a single screen with
 * the project's name, a one-line value-proposition, and a "Get started"
 * button that flips the `isFirstLaunch` DataStore flag and lets
 * [AppNavGraph][ai.agent.android.presentation.ui.navigation.AppNavGraph]
 * route forward to the Chat tab. The full 4-step `HorizontalPager` flow
 * (`screens/README.md §C5`: Welcome → Models → Permissions → Sample
 * pipelines) is Task 10's deliverable.
 *
 * Keeping the gate composable wired with the eventual final-screen shape
 * means Task 10 only replaces the body — no caller-side changes.
 *
 * @param onCompleted Pop onboarding off the back-stack and navigate to
 *        the Chat tab. Invoked exactly once per gate session.
 * @param viewModel Hilt-injected ViewModel; defaults to [hiltViewModel]
 *        so tests can supply a fake.
 */
@Composable
fun OnboardingScreen(onCompleted: () -> Unit, viewModel: OnboardingViewModel = hiltViewModel()) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .safeDrawingPadding()
            .padding(24.dp)
            .testTag(ONBOARDING_ROOT_TEST_TAG),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = stringResource(R.string.onboarding_headline),
            style = MaterialTheme.typography.headlineMedium,
        )
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = stringResource(R.string.onboarding_subtitle),
            style = MaterialTheme.typography.bodyMedium,
        )
        Spacer(modifier = Modifier.height(24.dp))
        Button(
            onClick = {
                viewModel.completeOnboarding()
                onCompleted()
            },
            modifier = Modifier.testTag(ONBOARDING_CTA_TEST_TAG),
        ) {
            Text(stringResource(R.string.onboarding_get_started))
        }
    }
}

/** Stable test tag for the onboarding screen root — used by instrumented tests. */
const val ONBOARDING_ROOT_TEST_TAG: String = "onboarding_root"

/** Stable test tag for the "Get started" CTA — used by instrumented tests. */
const val ONBOARDING_CTA_TEST_TAG: String = "onboarding_get_started_cta"
