package app.knotwork.design.screens.splash

/**
 * Render state of the Knotwork splash surface. Models the three cold-start
 * outcomes drawn by `SplashContent`.
 */
sealed interface SplashViewState {
    /**
     * Initial pre-emission state (use case has not yet produced an
     * `InitProgress` value). Rendered identically to `Loading(progress = 0f)`
     * but with the placeholder status text.
     */
    data object Initializing : SplashViewState

    /**
     * Determinate-progress in-flight state.
     *
     * @property message status label below the progress bar.
     * @property progress `0f..1f` fraction driving the `LinearProgressIndicator`.
     */
    data class Loading(val message: String, val progress: Float) : SplashViewState {
        init {
            require(progress in 0f..1f) { "progress must be in 0f..1f, was $progress" }
        }
    }

    /**
     * Terminal failure state — initialisation aborted. Shows the message
     * with a Retry CTA.
     *
     * @property message human-readable error message.
     */
    data class Error(val message: String) : SplashViewState
}

/**
 * One-shot callbacks consumed by `SplashContent`. Defaults to a no-op
 * bundle so previews and snapshots can ignore them.
 */
class SplashCallbacks(val onRetry: () -> Unit = {})

/** Convenience factory returning a no-op callback bundle. */
fun noopSplashCallbacks(): SplashCallbacks = SplashCallbacks()
