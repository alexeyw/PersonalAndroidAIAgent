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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.knotwork.design.R
import app.knotwork.design.theme.KnotworkTheme
import app.knotwork.design.tokens.KnotworkPalette
import app.knotwork.design.tokens.KnotworkTextStyles

/** Height of the full (Partial / Full) header — drag handle + tab strip + actions row. */
private val FullHeaderHeight = 56.dp

/** Drag handle bar — `32 × 4 dp` centred at the top of every header. */
private val DragHandleWidth = 32.dp
private val DragHandleHeight = 4.dp

/** Vertical area reserved for the drag handle bar (handle + padding above/below). */
private val DragHandleAreaHeight = 8.dp

/** Compact tab-strip height used by the Peek header. */
private val PeekTabStripHeight = 18.dp

/** Single-line ticker height used by the Peek header. */
private val PeekTickerHeight = 16.dp

/** Combined Peek-header vertical budget (`8 + 18 + 16 = 42`); leaves `2 dp` slack inside the 44 dp snap. */
@Suppress("UnusedPrivateProperty") // Documented sum of the three Peek rows — kept as an explicit invariant marker.
private val PeekHeaderBudget: Dp = DragHandleAreaHeight + PeekTabStripHeight + PeekTickerHeight

/** Opacity of the drag handle relative to `consoleFg`. */
private const val DRAG_HANDLE_ALPHA = 0.30f

/** Selected-tab underline thickness (used by Partial / Full headers only — Peek tabs are label-only). */
private val TabIndicatorHeight = 2.dp

/** Width of one tab in the full (Partial / Full) tab strip. */
private val FullTabWidth = 72.dp

/** Width of the leading accent strip on every log row. */
private val LogAccentStripWidth = 2.dp

/** Maximum length of the bar in a trace row, used to derive the per-row width. */
private val TraceBarMaxWidth = 160.dp

/** Opacity of inactive tab labels relative to `consoleFg`. */
private const val INACTIVE_TAB_ALPHA = 0.55f

/** Alpha applied to the source-filter chip when active. */
private const val SOURCE_CHIP_ON_ALPHA = 0.45f

/** Alpha applied to the source-filter chip when inactive. */
private const val SOURCE_CHIP_OFF_ALPHA = 0.10f

/** Height of one trace bar — thin sub-row beneath the row text. */
private val TraceBarHeight = 4.dp

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
 * **Peek layout (44 dp budget).** The full Partial/Full header is 56 dp,
 * so Peek renders a *separate* compact header (8 dp drag handle + 18 dp
 * label-only tab strip + 16 dp ticker = 42 dp) that fits the 44 dp snap
 * and preserves the spec's "drag handle + tab strip + last-line ticker"
 * promise. The Search / Copy all / Clear / Close actions only appear in
 * Partial / Full where there is room for 48 dp icon buttons.
 *
 * **Header actions.** Every action button in the Partial/Full header
 * fires the matching caller-supplied callback ([onSearch], [onCopyAll],
 * [onClear], plus the close affordance which calls [onSnapChange] with
 * `ConsoleSnap.Peek`). Pass a no-op lambda if a host explicitly wants
 * to hide functionality, but every button is always wired — no dead UI.
 *
 * @param snap current snap point. Drives the pane height and the
 * Peek-vs-full header switch.
 * @param onSnapChange invoked when the user requests a new snap (drag
 * handle tap cycles through snaps, Close header action collapses to Peek).
 * @param tab currently-selected tab.
 * @param onTabChange invoked when the user taps a different tab.
 * @param logs Logs-tab data.
 * @param vars Vars-tab data.
 * @param traces Traces-tab data.
 * @param filter source filter applied to [logs].
 * @param onFilterChange invoked when the user toggles a source filter.
 * @param onSearch invoked when the user taps the header Search action
 * (Partial / Full only). Screens typically toggle an inline search bar.
 * @param onCopyAll invoked when the user taps the header Copy-all action.
 * @param onClear invoked when the user taps the header Clear action.
 * Screens typically confirm via a dialog before clearing the log.
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
    onSearch: () -> Unit,
    onCopyAll: () -> Unit,
    onClear: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .height(snap.height)
            .background(color = KnotworkTheme.extended.consoleBg),
    ) {
        if (snap == ConsoleSnap.Peek) {
            PeekHeader(tab = tab, onTabChange = onTabChange, logs = logs, onSnapChange = onSnapChange)
        } else {
            FullHeader(
                snap = snap,
                onSnapChange = onSnapChange,
                tab = tab,
                onTabChange = onTabChange,
                onSearch = onSearch,
                onCopyAll = onCopyAll,
                onClear = onClear,
            )
            when (tab) {
                ConsoleTab.Logs -> ConsoleLogsBody(
                    logs = logs,
                    filter = filter,
                    onFilterChange = onFilterChange,
                )
                ConsoleTab.Vars -> ConsoleVarsBody(rows = vars)
                ConsoleTab.Traces -> ConsoleTracesBody(spans = traces)
            }
        }
    }
}

/**
 * Compact header used in the 44 dp Peek snap. Renders drag handle, a
 * label-only tab strip, and a single-line ticker — totalling 42 dp inside
 * the 44 dp budget.
 */
@Composable
private fun PeekHeader(
    tab: ConsoleTab,
    onTabChange: (ConsoleTab) -> Unit,
    logs: List<ConsoleLine>,
    onSnapChange: (ConsoleSnap) -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        DragHandleStrip(onClick = { onSnapChange(ConsoleSnap.Partial) })
        PeekTabStrip(tab = tab, onTabChange = onTabChange)
        PeekTickerRow(logs = logs)
    }
}

