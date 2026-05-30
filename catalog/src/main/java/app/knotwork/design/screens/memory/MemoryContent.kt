@file:Suppress("MatchingDeclarationName", "LongMethod", "TooManyFunctions")
// File hosts MemoryContent and its private section/row/dialog helpers.

package app.knotwork.design.screens.memory

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.outlined.ArrowDropDown
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.knotwork.design.R
import app.knotwork.design.components.buttons.KnotworkButtonSize
import app.knotwork.design.components.buttons.KnotworkPrimaryButton
import app.knotwork.design.components.buttons.KnotworkSecondaryButton
import app.knotwork.design.components.buttons.KnotworkTextButton
import app.knotwork.design.components.chips.KnotworkFilterChip
import app.knotwork.design.components.misc.EmptyState
import app.knotwork.design.theme.KnotworkTheme
import app.knotwork.design.tokens.KnotworkTextStyles

/** Minimum width fraction so a tiny breakdown segment still renders a sliver. */
private const val MIN_SEGMENT_FRACTION = 0.0001f

/** Height of the provenance breakdown bar (spec §6 — 6 px). */
private val BreakdownBarHeight = 6.dp

/** Width of a row's leading provenance accent bar. */
private val AccentBarWidth = 3.dp

/** Card vertical rhythm (spec §6): title → description 2 px, description → meta 8 px. */
private val CARD_TITLE_GAP = 2.dp
private val CARD_BODY_GAP = 8.dp

