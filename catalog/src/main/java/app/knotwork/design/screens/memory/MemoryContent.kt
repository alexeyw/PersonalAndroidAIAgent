@file:Suppress("MatchingDeclarationName") // Hosts MemoryContent and its helpers.

package app.knotwork.design.screens.memory

import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.FileDownload
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import app.knotwork.design.R
import app.knotwork.design.components.buttons.KnotworkPrimaryButton
import app.knotwork.design.components.buttons.KnotworkSecondaryButton
import app.knotwork.design.components.buttons.KnotworkTextButton
import app.knotwork.design.components.chips.ChipStyle
import app.knotwork.design.components.chips.KnotworkChip
import app.knotwork.design.components.chips.KnotworkFilterChip
import app.knotwork.design.components.lists.MemoryEntryRow
import app.knotwork.design.components.misc.EmptyState
import app.knotwork.design.components.misc.StripedPlaceholder
import app.knotwork.design.theme.KnotworkTheme
import app.knotwork.design.tokens.KnotworkTextStyles

/** Length of the FLIP rank-shuffle animation per `compose/components/animations.md`. */
private const val RANK_SHUFFLE_DURATION_MS = 320

/**
 * Reduced-motion fallback duration for the FLIP rank-shuffle (per
 * `decisions.md §14`). Matches the 80ms alpha-only crossfade window
 * used by [app.knotwork.design.a11y.respectReducedMotionTransitions].
 */
private const val REDUCED_MOTION_FALLBACK_MS = 80

/** Height of the trailing skeleton row used while paginating. */
private val SkeletonRowHeight = 88.dp

