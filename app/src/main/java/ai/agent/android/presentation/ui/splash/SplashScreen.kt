package ai.agent.android.presentation.ui.splash

import ai.agent.android.R
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.knotwork.design.screens.splash.SplashCallbacks
import app.knotwork.design.screens.splash.SplashContent
import app.knotwork.design.screens.splash.SplashViewState

/**
 * Slim app-side splash mapper. Subscribes to [SplashViewModel.uiState],
 * maps the [SplashUiState] domain projection to the catalog
 * [SplashViewState], and renders [SplashContent].
 *
 * On successful initialisation the screen invokes [onInitialized], which the
 * activity wires to a `popUpTo(splash) { inclusive = true }` navigation so
 * the splash route is removed from the back stack.
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

    val startingMessage = stringResource(R.string.splash_starting)
    val retryLabel = stringResource(R.string.common_retry)
    val viewState = uiState.toViewState(defaultMessage = startingMessage)
    val appName = stringResource(R.string.app_name)

    SplashContent(
        appName = appName,
        state = viewState,
        modifier = modifier,
        retryLabel = retryLabel,
        callbacks = SplashCallbacks(onRetry = viewModel::retry),
    )
}

/**
 * Map the cold-start [SplashUiState] onto the catalog
 * [SplashViewState] used by `SplashContent`.
 *
 * @param defaultMessage placeholder text shown while the use case has not
 * yet emitted a localised status.
 */
internal fun SplashUiState.toViewState(defaultMessage: String): SplashViewState = when {
    errorMessage != null -> SplashViewState.Error(message = errorMessage)
    message == SplashUiState.DEFAULT_STARTING_MESSAGE && progressFraction == 0f -> SplashViewState.Initializing
    else -> {
        val displayed = if (message == SplashUiState.DEFAULT_STARTING_MESSAGE) defaultMessage else message
        SplashViewState.Loading(
            message = displayed,
            progress = progressFraction.coerceIn(minimumValue = 0f, maximumValue = 1f),
        )
    }
}
