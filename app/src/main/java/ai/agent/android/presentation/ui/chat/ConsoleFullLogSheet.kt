package ai.agent.android.presentation.ui.chat

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.ClearAll
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import ai.agent.android.domain.models.ConsoleEvent
import ai.agent.android.domain.models.ConsoleEventType
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Modal bottom sheet hosting the full session console log (Phase 17.5).
 *
 * Surfaces every [ConsoleEvent] of the current chat session in chronological
 * order with millisecond-precision timestamps, a filter-chip row over the
 * five [ConsoleLogFilter] categories, and a `TopAppBar` with `Clear` /
 * `Copy all` actions. Auto-scrolls to the tail whenever a new event arrives
 * and the user is already pinned to the bottom; otherwise an `↓ New events`
 * extended FAB appears so the user can jump back without breaking their
 * read position.
 *
 * Stateless except for two pieces of strictly UI-local state:
 *  - the "Clear log" confirmation dialog (private — opening/dismissing it is
 *    not interesting to the ViewModel);
 *  - the `LazyListState` and the `userAtBottom` flag that drive the
 *    auto-scroll / FAB visibility logic.
 *
 * The composable hosts the `ModalBottomSheet` itself, so callers only need
 * to pass the visibility flag and the dismiss callback.
 *
 * @param events Full chronological event log of the current session (the
 *   raw [ConsoleEvent] list as held by `ChatUiState.consoleLines`).
 *   Filtering is applied internally based on [filter].
 * @param filter Currently-selected category chip; drives which subset of
 *   [events] is rendered.
 * @param onFilterChange Invoked when the user picks a different chip.
 * @param onClear Invoked after the user confirms the "Clear log"
 *   `AlertDialog`. The ViewModel resets `consoleLines` and the sheet stays
 *   open; the user sees an empty list immediately.
 * @param onCopyAll Invoked when the user taps `Copy all`. The composable
 *   passes the rendered plain-text dump (one line per event, all events
 *   regardless of [filter] — copy-all means copy all). The caller is
 *   responsible for placing the text on the system clipboard and for any
 *   user feedback (e.g. a "Copied" snackbar).
 * @param onDismiss Invoked when the sheet is dismissed (drag-down, scrim
 *   tap, or the navigation icon).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConsoleFullLogSheet(
    events: List<ConsoleEvent>,
    filter: ConsoleLogFilter,
    onFilterChange: (ConsoleLogFilter) -> Unit,
    onClear: () -> Unit,
    onCopyAll: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val listState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()
    var clearDialogVisible by remember { mutableStateOf(false) }

    // Hoisted out of `ConsoleLogRow` because `LazyColumn` items are
    // disposed and re-entered on scroll, which would re-instantiate the
    // formatter for every row that re-enters the viewport. One instance
    // shared across rows is safe here — Compose rendering is single-
    // threaded, and `SimpleDateFormat.format(Date)` is reentrant on a
    // single thread despite the class being non-thread-safe in general.
    val timeFormatter = remember { SimpleDateFormat("HH:mm:ss.SSS", Locale.US) }

    // Filtered view of the events for rendering. Recomputed only when the
    // log itself or the selected chip change so chip toggles in a quiet
    // session don't trigger a full event-list re-walk on every recomposition.
    val visibleEvents = remember(events, filter) {
        events.filter { filter.matches(it) }
    }

    // Whether the user is currently following the tail of the log. Updated
    // exclusively from user-driven scroll events (`isScrollInProgress`),
    // never from item appends — the previous `derivedStateOf` approach
    // raced with append: when a new event is added, `totalItemsCount`
    // increments before `visibleItemsInfo.last().index` catches up,
    // briefly reading as "scrolled away" and suppressing the auto-scroll
    // even though the user had not moved.
    var pinnedToBottom by remember { mutableStateOf(true) }

    LaunchedEffect(listState) {
        snapshotFlow {
            listState.firstVisibleItemIndex to listState.firstVisibleItemScrollOffset
        }.collect {
            // Only re-evaluate while the user / animation is actually
            // moving the list. Item appends do not change the first-visible
            // tuple, so this collector stays quiet until the user drags or
            // the FAB triggers `animateScrollToItem`. Both cases correctly
            // re-pin (or un-pin) based on the final layout snapshot.
            if (listState.isScrollInProgress) {
                val info = listState.layoutInfo
                val total = info.totalItemsCount
                val lastVisible = info.visibleItemsInfo.lastOrNull()?.index ?: -1
                pinnedToBottom = total == 0 || lastVisible >= total - 1
            }
        }
    }

    // Auto-scroll to the freshest event whenever the rendered list grows
    // and the user hasn't manually scrolled away. `pinnedToBottom` is the
    // pre-append snapshot of intent, so a single new event still anchors
    // to the tail even though the layoutInfo would briefly disagree.
    //
    // When the list drains (Clear, filter switch with no matches), we
    // re-pin: the user has nothing to scroll past, so the next event must
    // tail by default. Without this, a Clear performed while scrolled up
    // would leave `pinnedToBottom = false` (the scroll-progress collector
    // had no reason to fire during the Clear), and the next event would
    // surface the "↓ New events" FAB even though the single row is fully
    // visible.
    LaunchedEffect(visibleEvents.size) {
        if (visibleEvents.isEmpty()) {
            pinnedToBottom = true
            return@LaunchedEffect
        }
        if (pinnedToBottom) {
            listState.scrollToItem(visibleEvents.lastIndex)
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
    ) {
        Scaffold(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.92f),
            topBar = {
                TopAppBar(
                    title = { Text("Console log") },
                    actions = {
                        IconButton(
                            onClick = { clearDialogVisible = true },
                            enabled = events.isNotEmpty(),
                        ) {
                            Icon(
                                imageVector = Icons.Default.ClearAll,
                                contentDescription = "Clear log",
                            )
                        }
                        IconButton(
                            onClick = { onCopyAll(plainTextDump(events)) },
                            enabled = events.isNotEmpty(),
                        ) {
                            Icon(
                                imageVector = Icons.Default.ContentCopy,
                                contentDescription = "Copy all",
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface,
                    ),
                )
            },
            floatingActionButton = {
                AnimatedVisibility(
                    visible = !pinnedToBottom && visibleEvents.isNotEmpty(),
                    enter = fadeIn(),
                    exit = fadeOut(),
                ) {
                    ExtendedFloatingActionButton(
                        onClick = {
                            // `visibleEvents.isNotEmpty()` is guaranteed
                            // by the AnimatedVisibility predicate above,
                            // so `lastIndex >= 0`.
                            val target = visibleEvents.lastIndex
                            coroutineScope.launch {
                                listState.animateScrollToItem(target)
                            }
                        },
                        icon = {
                            Icon(
                                imageVector = Icons.Default.KeyboardArrowDown,
                                contentDescription = null,
                            )
                        },
                        text = { Text("New events") },
                    )
                }
            },
        ) { contentPadding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(contentPadding),
            ) {
                FilterChipsRow(
                    selected = filter,
                    onSelect = onFilterChange,
                )
                if (visibleEvents.isEmpty()) {
                    EmptyState(
                        message = if (events.isEmpty()) {
                            "Console log is empty"
                        } else {
                            "No events match this filter"
                        },
                        modifier = Modifier.fillMaxSize(),
                    )
                } else {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 12.dp),
                    ) {
                        items(
                            items = visibleEvents,
                            // `timestamp + message` makes the key tolerant
                            // of two events with identical wall-clock
                            // timestamps (millisecond resolution under
                            // burst load) without resorting to identity
                            // hashes that would change on recomposition.
                            key = { event -> "${event.timestamp}|${event.message}" },
                        ) { event ->
                            ConsoleLogRow(event, timeFormatter)
                        }
                    }
                }
            }
        }
    }

    if (clearDialogVisible) {
        AlertDialog(
            onDismissRequest = { clearDialogVisible = false },
            title = { Text("Clear console log?") },
            text = {
                Text("This removes every event from the current session log. The action cannot be undone.")
            },
            confirmButton = {
                Button(onClick = {
                    clearDialogVisible = false
                    onClear()
                }) {
                    Text("Clear")
                }
            },
            dismissButton = {
                TextButton(onClick = { clearDialogVisible = false }) {
                    Text("Cancel")
                }
            },
        )
    }
}

