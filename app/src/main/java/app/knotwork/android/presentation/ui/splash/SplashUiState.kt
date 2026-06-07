package app.knotwork.android.presentation.ui.splash

import app.knotwork.android.domain.models.InitStage

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
    val message: String = DEFAULT_STARTING_MESSAGE,
    val progressFraction: Float = 0f,
    val isDone: Boolean = false,
    val errorMessage: String? = null,
) {
    companion object {
        /**
         * Default `message` used before the first [InitStage] emission lands.
         * Kept as a plain `const val` (not a resource) because it is a Kotlin
         * default argument and `data class` defaults must be compile-time
         * constants; the screen is allowed to override the rendered text via
         * `stringResource(R.string.splash_starting)` when the user-visible
         * value is needed.
         */
        const val DEFAULT_STARTING_MESSAGE: String = "Starting…"
    }
}
