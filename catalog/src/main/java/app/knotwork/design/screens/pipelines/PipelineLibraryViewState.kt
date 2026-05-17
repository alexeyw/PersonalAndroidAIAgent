package app.knotwork.design.screens.pipelines

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import app.knotwork.design.components.chips.Status

/**
 * Visual variant of the pipeline-library surface. Drives the 7 documented
 * states (`compose/screens/README.md §C3 · Pipeline library`) and lets
 * snapshot tests iterate them deterministically.
 *
 * The presentation layer in `:app` maps its `OrchestratorUiState` onto this
 * enum at the boundary so the catalog stays free of `:app` types.
 */
enum class PipelineLibraryVisualState {
    /** No pipelines persisted; shows the empty-state CTA. */
    Empty,

    /** Initial fetch from disk; shows skeleton rows. */
    Loading,

    /** Default surface with one or more pipelines listed. */
    Populated,

    /**
     * Search text is non-empty. Rows are pre-filtered; if the result is empty
     * the body switches to a "no matches" tile.
     */
    Filtering,

    /** One row is in its swipe-revealed state; visual only — gestures live in `:app`. */
    SwipeOpen,

    /** Multi-select mode is active; the TopAppBar is replaced by selection chrome. */
    MultiSelect,

    /** Repository load failed; full-screen error state with Retry CTA. */
    Error,
}

/**
 * Filter chip exposed in the sticky chip row beneath the search bar. Mirrors
 * the spec's `All / Recent / Shared / Mine` set.
 */
enum class PipelineLibraryFilter {
    /** Default filter — every pipeline is visible. */
    All,

    /** Recently used pipelines. */
    Recent,

    /**
     * Pipelines shared with other devices / accounts. Phase 21 ships this
     * affordance disabled because the multi-device sync backend is not
     * available yet — the chip is rendered but the screen forbids selecting
     * it.
     */
    Shared,

    /** Pipelines authored on this device. */
    Mine,
}

/**
 * Lightweight projection of one pipeline as consumed by
 * `PipelineLibraryContent`. The catalog never reaches the domain
 * `PipelineGraph` directly.
 *
 * @property id stable identity used as the `LazyColumn` key.
 * @property title pipeline display name; rendered with `TitleMd` ellipsis.
 * @property subtitle pre-formatted secondary line (e.g. "Run 12 min ago · 3 nodes").
 * @property status status pill displayed next to the subtitle.
 * @property leadingTint hue used for the 40 dp leading mark.
 * @property leadingIcon vector rendered inside the leading mark.
 * @property selected `true` when this row is part of the active multi-select
 * selection (renders the 4 dp accent on the leading edge).
 * @property revealed `true` when this row is currently in its swipe-revealed
 * state — surfaced separately from the gesture state so snapshot tests can
 * pin it.
 */
data class PipelineLibraryRow(
    val id: String,
    val title: String,
    val subtitle: String,
    val status: Status,
    val leadingTint: Color,
    val leadingIcon: ImageVector,
    val selected: Boolean = false,
    val revealed: Boolean = false,
)

/**
 * Top-level immutable input to `PipelineLibraryContent`. Mirrors
 * `compose/screens/README.md §C3` and the 7-state matrix locked in by
 * Phase 21 / Task 10.
 *
 * @property visualState which of the documented states to render.
 * @property pipelines rows passed through to the list body; empty when
 * [visualState] is [PipelineLibraryVisualState.Empty] /
 * [PipelineLibraryVisualState.Loading] / [PipelineLibraryVisualState.Error].
 * @property totalCount total number of pipelines before filtering — used to
 * render the "12 of 47" subtitle in the filtering state.
 * @property searchQuery current value of the inline search field. Always
 * present even when empty; the field is sticky regardless of state.
 * @property activeFilter currently-selected filter chip.
 * @property errorMessage user-visible error text rendered in
 * [PipelineLibraryVisualState.Error]; `null` otherwise.
 * @property selectedCount number of selected rows; used by the multi-select
 * toolbar to render the count label.
 */
data class PipelineLibraryViewState(
    val visualState: PipelineLibraryVisualState,
    val pipelines: List<PipelineLibraryRow> = emptyList(),
    val totalCount: Int = pipelines.size,
    val searchQuery: String = "",
    val activeFilter: PipelineLibraryFilter = PipelineLibraryFilter.All,
    val errorMessage: String? = null,
    val selectedCount: Int = 0,
) {
    init {
        require((visualState == PipelineLibraryVisualState.Error) == (errorMessage != null)) {
            "errorMessage must be non-null iff visualState == Error"
        }
        require(selectedCount >= 0) { "selectedCount must be non-negative" }
    }
}

/**
 * Stable callback bundle accepted by `PipelineLibraryContent`. Hoisted out
 * of the composable signature so screen code can pass one parameter object
 * and tests / previews can construct a single no-op default.
 */
@Suppress("LongParameterList") // Mirrors user-visible affordances; collapsing further hides intent.
class PipelineLibraryCallbacks(
    val onSearchQueryChange: (String) -> Unit = {},
    val onFilterChange: (PipelineLibraryFilter) -> Unit = {},
    val onPipelineClick: (String) -> Unit = {},
    val onPipelineLongPress: (String) -> Unit = {},
    val onPipelineOverflow: (String) -> Unit = {},
    val onDuplicate: (String) -> Unit = {},
    val onArchive: (String) -> Unit = {},
    val onDelete: (String) -> Unit = {},
    val onNewPipeline: () -> Unit = {},
    val onBrowseTemplates: () -> Unit = {},
    val onClearSearch: () -> Unit = {},
    val onErrorRetry: () -> Unit = {},
    val onErrorReport: () -> Unit = {},
    val onMultiSelectCancel: () -> Unit = {},
    val onMultiSelectArchive: () -> Unit = {},
    val onMultiSelectDelete: () -> Unit = {},
)

/** Convenience factory returning a callbacks bundle that ignores every event. */
fun noopPipelineLibraryCallbacks(): PipelineLibraryCallbacks = PipelineLibraryCallbacks()