/**
 * Single row in the expanded log list. Renders the millisecond timestamp
 * (`HH:mm:ss.SSS`), a category icon, and the message body in a monospace
 * font so column alignment matches the collapsed mini-console.
 *
 * @param event Log entry to render.
 * @param timeFormatter Shared `SimpleDateFormat` instance owned by the
 *   parent [ConsoleFullLogSheet]. Hoisted here so scrolling does not
 *   thrash through `LazyColumn` item disposal/re-entry, each cycle of
 *   which would otherwise allocate a fresh formatter inside `remember`.
 */
@Composable
private fun ConsoleLogRow(event: ConsoleEvent, timeFormatter: SimpleDateFormat) {
    val color = lineColor(event.type)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Text(
            text = timeFormatter.format(Date(event.timestamp)),
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
            fontFamily = FontFamily.Monospace,
            style = MaterialTheme.typography.bodySmall,
        )
        Spacer(modifier = Modifier.width(8.dp))
        Icon(
            imageVector = iconFor(event.type),
            contentDescription = null,
            tint = color,
            modifier = Modifier.size(16.dp),
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = event.message,
            color = color,
            fontFamily = FontFamily.Monospace,
            style = MaterialTheme.typography.bodySmall,
        )
    }
}

/**
 * Horizontal chip row that lets the user narrow the list to a specific
 * event category. Wrapped in `horizontalScroll` so the row stays usable on
 * narrow phones without wrapping to a second line (chips would otherwise
 * eat vertical space the log itself needs).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FilterChipsRow(
    selected: ConsoleLogFilter,
    onSelect: (ConsoleLogFilter) -> Unit,
) {
    val scrollState = rememberScrollState()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(scrollState)
            .padding(horizontal = 12.dp, vertical = 4.dp),
    ) {
        ConsoleLogFilter.entries.forEachIndexed { index, value ->
            if (index > 0) Spacer(modifier = Modifier.width(8.dp))
            FilterChip(
                selected = selected == value,
                onClick = { onSelect(value) },
                label = { Text(value.label) },
            )
        }
    }
}

/**
 * Centered placeholder rendered in place of the log list when there's
 * nothing to show — either because the session has no events yet or
 * because the active filter matches none of the existing entries.
 */
