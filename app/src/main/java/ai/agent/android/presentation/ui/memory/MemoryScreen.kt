package ai.agent.android.presentation.ui.memory

import ai.agent.android.R
import ai.agent.android.domain.models.MemoryChunk
import ai.agent.android.domain.models.MemorySource
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.knotwork.design.screens.memory.MemoryCallbacks
import app.knotwork.design.screens.memory.MemoryContent
import app.knotwork.design.screens.memory.MemoryDateFilter
import app.knotwork.design.screens.memory.MemoryEntryDetail
import app.knotwork.design.screens.memory.MemoryRow
import app.knotwork.design.screens.memory.MemorySortMode
import app.knotwork.design.screens.memory.MemorySourceFilter
import app.knotwork.design.screens.memory.MemoryViewState
import app.knotwork.design.screens.memory.MemoryVisualState
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collectLatest
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Memory management screen — Phase 21 / Task 10 rewrite, extended in
 * Phase 25 / Task 7 with manual provenance/date filtering and multi-select
 * bulk-actions.
 *
 * The catalog `MemoryContent` composable owns the visual surface; this
 * screen subscribes to [MemoryViewModel], applies the active filters to the
 * raw chunk list, projects the result to the catalog [MemoryViewState], and
 * forwards events back to the VM. The legacy `Chat history` tab is dropped per
 * Task 10 scope.
 */