/**
 * Stateless Knotwork memory surface — Phase 25 redesign.
 *
 * Renders the stats header (count / size / last-compacted / provenance
 * breakdown + Compact), the category chip row, the sort + date dropdowns, the
 * time-grouped entry list (with provenance accent + badge + tags), the
 * semantic-search field, the detail bottom sheet, the Compact / Add dialogs,
 * and the "Add memory" FAB. All state is supplied by [state]; every
 * interaction is forwarded through [callbacks].
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MemoryContent(
    state: MemoryViewState,
    modifier: Modifier = Modifier,
    callbacks: MemoryCallbacks = noopMemoryCallbacks(),
) {
    Box(modifier = modifier.fillMaxSize()) {
        Scaffold(
            containerColor = MaterialTheme.colorScheme.surface,
            // System-bar insets are owned by the app shell (matches every other
            // catalog screen); without this the Scaffold double-pads the bottom
            // under the navigation buttons.
            contentWindowInsets = WindowInsets(0, 0, 0, 0),
            topBar = {
                app.knotwork.design.components.topbar.KnotworkTopAppBarShell {
                    MemoryTopBar(searching = state.searchActive, callbacks = callbacks)
                }
            },
            floatingActionButton = {
                if (state.visualState != MemoryVisualState.Error) {
                    ExtendedFloatingActionButton(
                        onClick = callbacks.onAddClick,
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary,
                        icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                        text = { Text(stringResource(R.string.knotwork_memory_add_memory)) },
                    )
                }
            },
            modifier = Modifier.fillMaxSize(),
        ) { padding ->
            MemoryBody(state = state, callbacks = callbacks, padding = padding)
        }

        if (state.visualState == MemoryVisualState.EntryExpanded || state.visualState == MemoryVisualState.Editing) {
            state.expandedEntry?.let { detail ->
                MemoryDetailSheet(
                    detail = detail,
                    editing = state.visualState == MemoryVisualState.Editing,
                    callbacks = callbacks,
                )
            }
        }
        if (state.compactDialogVisible) {
            MemoryCompactDialog(estimate = state.compactEstimate, callbacks = callbacks)
        }
        if (state.addDialogVisible) {
            MemoryAddDialog(callbacks = callbacks)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MemoryTopBar(searching: Boolean, callbacks: MemoryCallbacks) {
    var menuOpen by remember { mutableStateOf(false) }
    TopAppBar(
        title = {
            Column {
                Text(
                    text = stringResource(R.string.knotwork_memory_title),
                    style = MemoryType.appBarTitle,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = stringResource(R.string.knotwork_memory_subtitle),
                    style = MemoryType.appBarSubtitle,
                    color = KnotworkTheme.extended.onSurfaceMuted,
                )
            }
        },
        navigationIcon = {
            IconButton(onClick = callbacks.onBack) {
                Icon(
                    imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                    contentDescription = stringResource(R.string.knotwork_common_back),
                    tint = MaterialTheme.colorScheme.onSurface,
                )
            }
        },
        actions = {
            IconButton(onClick = callbacks.onSearchOpen) {
                Icon(
                    imageVector = Icons.Outlined.Search,
                    contentDescription = stringResource(R.string.knotwork_memory_search_cd),
                    tint = if (searching) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                )
            }
            Box {
                IconButton(onClick = { menuOpen = true }) {
                    Icon(
                        imageVector = Icons.Outlined.MoreVert,
                        contentDescription = stringResource(R.string.knotwork_memory_overflow_cd),
                        tint = MaterialTheme.colorScheme.onSurface,
                    )
                }
                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.knotwork_memory_export_all)) },
                        onClick = {
                            menuOpen = false
                            callbacks.onExportAll()
                        },
                    )
                }
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.surface,
            titleContentColor = MaterialTheme.colorScheme.onSurface,
        ),
    )
}

@Composable
private fun MemoryBody(state: MemoryViewState, callbacks: MemoryCallbacks, padding: PaddingValues) {
    when (state.visualState) {
        MemoryVisualState.Error -> MemoryError(state = state, callbacks = callbacks, padding = padding)
        MemoryVisualState.Empty -> MemoryEmpty(callbacks = callbacks, padding = padding)
        else -> MemoryPopulated(state = state, callbacks = callbacks, padding = padding)
    }
}

@Composable
private fun MemoryPopulated(state: MemoryViewState, callbacks: MemoryCallbacks, padding: PaddingValues) {
    // Search chrome is driven by the dedicated flag, not visualState, so opening
    // a detail sheet mid-search keeps the search field + relevance scores.
    val searching = state.searchActive
    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(padding),
        contentPadding = PaddingValues(bottom = KnotworkTheme.spacing.sp16),
    ) {
        item(key = "header") {
            if (searching) {
                MemorySearchField(query = state.searchQuery, callbacks = callbacks)
            } else {
                MemoryStatsCard(header = state.header, callbacks = callbacks)
            }
            MemoryCategoryRow(
                chips = state.categoryChips,
                selected = state.selectedCategory,
                onSelect = callbacks.onCategorySelect,
            )
            if (!searching) {
                MemorySortRow(state = state, callbacks = callbacks)
            }
        }
        if (searching && state.searchEmpty) {
            item(key = "no-matches") {
                MemoryNoMatches(query = state.searchQuery, onClear = callbacks.onClearSearch)
            }
        }
        state.sections.forEach { section ->
            // Search results are a single relevance-ordered list with no time
            // grouping, so their section carries a blank title and no header.
            if (section.title.isNotBlank()) {
                item(key = "section-${section.title}") {
                    MemorySectionHeader(title = section.title, count = section.count)
                }
            }
            items(count = section.rows.size, key = { section.rows[it].id }) { index ->
                MemoryListRow(row = section.rows[index], searching = searching, callbacks = callbacks)
            }
        }
    }
}

@Composable
private fun MemoryNoMatches(query: String, onClear: () -> Unit) {
    Box(modifier = Modifier.fillMaxWidth().padding(KnotworkTheme.spacing.sp8), contentAlignment = Alignment.Center) {
        EmptyState(
            title = stringResource(R.string.knotwork_memory_no_match_title),
            subtitle = stringResource(R.string.knotwork_memory_no_match_subtitle) +
                if (query.isNotBlank()) " (\"$query\")" else "",
            illustration = { },
            ctaLabel = stringResource(R.string.knotwork_memory_no_match_clear),
            onCtaClick = onClear,
        )
    }
}

@Composable
private fun MemorySearchField(query: String, callbacks: MemoryCallbacks) {
    val fieldCd = stringResource(R.string.knotwork_memory_search_cd)
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = KnotworkTheme.spacing.sp4)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(KnotworkTheme.spacing.sp2),
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = KnotworkTheme.spacing.sp2)
                .clip(KnotworkTheme.shapes.md)
                .background(KnotworkTheme.extended.surface2)
                .padding(horizontal = KnotworkTheme.spacing.sp3, vertical = KnotworkTheme.spacing.sp2),
        ) {
            Icon(Icons.Outlined.Search, contentDescription = null, tint = KnotworkTheme.extended.onSurfaceMuted)
            Box(modifier = Modifier.weight(1f)) {
                BasicTextField(
                    value = query,
                    onValueChange = callbacks.onSearchQueryChange,
                    singleLine = true,
                    textStyle = KnotworkTextStyles.BodyBase.copy(color = MaterialTheme.colorScheme.onSurface),
                    cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                    modifier = Modifier.fillMaxWidth().semantics { contentDescription = fieldCd },
                )
                if (query.isEmpty()) {
                    Text(
                        text = stringResource(R.string.knotwork_memory_search_placeholder),
                        style = KnotworkTextStyles.BodyBase,
                        color = KnotworkTheme.extended.onSurfaceMuted,
                    )
                }
            }
            if (query.isNotEmpty()) {
                IconButton(onClick = callbacks.onClearSearch) {
                    Icon(
                        Icons.Outlined.Close,
                        contentDescription = stringResource(R.string.knotwork_memory_search_clear_cd),
                        tint = KnotworkTheme.extended.onSurfaceMuted,
                    )
                }
            }
        }
        Text(
            text = stringResource(R.string.knotwork_memory_search_semantic),
            style = MemoryType.sortLabel,
            color = KnotworkTheme.extended.onSurfaceMuted,
            modifier = Modifier.padding(vertical = KnotworkTheme.spacing.sp1),
        )
    }
}

@Composable
private fun MemoryStatsCard(header: MemoryStatsHeader, callbacks: MemoryCallbacks) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = KnotworkTheme.spacing.sp4, vertical = KnotworkTheme.spacing.sp2)
            .clip(KnotworkTheme.shapes.md)
            .background(KnotworkTheme.extended.surface2)
            .padding(KnotworkTheme.spacing.sp4),
        verticalArrangement = Arrangement.spacedBy(KnotworkTheme.spacing.sp2),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = header.totalLabel,
                        style = MemoryType.overviewCount,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        text = stringResource(R.string.knotwork_memory_count_unit),
                        style = MemoryType.overviewUnit,
                        color = KnotworkTheme.extended.onSurfaceMuted,
                        modifier = Modifier.padding(bottom = 4.dp),
                    )
                }
                Text(
                    text = listOfNotNull(header.sizeLabel, header.lastCompactedLabel).joinToString("  ·  "),
                    style = MemoryType.overviewMeta,
                    color = KnotworkTheme.extended.onSurfaceMuted,
                )
            }
            KnotworkSecondaryButton(
                text = stringResource(R.string.knotwork_memory_compact),
                onClick = callbacks.onCompactClick,
                size = KnotworkButtonSize.Sm,
                leadingIcon = Icons.Outlined.AutoAwesome,
            )
        }
        if (header.segments.isNotEmpty()) {
            MemoryBreakdownBar(segments = header.segments)
            MemoryBreakdownLegend(segments = header.segments)
        }
    }
}

@Composable
private fun MemoryBreakdownBar(segments: List<MemoryBreakdownSegment>) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(BreakdownBarHeight)
            .clip(RoundedCornerShape(percent = 50)),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        segments.forEach { segment ->
            Box(
                modifier = Modifier
                    .weight(segment.fraction.coerceAtLeast(MIN_SEGMENT_FRACTION))
                    .fillMaxSize()
                    .background(sourceColor(segment.kind)),
            )
        }
    }
}

@Composable
private fun MemoryBreakdownLegend(segments: List<MemoryBreakdownSegment>) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(KnotworkTheme.spacing.sp3)) {
        segments.forEach { segment ->
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Box(
                    modifier = Modifier.size(
                        8.dp,
                    ).clip(RoundedCornerShape(percent = 50)).background(sourceColor(segment.kind)),
                )
                Text(
                    text = segment.label,
                    style = MemoryType.overviewLegend,
                    color = KnotworkTheme.extended.onSurfaceMuted,
                )
            }
        }
    }
}

@Composable
private fun MemoryCategoryRow(
    chips: List<MemoryCategoryChip>,
    selected: MemoryCategory,
    onSelect: (MemoryCategory) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = KnotworkTheme.spacing.sp4, vertical = KnotworkTheme.spacing.sp1),
        horizontalArrangement = Arrangement.spacedBy(KnotworkTheme.spacing.sp2),
    ) {
        chips.forEach { chip ->
            KnotworkFilterChip(
                label = stringResource(chip.category.labelRes()),
                selected = chip.category == selected,
                onClick = { onSelect(chip.category) },
                leadingIcon = if (chip.category == MemoryCategory.Pinned) Icons.Filled.PushPin else null,
                trailingCount = chip.count,
            )
        }
    }
}

@Composable
private fun MemorySortRow(state: MemoryViewState, callbacks: MemoryCallbacks) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(KnotworkTheme.spacing.sp3),
        modifier = Modifier.padding(horizontal = KnotworkTheme.spacing.sp4, vertical = KnotworkTheme.spacing.sp1),
    ) {
        Text(
            text = stringResource(R.string.knotwork_memory_sort_label).uppercase(),
            style = MemoryType.sortLabel,
            color = KnotworkTheme.extended.onSurfaceDim,
        )
        MemoryDropdown(
            label = stringResource(state.sortMode.labelRes()),
            options = MemorySortMode.entries,
            optionLabel = { stringResource(it.labelRes()) },
            onSelect = callbacks.onSortChange,
        )
        MemoryDropdown(
            label = stringResource(state.dateFilter.labelRes()),
            options = MemoryDateFilter.entries,
            optionLabel = { stringResource(it.labelRes()) },
            onSelect = callbacks.onDateFilterChange,
        )
    }
}

@Composable
private fun <T> MemoryDropdown(
    label: String,
    options: List<T>,
    optionLabel: @Composable (T) -> String,
    onSelect: (T) -> Unit,
) {
    var open by remember { mutableStateOf(false) }
    Box {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.clip(KnotworkTheme.shapes.sm).clickable {
                open = true
            }.padding(horizontal = 4.dp, vertical = 2.dp),
        ) {
            Text(text = label, style = MemoryType.control, color = MaterialTheme.colorScheme.onSurface)
            Icon(Icons.Outlined.ArrowDropDown, contentDescription = null, tint = KnotworkTheme.extended.onSurfaceMuted)
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            options.forEach { option ->
                DropdownMenuItem(
                    text = { Text(optionLabel(option)) },
                    onClick = {
                        open = false
                        onSelect(option)
                    },
                )
            }
        }
    }
}

@Composable
private fun MemorySectionHeader(title: String, count: Int) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(KnotworkTheme.spacing.sp2),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = KnotworkTheme.spacing.sp4, vertical = KnotworkTheme.spacing.sp2),
    ) {
        Text(
            text = title.uppercase(),
            style = MemoryType.groupHeader,
            color = KnotworkTheme.extended.onSurfaceMuted,
        )
        Text(
            text = count.toString(),
            style = MemoryType.groupCount,
            color = KnotworkTheme.extended.onSurfaceDim,
        )
        Box(modifier = Modifier.weight(1f).height(1.dp).background(KnotworkTheme.extended.outlineStrong))
    }
}

@Composable
private fun MemoryListRow(row: MemoryRow, searching: Boolean, callbacks: MemoryCallbacks) {
    Row(modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
        Box(modifier = Modifier.width(AccentBarWidth).fillMaxHeight().background(sourceColor(row.sourceKind)))
        Column(
            modifier = Modifier
                .weight(1f)
                .clickable { callbacks.onEntryClick(row.id) }
                .padding(horizontal = KnotworkTheme.spacing.sp4, vertical = KnotworkTheme.spacing.sp3),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = row.title,
                    style = MemoryType.cardTitle,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = { callbacks.onEntryPinToggle(row.id) }, modifier = Modifier.size(28.dp)) {
                    Icon(
                        imageVector = if (row.isPinned) Icons.Filled.PushPin else Icons.Outlined.PushPin,
                        contentDescription = stringResource(
                            if (row.isPinned) R.string.knotwork_memory_unpin else R.string.knotwork_memory_pin,
                        ),
                        tint = if (row.isPinned) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            KnotworkTheme.extended.onSurfaceMuted
                        },
                        modifier = Modifier.size(18.dp),
                    )
                }
            }
            Spacer(modifier = Modifier.height(CARD_TITLE_GAP))
            Text(
                text = row.body,
                style = MemoryType.cardBody,
                color = KnotworkTheme.extended.onSurface2,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(modifier = Modifier.height(CARD_BODY_GAP))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(KnotworkTheme.spacing.sp2),
            ) {
                MemorySourceBadge(kind = row.sourceKind)
                if (row.tags.isNotEmpty()) {
                    Text(
                        text = row.tags.joinToString("  ·  "),
                        style = MemoryType.cardTags,
                        color = KnotworkTheme.extended.onSurfaceDim,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                }
                Spacer(modifier = Modifier.weight(1f))
                if (searching && row.relevanceScore != null) {
                    Text(
                        text = row.relevanceScore,
                        style = MemoryType.score,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
                Text(
                    text = row.timestampLabel,
                    style = MemoryType.timestamp,
                    color = KnotworkTheme.extended.onSurfaceMuted,
                )
            }
        }
    }
}

@Composable
private fun MemorySourceBadge(kind: MemorySourceKind) {
    val color = sourceColor(kind)
    Text(
        text = stringResource(kind.labelRes()).uppercase(),
        style = MemoryType.sourceTag,
        color = color,
        modifier = Modifier
            .clip(KnotworkTheme.shapes.xs)
            .background(color.copy(alpha = 0.14f))
            .padding(horizontal = 6.dp, vertical = 2.dp),
    )
}

@Composable
private fun MemoryEmpty(callbacks: MemoryCallbacks, padding: PaddingValues) {
    Box(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
        EmptyState(
            title = stringResource(R.string.knotwork_memory_empty_title),
            subtitle = stringResource(R.string.knotwork_memory_empty_subtitle),
            ctaLabel = stringResource(R.string.knotwork_memory_empty_cta),
            onCtaClick = callbacks.onEmptyCta,
        )
    }
}

@Composable
private fun MemoryError(state: MemoryViewState, callbacks: MemoryCallbacks, padding: PaddingValues) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(KnotworkTheme.spacing.sp2),
        modifier = Modifier.fillMaxSize().padding(padding).padding(KnotworkTheme.spacing.sp6),
    ) {
        Icon(
            imageVector = Icons.Outlined.WarningAmber,
            contentDescription = null,
            tint = KnotworkTheme.extended.signalError,
            modifier = Modifier.size(KnotworkTheme.spacing.sp16),
        )
        EmptyState(
            title = stringResource(R.string.knotwork_memory_error_title),
            subtitle = state.errorMessage.orEmpty(),
            illustration = { },
            ctaLabel = stringResource(R.string.knotwork_memory_error_retry),
            onCtaClick = callbacks.onErrorRetry,
        )
    }
}

@Composable
private fun MemoryDetailSheet(detail: MemoryEntryDetail, editing: Boolean, callbacks: MemoryCallbacks) {
    var body by remember(detail.id, editing) { mutableStateOf(detail.body) }
    var tags by remember(detail.id, editing) { mutableStateOf(detail.tags) }
    var newTag by remember(detail.id, editing) { mutableStateOf("") }

    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.BottomCenter) {
        // Scrim — dims the surface behind the sheet and dismisses on tap-outside.
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.32f))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = callbacks.onCloseDetail,
                ),
        )
        Surface(
            color = KnotworkTheme.extended.surface1,
            tonalElevation = KnotworkTheme.elevation.el3,
            shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(
                modifier = Modifier
                    .padding(KnotworkTheme.spacing.sp4)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(KnotworkTheme.spacing.sp3),
            ) {
                Box(
                    modifier = Modifier
                        .padding(bottom = KnotworkTheme.spacing.sp1)
                        .width(36.dp)
                        .height(4.dp)
                        .clip(RoundedCornerShape(percent = 50))
                        .background(KnotworkTheme.extended.outlineStrong)
                        .align(Alignment.CenterHorizontally),
                )
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    MemorySourceBadge(kind = detail.sourceKind)
                    Spacer(modifier = Modifier.width(KnotworkTheme.spacing.sp2))
                    Text(
                        text = detail.tokenLabel,
                        style = MemoryType.cardTags,
                        color = KnotworkTheme.extended.onSurfaceMuted,
                    )
                    Spacer(modifier = Modifier.weight(1f))
                    IconButton(onClick = { callbacks.onEntryPinToggle(detail.id) }) {
                        Icon(
                            imageVector = if (detail.isPinned) Icons.Filled.PushPin else Icons.Outlined.PushPin,
                            contentDescription = stringResource(
                                if (detail.isPinned) R.string.knotwork_memory_unpin else R.string.knotwork_memory_pin,
                            ),
                            tint = if (detail.isPinned) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.onSurface
                            },
                        )
                    }
                    IconButton(onClick = callbacks.onCloseDetail) {
                        Icon(
                            Icons.Outlined.Close,
                            contentDescription = stringResource(R.string.knotwork_memory_detail_close),
                            tint = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                }
                Text(
                    text = detail.title,
                    style = MemoryType.detailTitle,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                if (editing) {
                    OutlinedTextField(
                        value = body,
                        onValueChange = { body = it },
                        textStyle = MemoryType.detailBody,
                        modifier = Modifier.fillMaxWidth(),
                    )
                } else {
                    Text(
                        text = detail.body,
                        style = MemoryType.detailBody,
                        color = KnotworkTheme.extended.onSurface2,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(KnotworkTheme.shapes.md)
                            .background(KnotworkTheme.extended.surface2)
                            .padding(KnotworkTheme.spacing.sp3),
                    )
                }
                MemoryTagEditor(
                    tags = if (editing) tags else detail.tags,
                    editing = editing,
                    newTag = newTag,
                    onNewTagChange = { newTag = it },
                    onAddTag = {
                        val t = newTag.trim()
                        if (t.isNotEmpty() && t !in tags) tags = tags + t
                        newTag = ""
                    },
                    onRemoveTag = { tags = tags - it },
                )
                MemoryDetailMeta(detail = detail)
                MemoryDetailActions(
                    editing = editing,
                    onDelete = { callbacks.onEntryDelete(detail.id) },
                    onCancel = if (editing) callbacks.onEntryEditCancel else callbacks.onCloseDetail,
                    onPrimary = {
                        if (editing) {
                            callbacks.onEntryEditCommit(
                                detail.id,
                                body,
                                tags,
                            )
                        } else {
                            callbacks.onEntryEditRequest(detail.id)
                        }
                    },
                )
            }
        }
    }
}

@Composable
private fun MemoryTagEditor(
    tags: List<String>,
    editing: Boolean,
    newTag: String,
    onNewTagChange: (String) -> Unit,
    onAddTag: () -> Unit,
    onRemoveTag: (String) -> Unit,
) {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(KnotworkTheme.spacing.sp1),
        verticalArrangement = Arrangement.spacedBy(KnotworkTheme.spacing.sp1),
    ) {
        tags.forEach { tag ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .clip(KnotworkTheme.shapes.sm)
                    .background(KnotworkTheme.extended.surface2)
                    .padding(horizontal = KnotworkTheme.spacing.sp2, vertical = 4.dp),
            ) {
                Text(text = tag, style = MemoryType.cardTags, color = MaterialTheme.colorScheme.onSurface)
                if (editing) {
                    Spacer(modifier = Modifier.width(4.dp))
                    Icon(
                        imageVector = Icons.Outlined.Close,
                        contentDescription = stringResource(R.string.knotwork_memory_tag_remove_cd),
                        tint = KnotworkTheme.extended.onSurfaceMuted,
                        modifier = Modifier.size(14.dp).clickable { onRemoveTag(tag) },
                    )
                }
            }
        }
        if (editing) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .clip(KnotworkTheme.shapes.sm)
                    .background(KnotworkTheme.extended.surface2)
                    .padding(horizontal = KnotworkTheme.spacing.sp2, vertical = 4.dp),
            ) {
                Icon(
                    Icons.Filled.Add,
                    contentDescription = null,
                    tint = KnotworkTheme.extended.onSurfaceMuted,
                    modifier = Modifier.size(14.dp),
                )
                Spacer(modifier = Modifier.width(4.dp))
                Box {
                    BasicTextField(
                        value = newTag,
                        onValueChange = onNewTagChange,
                        singleLine = true,
                        textStyle = MemoryType.cardTags.copy(color = MaterialTheme.colorScheme.onSurface),
                        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                        modifier = Modifier.width(64.dp),
                    )
                    if (newTag.isEmpty()) {
                        Text(
                            text = stringResource(R.string.knotwork_memory_tag_add),
                            style = MemoryType.cardTags,
                            color = KnotworkTheme.extended.onSurfaceMuted,
                        )
                    }
                }
                KnotworkTextButton(text = stringResource(R.string.knotwork_memory_tag_add_action), onClick = onAddTag)
            }
        }
    }
}

@Composable
private fun MemoryDetailMeta(detail: MemoryEntryDetail) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(KnotworkTheme.shapes.md)
            .background(KnotworkTheme.extended.surface2)
            .padding(KnotworkTheme.spacing.sp3),
        verticalArrangement = Arrangement.spacedBy(KnotworkTheme.spacing.sp1),
    ) {
        MemoryMetaRow(label = stringResource(R.string.knotwork_memory_meta_source), value = detail.sourceLabel)
        detail.learnedFromLabel?.let {
            MemoryMetaRow(label = stringResource(R.string.knotwork_memory_meta_learned_from), value = it)
        }
        MemoryMetaRow(label = stringResource(R.string.knotwork_memory_meta_captured), value = detail.capturedLabel)
        detail.usedInLabel?.let {
            MemoryMetaRow(label = stringResource(R.string.knotwork_memory_meta_used_in), value = it)
        }
    }
}

@Composable
private fun MemoryMetaRow(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(KnotworkTheme.spacing.sp3)) {
        Text(
            text = label.uppercase(),
            style = MemoryType.provKey,
            color = KnotworkTheme.extended.onSurfaceMuted,
            modifier = Modifier.width(96.dp),
        )
        Text(
            text = value,
            style = MemoryType.provValue,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun MemoryDetailActions(editing: Boolean, onDelete: () -> Unit, onCancel: () -> Unit, onPrimary: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        KnotworkSecondaryButton(
            text = stringResource(R.string.knotwork_memory_detail_delete),
            onClick = onDelete,
            destructive = true,
        )
        Spacer(modifier = Modifier.weight(1f))
        KnotworkTextButton(text = stringResource(R.string.knotwork_memory_detail_cancel), onClick = onCancel)
        Spacer(modifier = Modifier.width(KnotworkTheme.spacing.sp2))
        KnotworkPrimaryButton(
            text = stringResource(
                if (editing) R.string.knotwork_memory_detail_save else R.string.knotwork_memory_detail_edit,
            ),
            onClick = onPrimary,
        )
    }
}

@Composable
private fun MemoryCompactDialog(estimate: CompactionEstimateView?, callbacks: MemoryCallbacks) {
    AlertDialog(
        onDismissRequest = callbacks.onCompactDismiss,
        title = { Text(stringResource(R.string.knotwork_memory_compact_title), style = MemoryType.compactTitle) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(KnotworkTheme.spacing.sp3)) {
                Text(
                    text = stringResource(R.string.knotwork_memory_compact_body),
                    style = MemoryType.compactBody,
                    color = KnotworkTheme.extended.onSurface2,
                )
                if (estimate != null) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(KnotworkTheme.shapes.md)
                            .background(KnotworkTheme.extended.surface2)
                            .padding(KnotworkTheme.spacing.sp3),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        MemoryEstimateCell(
                            value = estimate.removedLabel,
                            label = stringResource(R.string.knotwork_memory_compact_removed),
                        )
                        MemoryEstimateCell(
                            value = estimate.freedLabel,
                            label = stringResource(R.string.knotwork_memory_compact_freed),
                        )
                        MemoryEstimateCell(
                            value = estimate.runtimeLabel,
                            label = stringResource(R.string.knotwork_memory_compact_runtime),
                        )
                    }
                }
            }
        },
        confirmButton = {
            KnotworkPrimaryButton(
                text = stringResource(R.string.knotwork_memory_compact_confirm),
                onClick = callbacks.onCompactConfirm,
            )
        },
        dismissButton = {
            KnotworkTextButton(
                text = stringResource(R.string.knotwork_memory_detail_cancel),
                onClick = callbacks.onCompactDismiss,
            )
        },
    )
}

@Composable
private fun MemoryEstimateCell(value: String, label: String) {
    Column(horizontalAlignment = Alignment.Start) {
        Text(text = value, style = MemoryType.statValue, color = MaterialTheme.colorScheme.onSurface)
        Text(
            text = label.uppercase(),
            style = MemoryType.statLabel,
            color = KnotworkTheme.extended.onSurfaceMuted,
        )
    }
}

@Composable
private fun MemoryAddDialog(callbacks: MemoryCallbacks) {
    var text by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = callbacks.onAddDismiss,
        title = { Text(stringResource(R.string.knotwork_memory_add_title), style = MemoryType.compactTitle) },
        text = {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(KnotworkTheme.shapes.md)
                    .background(KnotworkTheme.extended.surface2)
                    .padding(KnotworkTheme.spacing.sp3),
            ) {
                BasicTextField(
                    value = text,
                    onValueChange = { text = it },
                    textStyle = MemoryType.detailBody.copy(color = MaterialTheme.colorScheme.onSurface),
                    cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                    modifier = Modifier.fillMaxWidth(),
                )
                if (text.isEmpty()) {
                    Text(
                        text = stringResource(R.string.knotwork_memory_add_placeholder),
                        style = MemoryType.detailBody,
                        color = KnotworkTheme.extended.onSurfaceMuted,
                    )
                }
            }
        },
        confirmButton = {
            KnotworkPrimaryButton(
                text = stringResource(R.string.knotwork_memory_add_save),
                onClick = { callbacks.onAddConfirm(text) },
                enabled = text.isNotBlank(),
            )
        },
        dismissButton = {
            KnotworkTextButton(
                text = stringResource(R.string.knotwork_memory_detail_cancel),
                onClick = callbacks.onAddDismiss,
            )
        },
    )
}

/** Maps a [MemorySourceKind] to its accent colour. */
@Composable
private fun sourceColor(kind: MemorySourceKind): Color = when (kind) {
    MemorySourceKind.Auto -> KnotworkTheme.extended.memoryAuto
    MemorySourceKind.Compaction -> KnotworkTheme.extended.memoryCompaction
    MemorySourceKind.Manual -> MaterialTheme.colorScheme.primary
    MemorySourceKind.Unknown -> KnotworkTheme.extended.onSurfaceMuted
}