@Composable
private fun EmptyState(message: String, modifier: Modifier = Modifier) {
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
        )
    }
}

/**
 * Maps an event type to its leading row icon. Mirrors the colour mapping
 * in [lineColor] so the icon and message body share the same accent.
 */
private fun iconFor(type: ConsoleEventType): ImageVector = when (type) {
    ConsoleEventType.NodeExecution -> Icons.Default.PlayArrow
    ConsoleEventType.ToolCall -> Icons.Default.Build
    ConsoleEventType.MemoryAccess -> Icons.Default.Memory
    ConsoleEventType.SystemMessage -> Icons.Default.Info
    ConsoleEventType.Error -> Icons.Default.Error
}

/**
 * Maps an event category to its line colour. Kept separate from the
 * collapsed-console renderer because the expanded sheet uses a different
 * surface palette — the colours pick up `MaterialTheme.colorScheme` lazily
 * so theme changes are reflected without a force-recompose.
 */
@Composable
private fun lineColor(type: ConsoleEventType): Color = when (type) {
    ConsoleEventType.NodeExecution -> MaterialTheme.colorScheme.onSurface
    ConsoleEventType.ToolCall -> MaterialTheme.colorScheme.primary
    ConsoleEventType.MemoryAccess -> MaterialTheme.colorScheme.tertiary
    ConsoleEventType.SystemMessage -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
    ConsoleEventType.Error -> MaterialTheme.colorScheme.error
}

/**
 * Builds the plain-text representation of the entire log for clipboard
 * export. Format is identical to what the user sees on screen
 * (`HH:mm:ss.SSS  [TAG] message`) so a paste into a bug report mirrors the
 * UI exactly. Always dumps the unfiltered list — "Copy all" means all.
 */
internal fun plainTextDump(events: List<ConsoleEvent>): String {
    if (events.isEmpty()) return ""
    val formatter = SimpleDateFormat("HH:mm:ss.SSS", Locale.US)
    return events.joinToString(separator = "\n") { event ->
        val tag = when (event.type) {
            ConsoleEventType.NodeExecution -> "NODE"
            ConsoleEventType.ToolCall -> "TOOL"
            ConsoleEventType.MemoryAccess -> "MEM"
            ConsoleEventType.SystemMessage -> "SYS"
            ConsoleEventType.Error -> "ERR"
        }
        "${formatter.format(Date(event.timestamp))} [$tag] ${event.message}"
    }
}

