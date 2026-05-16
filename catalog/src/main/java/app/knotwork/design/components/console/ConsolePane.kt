package app.knotwork.design.components.console

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.DeleteSweep
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.knotwork.design.theme.KnotworkTheme
import app.knotwork.design.tokens.KnotworkPalette
import app.knotwork.design.tokens.KnotworkTextStyles

/** Height of the sticky drag-handle + tab-strip + actions header. */
private val ConsoleHeaderHeight = 56.dp

/** Drag handle bar — `32 × 4 dp` centred. */
private val DragHandleWidth = 32.dp
private val DragHandleHeight = 4.dp

/** Opacity of the drag handle relative to `consoleFg`. */
private const val DRAG_HANDLE_ALPHA = 0.30f

/** Selected-tab underline thickness. */
private val TabIndicatorHeight = 2.dp

/** Width of one tab in the tab strip. */
private val TabWidth = 72.dp

/** Width of the leading accent strip on every log row. */
private val LogAccentStripWidth = 2.dp

/** Maximum length of the bar in a trace row, used to derive the per-row width. */
private val TraceBarMaxWidth = 160.dp

/**
 * Knotwork agent console — bottom-sheet container with three snap points
 * (`Peek` / `Partial` / `Full`) and three tabs (`Logs` / `Vars` / `Traces`).
 *
 * The console is **always dark** — same `extended.consoleBg` /
 * `extended.consoleFg` in light and dark theme, so developer ergonomics
 * (terminal-like contrast) survive the system theme.
 *
 * Catalog API takes plain `List<…>` collections instead of `LazyPagingItems`
 * — paging is a screen-level concern that the catalog deliberately avoids
 * depending on. The screen layer wraps the catalog component with its own
 * pager / pull-loader machinery.
 *
 * Visual contract: `compose/components/README.md` §Chat surface §ConsolePane.
 *
 * @param snap current snap point. Drives the pane height.
 * @param onSnapChange invoked when the user requests a new snap (drag
 * handle tap cycles through snaps, Close header action collapses to Peek).
 * @param tab currently-selected tab.
 * @param onTabChange invoked when the user taps a different tab.
 * @param logs Logs-tab data.
 * @param vars Vars-tab data.
 * @param traces Traces-tab data.
 * @param filter source filter applied to [logs].
 * @param onFilterChange invoked when the user toggles a source filter.
 * @param modifier optional layout modifier applied to the pane root.
 */
@Composable
@Suppress("LongParameterList") // Stable public API mirroring `components/README.md`.
fun ConsolePane(
    snap: ConsoleSnap,
    onSnapChange: (ConsoleSnap) -> Unit,
    tab: ConsoleTab,
    onTabChange: (ConsoleTab) -> Unit,
    logs: List<ConsoleLine>,
    vars: List<ConsoleVarRow>,
    traces: List<ConsoleTraceSpan>,
    filter: ConsoleFilter,
    onFilterChange: (ConsoleFilter) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .height(snap.height)
            .background(color = KnotworkTheme.extended.consoleBg),
    ) {
        ConsoleHeader(
            snap = snap,
            onSnapChange = onSnapChange,
            tab = tab,
            onTabChange = onTabChange,
        )
        if (snap != ConsoleSnap.Peek) {
            when (tab) {
                ConsoleTab.Logs -> ConsoleLogsBody(
                    logs = logs,
                    filter = filter,
                    onFilterChange = onFilterChange,
                )
                ConsoleTab.Vars -> ConsoleVarsBody(rows = vars)
                ConsoleTab.Traces -> ConsoleTracesBody(spans = traces)
            }
        } else {
            PeekTickerRow(logs = logs)
        }
    }
}

/** Sticky header: drag handle + tab strip + trailing actions. */
@Composable
private fun ConsoleHeader(
    snap: ConsoleSnap,
    onSnapChange: (ConsoleSnap) -> Unit,
    tab: ConsoleTab,
    onTabChange: (ConsoleTab) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .height(ConsoleHeaderHeight)
            .background(color = KnotworkTheme.extended.consoleBg),
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .fillMaxWidth()
                .height(KnotworkTheme.spacing.sp3)
                .clickable {
                    val next = when (snap) {
                        ConsoleSnap.Peek -> ConsoleSnap.Partial
                        ConsoleSnap.Partial -> ConsoleSnap.Full
                        ConsoleSnap.Full -> ConsoleSnap.Peek
                    }
                    onSnapChange(next)
                },
        ) {
            Box(
                modifier = Modifier
                    .size(width = DragHandleWidth, height = DragHandleHeight)
                    .clip(RoundedCornerShape(percent = 50))
                    .background(color = KnotworkTheme.extended.consoleFg.copy(alpha = DRAG_HANDLE_ALPHA)),
            )
        }
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth().weight(1f),
        ) {
            ConsoleTabStrip(tab = tab, onTabChange = onTabChange, modifier = Modifier.weight(1f))
            ConsoleActions(onClose = { onSnapChange(ConsoleSnap.Peek) })
        }
    }
}