/**
 * Stateless Knotwork memory surface. Mirrors
 * `compose/screens/README.md §C6 · Memory`.
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
            topBar = {
                app.knotwork.design.components.topbar.KnotworkTopAppBarShell {
                    if (state.selectionMode) {
                        MemorySelectionTopBar(selectedCount = state.selectedIds.size, callbacks = callbacks)
                    } else {
                        MemoryTopBar(onBack = callbacks.onBack)
                    }
                }
            },
            modifier = Modifier.fillMaxSize(),
        ) { padding ->
            MemoryBody(state = state, callbacks = callbacks, padding = padding)
        }
        if (state.visualState == MemoryVisualState.EntryExpanded ||
            state.visualState == MemoryVisualState.Editing
        ) {
            state.expandedEntry?.let { detail ->
                MemoryDetailOverlay(
                    detail = detail,
                    editing = state.visualState == MemoryVisualState.Editing,
                    callbacks = callbacks,
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MemoryTopBar(onBack: () -> Unit) {
    TopAppBar(
        title = {
            Text(
                text = stringResource(R.string.knotwork_memory_title),
                style = KnotworkTextStyles.TitleLg,
                color = MaterialTheme.colorScheme.onSurface,
            )
        },
        navigationIcon = {
            androidx.compose.material3.IconButton(onClick = onBack) {
                Icon(
                    imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                    contentDescription = stringResource(R.string.knotwork_common_back),
                    tint = MaterialTheme.colorScheme.onSurface,
                )
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.surface,
            titleContentColor = MaterialTheme.colorScheme.onSurface,
        ),
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MemorySelectionTopBar(selectedCount: Int, callbacks: MemoryCallbacks) {
    TopAppBar(
        title = {
            Text(
                text = pluralStringResource(
                    R.plurals.knotwork_memory_selection_count,
                    selectedCount,
                    selectedCount,
                ),
                style = KnotworkTextStyles.TitleLg,
                color = MaterialTheme.colorScheme.onSurface,
            )
        },
        navigationIcon = {
            IconButton(onClick = callbacks.onExitSelection) {
                Icon(
                    imageVector = Icons.Outlined.Close,
                    contentDescription = stringResource(R.string.knotwork_memory_selection_exit_cd),
                    tint = MaterialTheme.colorScheme.onSurface,
                )
            }
        },
        actions = {
            IconButton(onClick = callbacks.onPinSelected) {
                Icon(
                    imageVector = Icons.Filled.PushPin,
                    contentDescription = stringResource(R.string.knotwork_memory_selection_pin_all),
                    tint = MaterialTheme.colorScheme.onSurface,
                )
            }
            IconButton(onClick = callbacks.onUnpinSelected) {
                Icon(
                    imageVector = Icons.Outlined.PushPin,
                    contentDescription = stringResource(R.string.knotwork_memory_selection_unpin_all),
                    tint = MaterialTheme.colorScheme.onSurface,
                )
            }
            IconButton(onClick = callbacks.onExportSelected) {
                Icon(
                    imageVector = Icons.Outlined.FileDownload,
                    contentDescription = stringResource(R.string.knotwork_memory_selection_export),
                    tint = MaterialTheme.colorScheme.onSurface,
                )
            }
            IconButton(onClick = callbacks.onDeleteSelected) {
                Icon(
                    imageVector = Icons.Outlined.DeleteOutline,
                    contentDescription = stringResource(R.string.knotwork_memory_selection_delete),
                    tint = KnotworkTheme.extended.signalError,
                )
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
    Column(modifier = Modifier.fillMaxSize().padding(padding)) {
        // Hide the search / sort / filter chrome while multi-selecting. These
        // controls narrow `state.entries`; if the user changed them mid-selection
        // a selected row could vanish from the list while its id lingered in
        // `selectedIds`, so a bulk action would silently affect a hidden entry.
        // Freezing the chrome during selection keeps `selectedIds` a subset of
        // the visible rows (contextual-action-bar pattern).
        val showHeaderChrome = state.visualState != MemoryVisualState.Error &&
            state.visualState != MemoryVisualState.Empty &&
            !state.selectionMode
        if (showHeaderChrome) {
            MemorySearchField(query = state.searchQuery, onQueryChange = callbacks.onSearchQueryChange)
            MemorySortRow(active = state.sortMode, onChange = callbacks.onSortChange)
            MemoryFilterRow(state = state, callbacks = callbacks)
        }
        when (state.visualState) {
            MemoryVisualState.Empty -> MemoryEmpty(callbacks = callbacks)
            MemoryVisualState.Error -> MemoryError(state = state, callbacks = callbacks)
            MemoryVisualState.Searching -> if (state.entries.isEmpty()) {
                MemoryNoMatches(callbacks = callbacks, query = state.searchQuery)
            } else {
                MemoryList(state = state, callbacks = callbacks, showSkeleton = false)
            }
            MemoryVisualState.LoadingMore -> MemoryList(state = state, callbacks = callbacks, showSkeleton = true)
            else -> MemoryList(state = state, callbacks = callbacks, showSkeleton = false)
        }
    }
}

@Composable
private fun MemorySearchField(query: String, onQueryChange: (String) -> Unit) {
    val fieldCd = stringResource(R.string.knotwork_memory_search_cd)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(KnotworkTheme.spacing.sp2),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = KnotworkTheme.spacing.sp4, vertical = KnotworkTheme.spacing.sp2)
            .clip(KnotworkTheme.shapes.md)
            .background(color = KnotworkTheme.extended.surface2)
            .padding(horizontal = KnotworkTheme.spacing.sp3, vertical = KnotworkTheme.spacing.sp2),
    ) {
        Icon(
            imageVector = Icons.Outlined.Search,
            contentDescription = null,
            tint = KnotworkTheme.extended.onSurfaceMuted,
        )
        Box(modifier = Modifier.weight(1f)) {
            BasicTextField(
                value = query,
                onValueChange = onQueryChange,
                singleLine = true,
                textStyle = KnotworkTextStyles.BodyBase.copy(color = MaterialTheme.colorScheme.onSurface),
                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                modifier = Modifier
                    .fillMaxWidth()
                    .semantics { contentDescription = fieldCd },
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
            IconButton(onClick = { onQueryChange("") }) {
                Icon(
                    imageVector = Icons.Outlined.Close,
                    contentDescription = stringResource(R.string.knotwork_memory_search_clear_cd),
                    tint = KnotworkTheme.extended.onSurfaceMuted,
                )
            }
        }
    }
}

@Composable
private fun MemorySortRow(active: MemorySortMode, onChange: (MemorySortMode) -> Unit) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(KnotworkTheme.spacing.sp2),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = KnotworkTheme.spacing.sp4, vertical = KnotworkTheme.spacing.sp1),
    ) {
        MemorySortMode.entries.forEach { mode ->
            val label = stringResource(
                when (mode) {
                    MemorySortMode.Recent -> R.string.knotwork_memory_sort_recent
                    MemorySortMode.Relevance -> R.string.knotwork_memory_sort_relevance
                    MemorySortMode.Alphabetical -> R.string.knotwork_memory_sort_alphabetical
                },
            )
            KnotworkChip(
                label = label,
                selected = mode == active,
                onClick = { onChange(mode) },
            )
        }
    }
}

/**
 * Filter chip row beneath the sort chips: a single-select date-range group,
 * a multi-select source group, and a pinned-only toggle. The catalog only
 * reflects the active selection and emits toggle events — the screen owns the
 * actual filtering (and the clock for date ranges).
 */
