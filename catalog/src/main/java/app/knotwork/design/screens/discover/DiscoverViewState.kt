package app.knotwork.design.screens.discover

/**
 * Visual variant of the model-discovery list surface (browsing the curated
 * Hugging Face organisation). Drives the documented state matrix and lets
 * snapshot tests iterate the states deterministically.
 *
 * The `:app` layer maps its `DiscoverUiState` onto this enum at the boundary
 * so the catalog stays free of `:app`/domain types.
 */
enum class DiscoverVisualState {
    /** Initial fetch (or a re-search) in progress; renders skeleton rows. */
    Loading,

    /** One or more compatible repositories listed. */
    Populated,

    /** Request succeeded but returned nothing (often a no-match search). */
    Empty,

    /** Network/parse failure; full-screen error with a Retry CTA. */
    Error,
}

/**
 * Lightweight projection of one discovered repository in the list.
 *
 * @property repoId fully-qualified repo id; stable `LazyColumn` key and the
 *   handle passed to the detail screen on tap.
 * @property name short display name (mono, brand convention for identifiers).
 * @property meta pre-formatted stats line owned by the host
 *   (e.g. `"↓ 12.3k · ♥ 45 · apache-2.0"`); the catalog never formats numbers.
 * @property fileCount number of installable `.litertlm` files; rendered as a
 *   localised "N files" hint (only for visible rows), or hidden when `0`. Kept
 *   as a raw count — not a pre-formatted string — so the host's row mapping
 *   stays free of `@Composable` plural lookups.
 * @property gated `true` to render the access-gated lock badge.
 */
data class DiscoverModelRow(
    val repoId: String,
    val name: String,
    val meta: String,
    val fileCount: Int = 0,
    val gated: Boolean = false,
)

/**
 * Top-level immutable input to `DiscoverContent`.
 *
 * @property visualState which documented state to render.
 * @property query current search text (caller owns the state).
 * @property rows listed repositories; empty in non-[DiscoverVisualState.Populated] states.
 * @property refreshing `true` while a pull-to-refresh is in flight (shows the
 *   refresh indicator without replacing the list with skeletons).
 * @property errorMessage user-visible error; non-null iff [visualState] is [DiscoverVisualState.Error].
 */
data class DiscoverViewState(
    val visualState: DiscoverVisualState,
    val query: String = "",
    val rows: List<DiscoverModelRow> = emptyList(),
    val refreshing: Boolean = false,
    val errorMessage: String? = null,
) {
    init {
        require((visualState == DiscoverVisualState.Error) == (errorMessage != null)) {
            "errorMessage must be non-null iff visualState == Error"
        }
    }
}

/**
 * Stable callback bundle accepted by `DiscoverContent`. Hoisted out of the
 * composable signature so screen code passes one object and tests/previews
 * construct a single no-op default.
 */
class DiscoverCallbacks(
    val onBack: () -> Unit = {},
    val onQueryChange: (String) -> Unit = {},
    val onSubmitSearch: () -> Unit = {},
    val onModelClick: (String) -> Unit = {},
    val onRefresh: () -> Unit = {},
    val onRetry: () -> Unit = {},
)

/** Convenience factory returning a callbacks bundle that ignores every event. */
fun noopDiscoverCallbacks(): DiscoverCallbacks = DiscoverCallbacks()