/** Three-tab strip with a 2 dp accent underline on the selected tab. */
@Composable
private fun ConsoleTabStrip(tab: ConsoleTab, onTabChange: (ConsoleTab) -> Unit, modifier: Modifier = Modifier) {
    Row(modifier = modifier.fillMaxHeight()) {
        ConsoleTab.entries.forEach { entry ->
            val selected = entry == tab
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
                modifier = Modifier
                    .width(TabWidth)
                    .fillMaxHeight()
                    .clickable { onTabChange(entry) },
            ) {
                Text(
                    text = entry.name,
                    style = KnotworkTextStyles.MonoBase,
                    color = if (selected) {
                        KnotworkTheme.extended.consoleFg
                    } else {
                        KnotworkTheme.extended.consoleFg.copy(alpha = INACTIVE_TAB_ALPHA)
                    },
                )
                Spacer(modifier = Modifier.height(KnotworkTheme.spacing.sp1))
                Box(
                    modifier = Modifier
                        .size(width = TabWidth, height = TabIndicatorHeight)
                        .background(
                            color = if (selected) KnotworkPalette.Accent400 else Color.Transparent,
                        ),
                )
            }
        }
    }
}

/** Opacity of inactive tab labels relative to `consoleFg`. */
private const val INACTIVE_TAB_ALPHA = 0.55f

/** Trailing action row: Search / Copy all / Clear / Close. */
@Composable
private fun ConsoleActions(onClose: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        ConsoleHeaderIcon(icon = Icons.Outlined.Search, contentDescription = "Search log") {}
        ConsoleHeaderIcon(icon = Icons.Outlined.ContentCopy, contentDescription = "Copy all") {}
        ConsoleHeaderIcon(icon = Icons.Outlined.DeleteSweep, contentDescription = "Clear log") {}
        ConsoleHeaderIcon(icon = Icons.Outlined.Close, contentDescription = "Collapse console", onClick = onClose)
    }
}

/** One header trailing icon — uses console-foreground tint. */
@Composable
private fun ConsoleHeaderIcon(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
) {
    IconButton(onClick = onClick) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = KnotworkTheme.extended.consoleFg,
        )
    }
}

/** Peek-snap body — single ticker row showing the last log line. */
@Composable
private fun PeekTickerRow(logs: List<ConsoleLine>) {
    val last = logs.lastOrNull() ?: return
    Text(
        text = "${last.timestamp} [${last.source}] ${last.text}",
        style = KnotworkTextStyles.MonoSm,
        color = KnotworkTheme.extended.consoleFg.copy(alpha = INACTIVE_TAB_ALPHA),
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = KnotworkTheme.spacing.sp3, vertical = KnotworkTheme.spacing.sp1),
    )
}

/** Logs-tab body — filter chips + LazyColumn of [ConsoleLineRow]. */
@Composable
private fun ConsoleLogsBody(logs: List<ConsoleLine>, filter: ConsoleFilter, onFilterChange: (ConsoleFilter) -> Unit) {
    Column(modifier = Modifier.fillMaxWidth().fillMaxHeight()) {
        ConsoleSourceFilterRow(filter = filter, onFilterChange = onFilterChange)
        LazyColumn(modifier = Modifier.fillMaxWidth().weight(1f)) {
            items(items = logs.filter(filter::matches)) { line ->
                ConsoleLineRow(line = line)
            }
        }
    }
}

/** One filterable source chip. Toggles inclusion of [source] in [ConsoleFilter.sources]. */
@Composable
private fun ConsoleSourceFilterRow(filter: ConsoleFilter, onFilterChange: (ConsoleFilter) -> Unit) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(KnotworkTheme.spacing.sp2),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = KnotworkTheme.spacing.sp3, vertical = KnotworkTheme.spacing.sp1),
    ) {
        ConsoleSource.entries.forEach { source ->
            val on = source in filter.sources
            Box(
                modifier = Modifier
                    .clip(KnotworkTheme.shapes.full)
                    .background(
                        color = if (on) {
                            KnotworkPalette.Accent400.copy(alpha = SOURCE_CHIP_ON_ALPHA)
                        } else {
                            KnotworkTheme.extended.consoleFg.copy(alpha = SOURCE_CHIP_OFF_ALPHA)
                        },
                    )
                    .clickable {
                        val next = filter.sources.toMutableSet().apply {
                            if (on) remove(source) else add(source)
                        }
                        onFilterChange(filter.copy(sources = next))
                    }
                    .padding(horizontal = KnotworkTheme.spacing.sp2, vertical = KnotworkTheme.spacing.sp1),
            ) {
                Text(
                    text = source.name,
                    style = KnotworkTextStyles.LabelSm,
                    color = KnotworkTheme.extended.consoleFg,
                )
            }
        }
    }
}

/** Alpha applied to the source-filter chip when active. */
private const val SOURCE_CHIP_ON_ALPHA = 0.45f