@Composable
private fun MemoryFilterRow(state: MemoryViewState, callbacks: MemoryCallbacks) {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(KnotworkTheme.spacing.sp2),
        verticalArrangement = Arrangement.spacedBy(KnotworkTheme.spacing.sp1),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = KnotworkTheme.spacing.sp4, vertical = KnotworkTheme.spacing.sp1),
    ) {
        MemoryDateFilter.entries.forEach { filter ->
            val label = stringResource(
                when (filter) {
                    MemoryDateFilter.All -> R.string.knotwork_memory_filter_date_all
                    MemoryDateFilter.Last7Days -> R.string.knotwork_memory_filter_date_7d
                    MemoryDateFilter.Last30Days -> R.string.knotwork_memory_filter_date_30d
                },
            )
            KnotworkFilterChip(
                label = label,
                selected = filter == state.dateFilter,
                onClick = { callbacks.onDateFilterChange(filter) },
            )
        }
        MemorySourceFilter.entries.forEach { source ->
            val label = stringResource(
                when (source) {
                    MemorySourceFilter.Auto -> R.string.knotwork_memory_filter_source_auto
                    MemorySourceFilter.Manual -> R.string.knotwork_memory_filter_source_manual
                    MemorySourceFilter.Compaction -> R.string.knotwork_memory_filter_source_compaction
                },
            )
            KnotworkFilterChip(
                label = label,
                selected = source in state.sourceFilters,
                onClick = { callbacks.onSourceFilterToggle(source) },
            )
        }
        KnotworkFilterChip(
            label = stringResource(R.string.knotwork_memory_filter_pinned_only),
            selected = state.pinnedOnly,
            onClick = callbacks.onPinnedOnlyToggle,
        )
    }
}

@Composable
private fun MemoryEmpty(callbacks: MemoryCallbacks) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        EmptyState(
            title = stringResource(R.string.knotwork_memory_empty_title),
            subtitle = stringResource(R.string.knotwork_memory_empty_subtitle),
            ctaLabel = stringResource(R.string.knotwork_memory_empty_cta),
            onCtaClick = callbacks.onEmptyCta,
        )
    }
}

@Composable
private fun MemoryNoMatches(callbacks: MemoryCallbacks, query: String) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        EmptyState(
            title = stringResource(R.string.knotwork_memory_no_match_title),
            subtitle = stringResource(R.string.knotwork_memory_no_match_subtitle) +
                (if (query.isNotEmpty()) " (\"$query\")" else ""),
            illustration = { /* no striped illustration */ },
            ctaLabel = stringResource(R.string.knotwork_memory_no_match_clear),
            onCtaClick = callbacks.onClearSearch,
        )
    }
}

@Composable
private fun MemoryError(state: MemoryViewState, callbacks: MemoryCallbacks) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(KnotworkTheme.spacing.sp2),
        modifier = Modifier.fillMaxSize().padding(KnotworkTheme.spacing.sp6),
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
            illustration = { /* icon already drawn */ },
            ctaLabel = stringResource(R.string.knotwork_memory_error_retry),
            onCtaClick = callbacks.onErrorRetry,
        )
        KnotworkTextButton(
            text = stringResource(R.string.knotwork_memory_error_open_diagnostics),
            onClick = callbacks.onErrorOpenDiagnostics,
        )
    }
}

@Composable
private fun MemoryList(state: MemoryViewState, callbacks: MemoryCallbacks, showSkeleton: Boolean) {
    val reducedMotion = KnotworkTheme.a11y.reducedMotion()
    val placementDuration = if (reducedMotion) REDUCED_MOTION_FALLBACK_MS else RANK_SHUFFLE_DURATION_MS
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(vertical = KnotworkTheme.spacing.sp2),
        verticalArrangement = Arrangement.spacedBy(KnotworkTheme.spacing.sp1),
    ) {
        items(items = state.entries, key = { it.id }) { row ->
            val selected = row.id in state.selectedIds
            MemoryEntryRow(
                title = row.title,
                body = row.body,
                tags = row.tags,
                relevanceScore = row.relevanceScore,
                lastAccessed = row.lastAccessed,
                onClick = {
                    if (state.selectionMode) callbacks.onToggleSelect(row.id) else callbacks.onEntryClick(row.id)
                },
                // FLIP rank-shuffle: `LazyColumn.items(key = …)` already
                // animates moves via `Modifier.animateItem`; bumping the
                // duration here matches the spec's `motionLg` budget.
                // Under reduced-motion (`decisions.md §14`) the placement
                // animation collapses to an 80ms crossfade-equivalent so
                // re-ranked rows still settle, just without the long slide.
                modifier = Modifier.animateItem(
                    placementSpec = tween(durationMillis = placementDuration),
                ),
                isPinned = row.isPinned,
                selectionMode = state.selectionMode,
                selected = selected,
                onLongClick = { callbacks.onEntryLongPress(row.id) },
            )
        }
        if (showSkeleton) {
            item {
                StripedPlaceholder(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(SkeletonRowHeight)
                        .padding(KnotworkTheme.spacing.sp4),
                )
            }
        }
    }
}

