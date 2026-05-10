package ai.agent.android.presentation.ui.splash

import ai.agent.android.domain.models.InitStage

/**
 * Render state of [SplashScreen].
 *
 * Mirrors the latest `InitProgress` snapshot from `AppInitializationUseCase`,
 * normalised for direct UI consumption (progress fraction, error/done flags).
 *
 * @property message Status text rendered under the progress bar.
 * @property progressFraction Determinate progress in `0f..1f` derived from
 *   `completedSteps / totalSteps`. `1f` once the run reaches [InitStage.Done].
 * @property isDone `true` after the use case successfully reaches
 *   [InitStage.Done]; the activity navigates to the home graph.
 * @property errorMessage Non-null while the use case is in the [InitStage.Failed]
 *   terminal state. Drives the inline error text + retry button.
 */
data class SplashUiState(
    val message: String = "Starting…",
    val progressFraction: Float = 0f,
    val isDone: Boolean = false,
    val errorMessage: String? = null,
)
