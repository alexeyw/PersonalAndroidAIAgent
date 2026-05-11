package ai.agent.android.presentation.ui.splash

import ai.agent.android.R
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle

/**
 * Splash / loading screen shown on cold start while [SplashViewModel] runs
 * the application initialisation pipeline. Renders the application name, a
 * determinate `LinearProgressIndicator` driven by `progressFraction`, the
 * current status `message`, and an inline error + Retry button on terminal
 * failure.
 *
 * On successful initialisation the screen invokes [onInitialized], which the
 * activity wires to a `popUpTo(splash) { inclusive = true }` navigation so
 * the splash route is removed from the back stack and Back from `home` exits
 * the app rather than re-entering the splash.
 *
 * @param onInitialized Invoked once when the use case reaches `InitStage.Done`.
 * @param viewModel Hilt-injected [SplashViewModel].
 * @param modifier Optional layout modifier.
 */
@Composable
fun SplashScreen(
    onInitialized: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: SplashViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(uiState.isDone) {
        if (uiState.isDone) onInitialized()
    }

    Box(
        // Splash sits directly under the NavHost, no Scaffold of its own —
        // so it must own the system-bar insets explicitly. Without this the
        // logo and progress text could collide with the status bar in
        // edge-to-edge mode.
        modifier = modifier
            .fillMaxSize()
            .systemBarsPadding(),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = 360.dp)
                .padding(horizontal = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                text = stringResource(R.string.app_name),
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onBackground,
            )
            Spacer(modifier = Modifier.height(48.dp))

            LinearProgressIndicator(
                progress = { uiState.progressFraction },
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(modifier = Modifier.height(16.dp))
            // The default `message` is a build-time string constant
            // (`Starting…`) because `data class` defaults must be
            // compile-time values. We swap it with the localised resource
            // here so the rendered text follows the device language until
            // the first real `InitProgress` emission overrides it.
            val displayedMessage = if (uiState.message == SplashUiState.DEFAULT_STARTING_MESSAGE) {
                stringResource(R.string.splash_starting)
            } else {
                uiState.message
            }
            Text(
                text = displayedMessage,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )

            if (uiState.errorMessage != null) {
                Spacer(modifier = Modifier.height(24.dp))
                Text(
                    text = uiState.errorMessage!!,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    textAlign = TextAlign.Center,
                )
                Spacer(modifier = Modifier.height(16.dp))
                Button(onClick = { viewModel.retry() }) {
                    Text(stringResource(R.string.common_retry))
                }
            }
        }
    }
}