@Composable
fun MemoryScreen(viewModel: MemoryViewModel = hiltViewModel(), onOpenChat: () -> Unit = {}, onBack: () -> Unit = {}) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    var searchQuery by remember { mutableStateOf("") }
    val debouncedQuery = rememberDebouncedString(input = searchQuery, debounceMs = MEMORY_SEARCH_DEBOUNCE_MS)
    var sortMode by remember { mutableStateOf(MemorySortMode.Recent) }
    var expandedEntryId by remember { mutableStateOf<Long?>(null) }
    var editingEntryId by remember { mutableStateOf<Long?>(null) }

    val exportFilename = stringResource(R.string.memory_export_selected_filename)
    val exportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument(MIME_JSON),
    ) { uri: Uri? ->
        uri ?: return@rememberLauncherForActivityResult
        val stream = runCatching { context.contentResolver.openOutputStream(uri) }.getOrNull()
        if (stream != null) viewModel.exportSelectedTo(stream)
    }

    LaunchedEffect(viewModel) {
        viewModel.exportRequests.collect { exportLauncher.launch(exportFilename) }
    }

    // Apply the persisted provenance / date / pinned filters to the raw chunk
    // list before mapping to rows. `nowMillis` is read inside the derived
    // computation so the date cut-off tracks recompositions.
    val filteredChunks by remember(
        uiState.vectorMemories,
        uiState.dateFilter,
        uiState.sourceFilters,
        uiState.pinnedOnly,
    ) {
        derivedStateOf {
            uiState.vectorMemories.applyFilters(
                dateFilter = uiState.dateFilter,
                sourceFilters = uiState.sourceFilters,
                pinnedOnly = uiState.pinnedOnly,
                nowMillis = System.currentTimeMillis(),
            )
        }
    }

    val allRows by remember(filteredChunks) {
        derivedStateOf { filteredChunks.map { it.toMemoryRow() } }
    }
    val displayRows by remember(allRows, debouncedQuery, sortMode) {
        derivedStateOf {
            val byFilter = if (debouncedQuery.isBlank()) {
                allRows
            } else {
                allRows.filter { it.body.contains(debouncedQuery, ignoreCase = true) }
            }
            val sorted = when (sortMode) {
                // Higher id == newer entry. Sorting descending by id approximates
                // "newest first" without paying for a per-row timestamp parse.
                MemorySortMode.Recent -> byFilter.sortedByDescending { it.id.toLongOrNull() ?: 0L }
                // Relevance ordering is owned by the repository (vector store
                // returns rows in similarity order already); preserve the
                // upstream order untouched.
                MemorySortMode.Relevance -> byFilter
                // Locale-aware case-insensitive title sort.
                MemorySortMode.Alphabetical -> byFilter.sortedWith(
                    compareBy(String.CASE_INSENSITIVE_ORDER) { it.title },
                )
            }
            // Pinned rows always float to the top; the selected sort decides
            // the order within each partition. `sortedByDescending` is stable
            // so the upstream `sorted` ordering survives the partition.
            sorted.sortedByDescending { it.isPinned }
        }
    }

    val expandedDetail = remember(expandedEntryId, uiState.vectorMemories) {
        uiState.vectorMemories.firstOrNull { it.id == expandedEntryId }?.toEntryDetail()
    }

    val visualState = when {
        // Empty is driven by the underlying store, not the filtered view — an
        // active filter that hides everything must not show the onboarding CTA.
        uiState.vectorMemories.isEmpty() -> MemoryVisualState.Empty
        editingEntryId != null && expandedDetail != null -> MemoryVisualState.Editing
        expandedDetail != null -> MemoryVisualState.EntryExpanded
        debouncedQuery.isNotBlank() -> MemoryVisualState.Searching
        else -> MemoryVisualState.Populated
    }

    val viewState = MemoryViewState(
        visualState = visualState,
        entries = displayRows,
        searchQuery = searchQuery,
        sortMode = sortMode,
        expandedEntry = expandedDetail,
        dateFilter = uiState.dateFilter,
        sourceFilters = uiState.sourceFilters,
        pinnedOnly = uiState.pinnedOnly,
        selectionMode = uiState.selectionMode,
        selectedIds = uiState.selectedIds.map { it.toString() }.toSet(),
    )

    val callbacks = MemoryCallbacks(
        onBack = onBack,
        onSearchQueryChange = { searchQuery = it },
        onSortChange = { sortMode = it },
        onEntryClick = { id -> expandedEntryId = id.toLongOrNull() },
        onEntryDelete = { id ->
            id.toLongOrNull()?.let { entryId ->
                viewModel.deleteVectorMemory(memoryId = entryId)
                expandedEntryId = null
                editingEntryId = null
            }
        },
        onEntryEditRequest = { id -> editingEntryId = id.toLongOrNull() },
        onEntryEditCommit = { id, newBody ->
            id.toLongOrNull()?.let { entryId ->
                viewModel.editVectorMemory(id = entryId, newText = newBody)
            }
            editingEntryId = null
        },
        onEntryEditCancel = { editingEntryId = null },
        onEntryPin = { id -> id.toLongOrNull()?.let(viewModel::togglePinned) },
        onCloseDetail = {
            expandedEntryId = null
            editingEntryId = null
        },
        onErrorRetry = { viewModel.loadAllData() },
        onEmptyCta = onOpenChat,
        onClearSearch = { searchQuery = "" },
        onDateFilterChange = viewModel::setDateFilter,
        onSourceFilterToggle = viewModel::toggleSourceFilter,
        onPinnedOnlyToggle = viewModel::togglePinnedOnly,
        onEntryLongPress = { id -> id.toLongOrNull()?.let(viewModel::enterSelection) },
        onToggleSelect = { id -> id.toLongOrNull()?.let(viewModel::toggleSelect) },
        onExitSelection = viewModel::exitSelection,
        onDeleteSelected = viewModel::deleteSelected,
        onPinSelected = { viewModel.setSelectedPinned(pinned = true) },
        onUnpinSelected = { viewModel.setSelectedPinned(pinned = false) },
        onExportSelected = viewModel::requestExportSelected,
    )

    Box(modifier = Modifier.fillMaxSize().testTag(tag = MEMORY_ROOT_TEST_TAG)) {
        MemoryContent(state = viewState, callbacks = callbacks)
    }
}

/**
 * Applies the active provenance / date / pinned filters to a chunk list.
 *
 * Semantics:
 *  - [sourceFilters] empty ⇒ every source is included (resting surface).
 *    `MemorySource.Unknown` (legacy backfill) maps to no category, so once any
 *    source chip is lit those legacy chunks are hidden.
 *  - [dateFilter] constrains by `timestamp >= nowMillis - window`.
 *  - [pinnedOnly] keeps only pinned chunks.
 *
 * @param nowMillis Caller-supplied wall clock so this function stays testable.
 */