/** Alpha applied to the source-filter chip when inactive. */
private const val SOURCE_CHIP_OFF_ALPHA = 0.10f

/** Single log row: leading accent strip + timestamp + source + text. */
@Composable
private fun ConsoleLineRow(line: ConsoleLine) {
    Row(modifier = Modifier.fillMaxWidth()) {
        Spacer(
            modifier = Modifier
                .width(LogAccentStripWidth)
                .height(KnotworkTheme.spacing.sp6)
                .background(color = levelAccent(line.level)),
        )
        Row(
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.spacedBy(KnotworkTheme.spacing.sp2),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = KnotworkTheme.spacing.sp3, vertical = KnotworkTheme.spacing.sp1),
        ) {
            Text(
                text = line.timestamp,
                style = KnotworkTextStyles.MonoSm,
                color = KnotworkTheme.extended.consoleFg.copy(alpha = INACTIVE_TAB_ALPHA),
            )
            Text(
                text = "[${line.source}]",
                style = KnotworkTextStyles.MonoSm,
                color = KnotworkPalette.Accent300,
            )
            Text(
                text = line.text,
                style = KnotworkTextStyles.MonoBase,
                color = KnotworkTheme.extended.consoleFg,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** Maps a [ConsoleLevel] to its leading-strip accent. */
@Composable
private fun levelAccent(level: ConsoleLevel): Color = when (level) {
    ConsoleLevel.Trace, ConsoleLevel.Info -> KnotworkTheme.extended.consoleFg.copy(alpha = SOURCE_CHIP_OFF_ALPHA)
    ConsoleLevel.Warn -> KnotworkTheme.extended.signalWarn
    ConsoleLevel.Error -> KnotworkTheme.extended.signalError
}

/** Vars-tab body — section headers per node, then key/value rows. */
@Composable
private fun ConsoleVarsBody(rows: List<ConsoleVarRow>) {
    val groups = rows.groupBy { it.node }
    LazyColumn(modifier = Modifier.fillMaxWidth().fillMaxHeight()) {
        groups.forEach { (node, entries) ->
            item {
                Text(
                    text = node,
                    style = KnotworkTextStyles.MonoSm,
                    color = KnotworkTheme.extended.consoleFg.copy(alpha = INACTIVE_TAB_ALPHA),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(
                            horizontal = KnotworkTheme.spacing.sp3,
                            vertical = KnotworkTheme.spacing.sp1,
                        ),
                )
            }
            items(items = entries) { row ->
                Row(
                    horizontalArrangement = Arrangement.spacedBy(KnotworkTheme.spacing.sp2),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(
                            horizontal = KnotworkTheme.spacing.sp4,
                            vertical = KnotworkTheme.spacing.sp1,
                        ),
                ) {
                    Text(
                        text = row.key,
                        style = KnotworkTextStyles.MonoBase,
                        color = KnotworkPalette.Accent400,
                    )
                    Text(
                        text = row.valueJson,
                        style = KnotworkTextStyles.MonoBase,
                        color = KnotworkTheme.extended.consoleFg,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

/** Traces-tab body — flat list with relative-duration bars. */
@Composable
private fun ConsoleTracesBody(spans: List<ConsoleTraceSpan>) {
    val maxDuration = spans.maxOfOrNull { it.durationMs }?.coerceAtLeast(1L) ?: 1L
    LazyColumn(modifier = Modifier.fillMaxWidth().fillMaxHeight()) {
        items(items = spans) { span ->
            val fraction = span.durationMs.toFloat() / maxDuration.toFloat()
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(
                        horizontal = KnotworkTheme.spacing.sp3,
                        vertical = KnotworkTheme.spacing.sp1,
                    ),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(KnotworkTheme.spacing.sp2),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        text = span.startedAt,
                        style = KnotworkTextStyles.MonoSm,
                        color = KnotworkTheme.extended.consoleFg.copy(alpha = INACTIVE_TAB_ALPHA),
                    )
                    Text(
                        text = span.name,
                        style = KnotworkTextStyles.MonoBase,
                        color = KnotworkTheme.extended.consoleFg,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        text = "${span.durationMs} ms",
                        style = KnotworkTextStyles.MonoSm,
                        color = traceStatusColor(span.status),
                    )
                }
                Spacer(modifier = Modifier.height(KnotworkTheme.spacing.sp1))
                Box(
                    modifier = Modifier
                        .height(TraceBarHeight)
                        .width(TraceBarMaxWidth * fraction)
                        .background(color = traceStatusColor(span.status)),
                )
            }
        }
    }
}

/** Height of one trace bar — thin sub-row beneath the row text. */
private val TraceBarHeight = 4.dp

/** Maps a [SpanStatus] to a colour used by both the bar and the duration label. */
@Composable
private fun traceStatusColor(status: SpanStatus): Color = when (status) {
    SpanStatus.Ok -> KnotworkTheme.extended.signalSuccess
    SpanStatus.Error -> KnotworkTheme.extended.signalError
}
