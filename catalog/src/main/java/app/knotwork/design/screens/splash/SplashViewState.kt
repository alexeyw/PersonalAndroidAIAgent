package app.knotwork.design.screens.splash

/**
 * Render state of the Knotwork splash surface. Models the four cold-start
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

    /**
     * Terminal recovery state — the encrypted database exists but its
     * passphrase cannot be read (lost Keystore key after backup/restore, OS
     * update, transient TEE failure). Renders a dedicated explanation with a
     * Retry CTA (such failures are often transient) and a destructive
     * "reset all data" action gated behind a typed-confirm dialog.
     *
     * All user-visible texts arrive pre-localised from the app layer so the
     * catalog stays string-resource-free for this screen.
     *
     * @property title short headline of the recovery surface.
     * @property body plain-language explanation of what happened and what
     *   Retry / Reset will do.
     * @property resetLabel label of the destructive reset button.
     * @property resetDialog typed-confirm dialog payload; `null` while the
     *   dialog is hidden.
     */
    data class DataLocked(
        val title: String,
        val body: String,
        val resetLabel: String,
        val resetDialog: SplashResetDialogState? = null,
    ) : SplashViewState
}

/**
 * Payload of the typed-confirm dialog gating the full data reset on the
 * [SplashViewState.DataLocked] surface. Mirrors the Settings destructive
 * dialog contract: the confirm button stays disabled until [pendingInput]
 * matches [keyword] (case-insensitive, trimmed).
 *
 * @property title dialog headline.
 * @property body warning text describing the irreversible consequence.
 * @property keyword exact word the user must type to arm the confirm button.
 * @property hint placeholder shown inside the typed-confirm field.
 * @property pendingInput current value of the typed-confirm field.
 * @property confirmLabel label of the destructive confirm button.
 * @property cancelLabel label of the dismiss button.
 */
data class SplashResetDialogState(
    val title: String,
    val body: String,
    val keyword: String,
    val hint: String,
    val pendingInput: String,
    val confirmLabel: String,
    val cancelLabel: String,
)

/**
 * One-shot callbacks consumed by `SplashContent`. Defaults to a no-op
 * bundle so previews and snapshots can ignore them.
 *
 * @property onRetry re-runs the initialization pipeline after a failure.
 * @property onResetRequest opens the typed-confirm reset dialog.
 * @property onResetInputChange live text change in the typed-confirm field.
 * @property onResetConfirm executes the wipe; only invoked while the typed
 *   keyword matches.
 * @property onResetDismiss closes the dialog without action.
 */
class SplashCallbacks(
    val onRetry: () -> Unit = {},
    val onResetRequest: () -> Unit = {},
    val onResetInputChange: (String) -> Unit = {},
    val onResetConfirm: () -> Unit = {},
    val onResetDismiss: () -> Unit = {},
)

/** Convenience factory returning a no-op callback bundle. */
fun noopSplashCallbacks(): SplashCallbacks = SplashCallbacks()