/** Drag handle area — 8 dp tall, the 4 dp bar centred horizontally. */
@Composable
private fun DragHandleStrip(onClick: () -> Unit) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .fillMaxWidth()
            .height(DragHandleAreaHeight)
            .clickable(onClick = onClick),
    ) {
        Box(
            modifier = Modifier
                .size(width = DragHandleWidth, height = DragHandleHeight)
                .clip(RoundedCornerShape(percent = 50))
                .background(color = KnotworkTheme.extended.consoleFg.copy(alpha = DRAG_HANDLE_ALPHA)),
        )
    }
}

/** Label-only tab strip used in the Peek header — selected tab is bold-bright, others dim. */
@Composable
private fun PeekTabStrip(tab: ConsoleTab, onTabChange: (ConsoleTab) -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(KnotworkTheme.spacing.sp3),
        modifier = Modifier
            .fillMaxWidth()
            .height(PeekTabStripHeight)
            .padding(horizontal = KnotworkTheme.spacing.sp3),
    ) {
        ConsoleTab.entries.forEach { entry ->
            val selected = entry == tab
            Text(
                text = entry.name,
                style = KnotworkTextStyles.LabelSm,
                color = if (selected) {
                    KnotworkTheme.extended.consoleFg
                } else {
                    KnotworkTheme.extended.consoleFg.copy(alpha = INACTIVE_TAB_ALPHA)
                },
                modifier = Modifier.clickable { onTabChange(entry) },
            )
        }
    }
}

/** Full Partial/Full header: drag handle + tab strip + trailing actions. */
@Composable
@Suppress("LongParameterList") // Internal header — split-out further would just shuffle the params.
private fun FullHeader(
    snap: ConsoleSnap,
    onSnapChange: (ConsoleSnap) -> Unit,
    tab: ConsoleTab,
    onTabChange: (ConsoleTab) -> Unit,
    onSearch: () -> Unit,
    onCopyAll: () -> Unit,
    onClear: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .height(FullHeaderHeight)
            .background(color = KnotworkTheme.extended.consoleBg),
    ) {
        DragHandleStrip(
            onClick = {
                val next = when (snap) {
                    ConsoleSnap.Peek -> ConsoleSnap.Partial
                    ConsoleSnap.Partial -> ConsoleSnap.Full
                    ConsoleSnap.Full -> ConsoleSnap.Peek
                }
                onSnapChange(next)
            },
        )
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth().weight(1f),
        ) {
            FullTabStrip(tab = tab, onTabChange = onTabChange, modifier = Modifier.weight(1f))
            ConsoleActions(
                onSearch = onSearch,
                onCopyAll = onCopyAll,
                onClear = onClear,
                onClose = { onSnapChange(ConsoleSnap.Peek) },
            )
        }
    }
}

/** Three-tab strip with a 2 dp accent underline on the selected tab. */
@Composable
private fun FullTabStrip(tab: ConsoleTab, onTabChange: (ConsoleTab) -> Unit, modifier: Modifier = Modifier) {
    Row(modifier = modifier.fillMaxHeight()) {
        ConsoleTab.entries.forEach { entry ->
            val selected = entry == tab
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
                modifier = Modifier
                    .width(FullTabWidth)
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
                        .size(width = FullTabWidth, height = TabIndicatorHeight)
                        .background(
                            color = if (selected) KnotworkPalette.Accent400 else Color.Transparent,
                        ),
                )
            }
        }
    }
}

/** Trailing action row: Search / Copy all / Clear / Close. */
@Composable
private fun ConsoleActions(onSearch: () -> Unit, onCopyAll: () -> Unit, onClear: () -> Unit, onClose: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        ConsoleHeaderIcon(
            icon = Icons.Outlined.Search,
            contentDescription = stringResource(R.string.knotwork_console_action_search),
            onClick = onSearch,
        )
        ConsoleHeaderIcon(
            icon = Icons.Outlined.ContentCopy,
            contentDescription = stringResource(R.string.knotwork_console_action_copy_all),
            onClick = onCopyAll,
        )
        ConsoleHeaderIcon(
            icon = Icons.Outlined.DeleteSweep,
            contentDescription = stringResource(R.string.knotwork_console_action_clear),
            onClick = onClear,
        )
        ConsoleHeaderIcon(
            icon = Icons.Outlined.Close,
            contentDescription = stringResource(R.string.knotwork_console_action_close),
            onClick = onClose,
        )
    }
}

/** One header trailing icon — uses console-foreground tint. */
@Composable
private fun ConsoleHeaderIcon(icon: ImageVector, contentDescription: String, onClick: () -> Unit) {
    IconButton(onClick = onClick) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = KnotworkTheme.extended.consoleFg,
        )
    }
}

/** Peek-snap ticker — single line showing the last log entry. */
@Composable
private fun PeekTickerRow(logs: List<ConsoleLine>) {
    val last = logs.lastOrNull()
    val tickerText = if (last != null) "${last.timestamp} [${last.source}] ${last.text}" else ""
    Text(
        text = tickerText,
        style = KnotworkTextStyles.MonoSm,
        color = KnotworkTheme.extended.consoleFg.copy(alpha = INACTIVE_TAB_ALPHA),
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier
            .fillMaxWidth()
            .height(PeekTickerHeight)
            .padding(horizontal = KnotworkTheme.spacing.sp3),
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

/** One filterable source chip. Toggles inclusion of `source` in [ConsoleFilter.sources]. */
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

/** Maps a [SpanStatus] to a colour used by both the bar and the duration label. */
@Composable
private fun traceStatusColor(status: SpanStatus): Color = when (status) {
    SpanStatus.Ok -> KnotworkTheme.extended.signalSuccess
    SpanStatus.Error -> KnotworkTheme.extended.signalError
}