@Composable
private fun MemoryDetailOverlay(detail: MemoryEntryDetail, editing: Boolean, callbacks: MemoryCallbacks) {
    Surface(
        color = KnotworkTheme.extended.surface1,
        tonalElevation = KnotworkTheme.elevation.el3,
        shape = KnotworkTheme.shapes.lg,
        modifier = Modifier
            .fillMaxSize()
            .padding(top = KnotworkTheme.spacing.sp10),
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(KnotworkTheme.spacing.sp3),
            modifier = Modifier.fillMaxSize().padding(KnotworkTheme.spacing.sp4),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = detail.title,
                    style = KnotworkTextStyles.TitleLg,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = callbacks.onCloseDetail) {
                    Icon(
                        imageVector = Icons.Outlined.Close,
                        contentDescription = stringResource(R.string.knotwork_memory_detail_close),
                        tint = MaterialTheme.colorScheme.onSurface,
                    )
                }
            }
            if (editing) {
                var draft by remember(detail.id) { mutableStateOf(detail.body) }
                OutlinedTextField(
                    value = draft,
                    onValueChange = { draft = it },
                    modifier = Modifier.fillMaxWidth().height(MEMORY_EDIT_FIELD_HEIGHT),
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(KnotworkTheme.spacing.sp2),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    KnotworkSecondaryButton(
                        text = stringResource(R.string.knotwork_memory_detail_close),
                        onClick = callbacks.onEntryEditCancel,
                    )
                    Spacer(modifier = Modifier.weight(1f))
                    KnotworkPrimaryButton(
                        text = stringResource(R.string.knotwork_memory_detail_edit),
                        onClick = { callbacks.onEntryEditCommit(detail.id, draft) },
                    )
                }
            } else {
                Text(
                    text = detail.body,
                    style = KnotworkTextStyles.BodyBase,
                    color = KnotworkTheme.extended.onSurface2,
                )
                if (detail.tags.isNotEmpty()) {
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(KnotworkTheme.spacing.sp1),
                        verticalArrangement = Arrangement.spacedBy(KnotworkTheme.spacing.sp1),
                    ) {
                        detail.tags.forEach { tag ->
                            // `screens/README.md §C6` calls for `KnotworkChip(Tonal)` in
                            // the detail sheet (filled accent), distinct from the
                            // outline-style chips on `MemoryEntryRow` list items.
                            KnotworkChip(label = tag, style = ChipStyle.Tonal)
                        }
                    }
                }
                Text(
                    text = detail.lastAccessed,
                    style = KnotworkTextStyles.Caption,
                    color = KnotworkTheme.extended.onSurfaceMuted,
                )
                Spacer(modifier = Modifier.weight(1f))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(KnotworkTheme.spacing.sp2),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    KnotworkTextButton(
                        text = stringResource(R.string.knotwork_memory_detail_edit),
                        onClick = { callbacks.onEntryEditRequest(detail.id) },
                    )
                    KnotworkTextButton(
                        text = stringResource(
                            if (detail.isPinned) {
                                R.string.knotwork_memory_detail_unpin
                            } else {
                                R.string.knotwork_memory_detail_pin
                            },
                        ),
                        onClick = { callbacks.onEntryPin(detail.id) },
                    )
                    Spacer(modifier = Modifier.weight(1f))
                    KnotworkTextButton(
                        text = stringResource(R.string.knotwork_memory_detail_delete),
                        onClick = { callbacks.onEntryDelete(detail.id) },
                        destructive = true,
                    )
                }
            }
        }
    }
}

/** Height of the multi-line entry-edit field rendered inside the detail sheet. */
private val MEMORY_EDIT_FIELD_HEIGHT = 200.dp