private fun MemorySourceKind.labelRes(): Int = when (this) {
    MemorySourceKind.Auto -> R.string.knotwork_memory_badge_auto
    MemorySourceKind.Manual -> R.string.knotwork_memory_badge_manual
    MemorySourceKind.Compaction -> R.string.knotwork_memory_badge_compact
    MemorySourceKind.Unknown -> R.string.knotwork_memory_badge_unknown
}

private fun MemoryCategory.labelRes(): Int = when (this) {
    MemoryCategory.All -> R.string.knotwork_memory_category_all
    MemoryCategory.Pinned -> R.string.knotwork_memory_category_pinned
    MemoryCategory.Auto -> R.string.knotwork_memory_category_auto
    MemoryCategory.Manual -> R.string.knotwork_memory_category_manual
    MemoryCategory.Compaction -> R.string.knotwork_memory_category_compaction
}

private fun MemorySortMode.labelRes(): Int = when (this) {
    MemorySortMode.Recent -> R.string.knotwork_memory_sort_recent
    MemorySortMode.Relevance -> R.string.knotwork_memory_sort_relevance
    MemorySortMode.Alphabetical -> R.string.knotwork_memory_sort_alphabetical
}

private fun MemoryDateFilter.labelRes(): Int = when (this) {
    MemoryDateFilter.All -> R.string.knotwork_memory_filter_date_all
    MemoryDateFilter.Last7Days -> R.string.knotwork_memory_filter_date_7d
    MemoryDateFilter.Last30Days -> R.string.knotwork_memory_filter_date_30d
}
