package app.knotwork.android.presentation.ui.discover

import app.knotwork.android.domain.models.DiscoverableModelDetail

/** Loading branch of the Discover detail screen. */
enum class DiscoverDetailStatus {
    /** Fetching the repository detail. */
    Loading,

    /** Detail loaded. */
    Loaded,

    /** Network/parse failure. */
    Error,
}

/**
 * Immutable UI state of the model-discovery detail screen.
 *
 * @property status which loading branch to render.
 * @property detail the loaded repository detail, or `null` until loaded.
 * @property progress per-file download percentage (0..100) for in-flight installs.
 * @property installed file names installed during this screen's lifetime,
 *   layered over the [DiscoverableModelDetail.files] `isInstalled` flags so a
 *   just-finished install flips to the "Installed" badge without a refetch.
 * @property pendingLicenseFileName file awaiting license confirmation; non-null
 *   shows the confirm dialog.
 * @property tokenInput current Hugging Face token text (for gated repos).
 * @property tokenRevealed whether the token is shown in clear text.
 */
data class DiscoverDetailUiState(
    val status: DiscoverDetailStatus = DiscoverDetailStatus.Loading,
    val detail: DiscoverableModelDetail? = null,
    val progress: Map<String, Int> = emptyMap(),
    val installed: Set<String> = emptySet(),
    val pendingLicenseFileName: String? = null,
    val tokenInput: String = "",
    val tokenRevealed: Boolean = false,
)

/** One-shot result of an install attempt, surfaced to the screen as a snackbar. */
sealed interface DiscoverInstallEvent {
    /** Install completed; [fileName] is now on disk. */
    data class Success(val fileName: String) : DiscoverInstallEvent

    /**
     * Install failed.
     *
     * @property gated `true` when the failure was an access-gated refusal
     *   (HTTP 401/403) — the screen nudges the user toward a token.
     */
    data class Failed(val gated: Boolean) : DiscoverInstallEvent
}
