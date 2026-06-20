package app.knotwork.android.presentation.ui.discover

import app.knotwork.android.domain.models.DiscoverableModelSummary

/**
 * Loading branch of the Discover list screen, mirrored onto the catalog
 * `DiscoverVisualState` at the screen boundary.
 */
enum class DiscoverStatus {
    /** First fetch (or a re-search) in flight. */
    Loading,

    /** At least one compatible repository returned. */
    Populated,

    /** Request succeeded but returned nothing. */
    Empty,

    /** Network/parse failure. */
    Error,
}

/**
 * Immutable UI state of the model-discovery list screen.
 *
 * @property status which loading branch to render.
 * @property query current search text.
 * @property models compatible repositories from the last successful fetch.
 * @property refreshing `true` while a pull-to-refresh is running (keeps the
 *   list visible instead of swapping in skeletons).
 */
data class DiscoverUiState(
    val status: DiscoverStatus = DiscoverStatus.Loading,
    val query: String = "",
    val models: List<DiscoverableModelSummary> = emptyList(),
    val refreshing: Boolean = false,
)