internal fun List<MemoryChunk>.applyFilters(
    dateFilter: MemoryDateFilter,
    sourceFilters: Set<MemorySourceFilter>,
    pinnedOnly: Boolean,
    nowMillis: Long,
): List<MemoryChunk> {
    val cutoff = when (dateFilter) {
        MemoryDateFilter.All -> null
        MemoryDateFilter.Last7Days -> nowMillis - DAYS_7_MILLIS
        MemoryDateFilter.Last30Days -> nowMillis - DAYS_30_MILLIS
    }
    return filter { chunk ->
        val dateOk = cutoff == null || chunk.timestamp >= cutoff
        val sourceOk = sourceFilters.isEmpty() || chunk.source.toFilterCategory() in sourceFilters
        val pinnedOk = !pinnedOnly || chunk.isPinned
        dateOk && sourceOk && pinnedOk
    }
}

/** Maps a domain [MemorySource] to its filter category, or `null` for legacy `Unknown`. */
private fun MemorySource.toFilterCategory(): MemorySourceFilter? = when (this) {
    is MemorySource.ChatSession -> MemorySourceFilter.Auto
    MemorySource.Manual -> MemorySourceFilter.Manual
    is MemorySource.Compaction -> MemorySourceFilter.Compaction
    MemorySource.Unknown -> null
}

/**
 * Debounces a string input so the heavy filtering work only re-runs after
 * the user pauses typing. The 200 ms floor matches
 * `compose/screens/README.md §C6`.
 */
@Composable
private fun rememberDebouncedString(input: String, debounceMs: Long): String {
    val emitter = remember { MutableStateFlow(value = input) }
    var debounced by remember { mutableStateOf(input) }
    LaunchedEffect(input) {
        emitter.value = input
    }
    LaunchedEffect(emitter, debounceMs) {
        emitter.collectLatest { value ->
            delay(timeMillis = debounceMs)
            debounced = value
        }
    }
    return debounced
}

private fun MemoryChunk.toMemoryRow(): MemoryRow = MemoryRow(
    id = id.toString(),
    title = text.lineSequence().firstOrNull()?.take(n = MEMORY_TITLE_MAX_CHARS).orEmpty(),
    body = text,
    tags = emptyList(),
    relevanceScore = null,
    lastAccessed = formatMemoryTimestamp(timestamp = timestamp),
    isPinned = isPinned,
)

private fun MemoryChunk.toEntryDetail(): MemoryEntryDetail = MemoryEntryDetail(
    id = id.toString(),
    title = text.lineSequence().firstOrNull()?.take(n = MEMORY_TITLE_MAX_CHARS).orEmpty(),
    body = text,
    tags = emptyList(),
    lastAccessed = formatMemoryTimestamp(timestamp = timestamp),
    isPinned = isPinned,
)

private fun formatMemoryTimestamp(timestamp: Long): String {
    val formatter = SimpleDateFormat("dd MMM yyyy HH:mm", Locale.getDefault())
    return formatter.format(Date(timestamp))
}

/** TestTag applied to the screen root. */
internal const val MEMORY_ROOT_TEST_TAG = "memory_screen_root"

/** Search debounce duration per `compose/screens/README.md §C6`. */
private const val MEMORY_SEARCH_DEBOUNCE_MS = 200L

/** Maximum characters preserved from the entry body when synthesising the row title. */
private const val MEMORY_TITLE_MAX_CHARS = 60

/** MIME type for the SAF "Export selected" document. */
private const val MIME_JSON = "application/json"

/** Milliseconds in 7 / 30 days — the date-range filter windows. */
private const val DAYS_7_MILLIS = 7L * 24 * 60 * 60 * 1000
private const val DAYS_30_MILLIS = 30L * 24 * 60 * 60 * 1000
