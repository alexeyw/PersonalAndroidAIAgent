package ai.agent.android.presentation.ui.memory

import ai.agent.android.domain.models.MemoryChunk
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
import androidx.compose.ui.platform.testTag
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.knotwork.design.screens.memory.MemoryCallbacks
import app.knotwork.design.screens.memory.MemoryContent
import app.knotwork.design.screens.memory.MemoryEntryDetail
import app.knotwork.design.screens.memory.MemoryRow
import app.knotwork.design.screens.memory.MemorySortMode
import app.knotwork.design.screens.memory.MemoryViewState
import app.knotwork.design.screens.memory.MemoryVisualState
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collectLatest
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Memory management screen — Phase 21 / Task 10 rewrite.
 *
 * The catalog `MemoryContent` composable owns the visual surface; this
 * screen subscribes to [MemoryViewModel], projects [MemoryUiState] to the
 * catalog [MemoryViewState], and forwards events back to the VM. The
 * legacy `Chat history` tab is dropped per Task 10 scope.
 */
@Suppress("UnusedParameter") // onBack kept for nav-graph compatibility until the catalog surface lands its back arrow.
@Composable
fun MemoryScreen(
    viewModel: MemoryViewModel = hiltViewModel(),
    onOpenChat: () -> Unit = {},
    onBack: () -> Unit = {},
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    var searchQuery by remember { mutableStateOf("") }
    val debouncedQuery = rememberDebouncedString(input = searchQuery, debounceMs = MEMORY_SEARCH_DEBOUNCE_MS)
    var sortMode by remember { mutableStateOf(MemorySortMode.Recent) }
    var expandedEntryId by remember { mutableStateOf<Long?>(null) }
    var editingEntryId by remember { mutableStateOf<Long?>(null) }

    val allRows by remember(uiState.vectorMemories) {
        derivedStateOf { uiState.vectorMemories.map { it.toMemoryRow() } }
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
        allRows.isEmpty() -> MemoryVisualState.Empty
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
    )

    val callbacks = MemoryCallbacks(
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
    )

    Box(modifier = Modifier.fillMaxSize().testTag(tag = MEMORY_ROOT_TEST_TAG)) {
        MemoryContent(state = viewState, callbacks = callbacks)
    }
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
