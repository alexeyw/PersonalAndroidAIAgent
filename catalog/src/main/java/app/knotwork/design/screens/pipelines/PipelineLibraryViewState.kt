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
 * @property title pipeline display name; rendered in monospace per the
 * Knotwork brand. The brand uses mono for any identifier-shaped string
 * (pipeline names, node ids, etc.).
 * @property subtitle pre-formatted "N nodes · {flavour}" line — e.g.
 * "8 nodes · INPUT→PLANNER→TOOLS→OUTPUT".
 * @property secondaryLine extra status line beneath [subtitle]: "Active
 * default" / "N chats" / "unbound" — null hides the line entirely.
 * @property secondaryLineKind drives the colour of [secondaryLine]
 * (`Default` = muted, `Unbound` = `signalError`).
 * @property status status pill rendered next to the leading icon (only used
 * by the swipe-rendered list-row variant; the rich row drops it in favour
 * of the badge + secondaryLine).
 * @property leadingTint hue used for the 40 dp leading mark.
 * @property leadingIcon vector rendered inside the leading mark.
 * @property isActive `true` when this is the pipeline currently loaded in
 * the editor. Renders the full-row warm-cream tint shown in the spec
 * mockup.
 * @property isDefault `true` when this pipeline is the user's default;
 * renders a brown `DEFAULT` pill next to the title.
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
    val secondaryLine: String? = null,
    val secondaryLineKind: PipelineSecondaryLineKind = PipelineSecondaryLineKind.Default,
    val isActive: Boolean = false,
    val isDefault: Boolean = false,
    val selected: Boolean = false,
    val revealed: Boolean = false,
)

/**
 * Visual variant of [PipelineLibraryRow.secondaryLine]. Controls colour
 * only — the underlying text is owned by the host.
 */
enum class PipelineSecondaryLineKind {
    /** Default muted secondary text ("Active default", "N chats"). */
    Default,

    /** Renders the line in `signalError` (used for "unbound" pipelines). */
    Unbound,
}

/**
 * Top-level immutable input to `PipelineLibraryContent`. Mirrors
 * `compose/screens/README.md §C3` and the 7-state matrix locked in by
 * Phase 21 / Task 10.
 *
 * @property visualState which of the documented states to render.
 * @property pipelines rows passed through to the list body; empty when
 * [visualState] is [PipelineLibraryVisualState.Empty] /
 * [PipelineLibraryVisualState.Loading] / [PipelineLibraryVisualState.Error].
 * @property totalCount total number of pipelines persisted (snapshot for
 * the top-bar subtitle).
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
    val defaultCount: Int = pipelines.count { it.isDefault },
    val activeFilter: PipelineLibraryFilter = PipelineLibraryFilter.All,
    val errorMessage: String? = null,
    val selectedCount: Int = 0,
    /** Id of the row whose overflow menu is currently open; `null` = closed. */
    val openOverflowRowId: String? = null,
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
    val onFilterChange: (PipelineLibraryFilter) -> Unit = {},
    val onPipelineClick: (String) -> Unit = {},
    val onPipelineLongPress: (String) -> Unit = {},
    val onPipelineOverflow: (String) -> Unit = {},
    val onOverflowDismiss: () -> Unit = {},
    val onDuplicate: (String) -> Unit = {},
    val onArchive: (String) -> Unit = {},
    val onDelete: (String) -> Unit = {},
    val onLoadInEditor: (String) -> Unit = {},
    val onSetAsDefault: (String) -> Unit = {},
    val onRename: (String) -> Unit = {},
    val onExportJson: (String) -> Unit = {},
    val onSaveAsPreset: (String) -> Unit = {},
    val onImportJson: () -> Unit = {},
    val onOpenDrawer: () -> Unit = {},
    val onTopOverflow: () -> Unit = {},
    val onNewPipeline: () -> Unit = {},
    val onBrowseTemplates: () -> Unit = {},
    val onErrorRetry: () -> Unit = {},
    val onErrorReport: () -> Unit = {},
    val onMultiSelectCancel: () -> Unit = {},
    val onMultiSelectArchive: () -> Unit = {},
    val onMultiSelectDelete: () -> Unit = {},
)

/** Convenience factory returning a callbacks bundle that ignores every event. */
fun noopPipelineLibraryCallbacks(): PipelineLibraryCallbacks = PipelineLibraryCallbacks()
