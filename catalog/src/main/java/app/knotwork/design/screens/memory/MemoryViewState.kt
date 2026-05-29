package app.knotwork.design.screens.memory

/**
 * Visual variant of the memory surface. Drives the 7 documented states
 * (`compose/screens/README.md §C6 · Memory`).
 */
enum class MemoryVisualState {
    /** No entries stored. */
    Empty,

    /** Default surface with one or more entries. */
    Populated,

    /** Query non-empty; rows are pre-filtered, FLIP-animated on rank shuffle. */
    Searching,

    /** Pagination fetch in progress; trailing skeleton row visible. */
    LoadingMore,

    /** Detail bottom sheet is open. */
    EntryExpanded,

    /** Detail sheet in edit mode. */
    Editing,

    /** Full-screen error state with Retry / diagnostics. */
    Error,
}

/** Sort modes exposed in the chip row beneath the search bar. */
enum class MemorySortMode {
    /** Newest first (default when query is empty). */
    Recent,

    /** Highest relevance score first (default when searching). */
    Relevance,

    /** Title alphabetical, ascending. */
    Alphabetical,
}

/**
 * Date-range filter applied to the entry list. Single-select chip group in
 * the filter row beneath the sort chips. The actual cut-off is resolved by
 * the screen (catalog stays clock-free).
 */
enum class MemoryDateFilter {
    /** No date constraint (default). */
    All,

    /** Only entries created within the last 7 days. */
    Last7Days,

    /** Only entries created within the last 30 days. */
    Last30Days,
}

/**
 * Provenance filter applied to the entry list. Multi-select chip group: a
 * chip lit means "include this source". An empty selection is treated as
 * "include every source" so the resting surface is unfiltered.
 */
enum class MemorySourceFilter {
    /** Chunks distilled automatically from finished conversations. */
    Auto,

    /** Chunks the user saved deliberately via "Save to memory". */
    Manual,

    /** Chunks produced by the background compaction pass. */
    Compaction,
}

/**
 * Lightweight projection of one memory chunk as consumed by
 * `MemoryContent`.
 *
 * @property id stable identity used as the `LazyColumn` key (drives the
 * FLIP rank-shuffle animation).
 * @property title display title (often the first line of the body).
 * @property body full body text; clamped to 3 lines in the list row.
 * @property tags optional tag list rendered as Outline chips.
 * @property relevanceScore optional relevance score (e.g. `"0.93"`).
 * @property lastAccessed human-readable "last accessed" string.
 * @property isPinned When `true`, the row renders a leading pin glyph and
 * sorts ahead of unpinned entries on the surface.
 */
data class MemoryRow(
    val id: String,
    val title: String,
    val body: String,
    val tags: List<String>,
    val relevanceScore: String?,
    val lastAccessed: String,
    val isPinned: Boolean = false,
)

/**
 * Per-entry payload surfaced in the detail bottom sheet.
 *
 * @property isPinned drives the Pin / Unpin action label in the sheet
 * footer.
 */
data class MemoryEntryDetail(
    val id: String,
    val title: String,
    val body: String,
    val tags: List<String>,
    val lastAccessed: String,
    val isPinned: Boolean = false,
)

/**
 * Top-level immutable input to `MemoryContent`. Mirrors
 * `compose/screens/README.md §C6`.
 *
 * @property visualState which of the 7 documented states to render.
 * @property entries rows passed through to the list body.
 * @property searchQuery current value of the search field.
 * @property sortMode currently-selected sort mode.
 * @property expandedEntry detail payload rendered in the bottom sheet when
 * [visualState] is [MemoryVisualState.EntryExpanded] / [MemoryVisualState.Editing].
 * @property errorMessage user-visible error rendered in [MemoryVisualState.Error].
 * @property dateFilter active date-range filter (drives the chip selection).
 * @property sourceFilters active provenance filters; empty = every source.
 * @property pinnedOnly when `true`, only pinned entries are shown.
 * @property selectionMode when `true`, the surface is in multi-select mode:
 * the top bar swaps to the bulk-action bar and rows render selection toggles
 * instead of opening the detail sheet on tap.
 * @property selectedIds ids of the entries currently selected in multi-select
 * mode (matches [MemoryRow.id]).
 */
data class MemoryViewState(
    val visualState: MemoryVisualState,
    val entries: List<MemoryRow> = emptyList(),
    val searchQuery: String = "",
    val sortMode: MemorySortMode = MemorySortMode.Recent,
    val expandedEntry: MemoryEntryDetail? = null,
    val errorMessage: String? = null,
    val dateFilter: MemoryDateFilter = MemoryDateFilter.All,
    val sourceFilters: Set<MemorySourceFilter> = emptySet(),
    val pinnedOnly: Boolean = false,
    val selectionMode: Boolean = false,
    val selectedIds: Set<String> = emptySet(),
) {
    init {
        require((visualState == MemoryVisualState.Error) == (errorMessage != null)) {
            "errorMessage must be non-null iff visualState == Error"
        }
        require(
            (
                visualState == MemoryVisualState.EntryExpanded ||
                    visualState == MemoryVisualState.Editing
                ) ==
                (expandedEntry != null),
        ) {
            "expandedEntry must be non-null iff visualState is EntryExpanded or Editing"
        }
    }
}

@Suppress("LongParameterList")
class MemoryCallbacks(
    val onBack: () -> Unit = {},
    val onSearchQueryChange: (String) -> Unit = {},
    val onSortChange: (MemorySortMode) -> Unit = {},
    val onEntryClick: (String) -> Unit = {},
    val onEntryDelete: (String) -> Unit = {},
    val onEntryEditRequest: (String) -> Unit = {},
    val onEntryEditCommit: (id: String, newBody: String) -> Unit = { _, _ -> },
    val onEntryEditCancel: () -> Unit = {},
    val onEntryPin: (String) -> Unit = {},
    val onCloseDetail: () -> Unit = {},
    val onErrorRetry: () -> Unit = {},
    val onErrorOpenDiagnostics: () -> Unit = {},
    val onEmptyCta: () -> Unit = {},
    val onClearSearch: () -> Unit = {},
    val onDateFilterChange: (MemoryDateFilter) -> Unit = {},
    val onSourceFilterToggle: (MemorySourceFilter) -> Unit = {},
    val onPinnedOnlyToggle: () -> Unit = {},
    val onEntryLongPress: (String) -> Unit = {},
    val onToggleSelect: (String) -> Unit = {},
    val onExitSelection: () -> Unit = {},
    val onDeleteSelected: () -> Unit = {},
    val onPinSelected: () -> Unit = {},
    val onUnpinSelected: () -> Unit = {},
    val onExportSelected: () -> Unit = {},
)

/** Convenience factory returning a callbacks bundle that ignores every event. */
fun noopMemoryCallbacks(): MemoryCallbacks = MemoryCallbacks()
