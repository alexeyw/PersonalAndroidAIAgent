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
 */
data class MemoryRow(
    val id: String,
    val title: String,
    val body: String,
    val tags: List<String>,
    val relevanceScore: String?,
    val lastAccessed: String,
)

/** Per-entry payload surfaced in the detail bottom sheet. */
data class MemoryEntryDetail(
    val id: String,
    val title: String,
    val body: String,
    val tags: List<String>,
    val lastAccessed: String,
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
 */
data class MemoryViewState(
    val visualState: MemoryVisualState,
    val entries: List<MemoryRow> = emptyList(),
    val searchQuery: String = "",
    val sortMode: MemorySortMode = MemorySortMode.Recent,
    val expandedEntry: MemoryEntryDetail? = null,
    val errorMessage: String? = null,
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
)

/** Convenience factory returning a callbacks bundle that ignores every event. */
fun noopMemoryCallbacks(): MemoryCallbacks = MemoryCallbacks()
