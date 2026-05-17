@file:Suppress("MatchingDeclarationName") // File hosts PipelineLibraryContent and its helper composables.

package app.knotwork.design.screens.pipelines

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import app.knotwork.design.R
import app.knotwork.design.components.buttons.KnotworkSecondaryButton
import app.knotwork.design.components.buttons.KnotworkTextButton
import app.knotwork.design.components.chips.KnotworkChip
import app.knotwork.design.components.lists.PipelineListRow
import app.knotwork.design.components.lists.PipelineSwipeAction
import app.knotwork.design.components.misc.EmptyState
import app.knotwork.design.components.misc.StripedPlaceholder
import app.knotwork.design.theme.KnotworkTheme
import app.knotwork.design.tokens.KnotworkTextStyles

/** Number of skeleton rows rendered while the repository is loading. */
private const val LOADING_SKELETON_ROWS = 3

/** Height of the skeleton row used while the repository is loading. */
private val SkeletonRowHeight = 72.dp

/**
 * Stateless Knotwork pipeline-library surface. Drives the 7 documented
 * states (`compose/screens/README.md §C3`) deterministically from [state];
 * the caller in `:app/PipelineLibraryScreen` owns navigation, the
 * `OrchestratorViewModel`, and any nav-graph wiring.
 *
 * Layout (per spec):
 *  - `TopAppBar` titled "Pipelines" — replaced by [MultiSelectToolbar] when
 *    [PipelineLibraryVisualState.MultiSelect] is active.
 *  - Sticky body header: inline search field + filter-chip row.
 *  - Body switches on [PipelineLibraryViewState.visualState]:
 *    - Empty / Error → centred [EmptyState].
 *    - Loading → skeleton rows.
 *    - Populated / Filtering / SwipeOpen / MultiSelect → `LazyColumn` of
 *      [PipelineListRow]s. Filtering with zero results renders an inline
 *      "no matches" tile.
 *  - Extended FAB "New pipeline" is hidden during Error / MultiSelect.
 *
 * @param state immutable visual snapshot — see [PipelineLibraryViewState].
 * @param callbacks bundle of one-shot event handlers; defaults to no-op.
 * @param modifier optional layout modifier applied to the screen root.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PipelineLibraryContent(
    state: PipelineLibraryViewState,
    modifier: Modifier = Modifier,
    callbacks: PipelineLibraryCallbacks = noopPipelineLibraryCallbacks(),
) {
    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.surface,
        topBar = {
            if (state.visualState == PipelineLibraryVisualState.MultiSelect) {
                MultiSelectToolbar(state = state, callbacks = callbacks)
            } else {
                LibraryTopBar()
            }
        },
        floatingActionButton = {
            // Use the `text/icon` overload (instead of the trailing-lambda
            // form) so the FAB renders in its final size on first frame —
            // the trailing-lambda variant first measures the content and
            // then animates the expansion, which produced the "FAB starts
            // mid-screen and slides to its slot" jank reported in QA.
            if (!state.isFabHidden) {
                ExtendedFloatingActionButton(
                    onClick = callbacks.onNewPipeline,
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                    icon = {
                        Icon(
                            imageVector = Icons.Outlined.Add,
                            contentDescription = stringResource(R.string.knotwork_library_new_pipeline_cd),
                        )
                    },
                    text = {
                        Text(
                            text = stringResource(R.string.knotwork_library_new_pipeline_label),
                            style = KnotworkTextStyles.LabelLg,
                        )
                    },
                )
            }
        },
    ) { padding ->
        LibraryBody(state = state, callbacks = callbacks, padding = padding)
    }
}

/** Hides the FAB in the visual states where it has no meaningful action. */
private val PipelineLibraryViewState.isFabHidden: Boolean
    get() = visualState == PipelineLibraryVisualState.Error ||
        visualState == PipelineLibraryVisualState.MultiSelect

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LibraryTopBar() {
    TopAppBar(
        title = {
            Text(
                text = stringResource(R.string.knotwork_library_title),
                style = KnotworkTextStyles.TitleLg,
                color = MaterialTheme.colorScheme.onSurface,
            )
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.surface,
            titleContentColor = MaterialTheme.colorScheme.onSurface,
        ),
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MultiSelectToolbar(state: PipelineLibraryViewState, callbacks: PipelineLibraryCallbacks) {
    TopAppBar(
        title = {
            Text(
                text = androidx.compose.ui.res.pluralStringResource(
                    R.plurals.knotwork_library_multi_select_count,
                    state.selectedCount,
                    state.selectedCount,
                ),
                style = KnotworkTextStyles.TitleMd,
                color = MaterialTheme.colorScheme.onSurface,
            )
        },
        navigationIcon = {
            IconButton(onClick = callbacks.onMultiSelectCancel) {
                Icon(
                    imageVector = Icons.Outlined.Close,
                    contentDescription = stringResource(R.string.knotwork_library_multi_select_cancel),
                    tint = MaterialTheme.colorScheme.onSurface,
                )
            }
        },
        actions = {
            KnotworkTextButton(
                text = stringResource(R.string.knotwork_library_multi_select_archive),
                onClick = callbacks.onMultiSelectArchive,
            )
            KnotworkTextButton(
                text = stringResource(R.string.knotwork_library_multi_select_delete),
                onClick = callbacks.onMultiSelectDelete,
                destructive = true,
            )
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = KnotworkTheme.extended.surface1,
            titleContentColor = MaterialTheme.colorScheme.onSurface,
        ),
    )
}

@Composable
private fun LibraryBody(state: PipelineLibraryViewState, callbacks: PipelineLibraryCallbacks, padding: PaddingValues) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(padding),
    ) {
        if (state.visualState != PipelineLibraryVisualState.Error) {
            LibrarySearchField(query = state.searchQuery, onQueryChange = callbacks.onSearchQueryChange)
            LibraryFilterRow(
                active = state.activeFilter,
                onChange = callbacks.onFilterChange,
            )
            if (state.visualState == PipelineLibraryVisualState.Filtering) {
                FilterCountLine(visible = state.pipelines.size, total = state.totalCount)
            }
        }
        when (state.visualState) {
            PipelineLibraryVisualState.Empty -> LibraryEmptyState(callbacks = callbacks)
            PipelineLibraryVisualState.Loading -> LibraryLoadingState()
            PipelineLibraryVisualState.Error -> LibraryErrorState(state = state, callbacks = callbacks)
            PipelineLibraryVisualState.Filtering -> {
                if (state.pipelines.isEmpty()) {
                    LibraryNoMatchState(callbacks = callbacks, query = state.searchQuery)
                } else {
                    LibraryList(state = state, callbacks = callbacks)
                }
            }
            else -> LibraryList(state = state, callbacks = callbacks)
        }
    }
}

@Composable
private fun LibrarySearchField(query: String, onQueryChange: (String) -> Unit) {
    val fieldCd = stringResource(R.string.knotwork_library_search_cd)
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
            modifier = Modifier.size(SEARCH_ICON_SIZE),
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
                    text = stringResource(R.string.knotwork_library_search_placeholder),
                    style = KnotworkTextStyles.BodyBase,
                    color = KnotworkTheme.extended.onSurfaceMuted,
                    maxLines = 1,
                )
            }
        }
        if (query.isNotEmpty()) {
            IconButton(onClick = { onQueryChange("") }) {
                Icon(
                    imageVector = Icons.Outlined.Close,
                    contentDescription = stringResource(R.string.knotwork_library_search_clear_cd),
                    tint = KnotworkTheme.extended.onSurfaceMuted,
                    modifier = Modifier.size(SEARCH_ICON_SIZE),
                )
            }
        }
    }
}

/** Leading / trailing search-field icon size. */
private val SEARCH_ICON_SIZE = 20.dp

@Composable
private fun LibraryFilterRow(active: PipelineLibraryFilter, onChange: (PipelineLibraryFilter) -> Unit) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(KnotworkTheme.spacing.sp2),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = KnotworkTheme.spacing.sp4, vertical = KnotworkTheme.spacing.sp1),
    ) {
        PipelineLibraryFilter.entries.forEach { entry ->
            // The `Shared` filter is disabled until the sync backend lands —
            // it stays in the chip row to advertise the affordance but the
            // catalog refuses to fire the callback.
            val disabled = entry == PipelineLibraryFilter.Shared
            KnotworkChip(
                label = filterLabel(entry),
                selected = entry == active,
                enabled = !disabled,
                onClick = if (disabled) {
                    null
                } else {
                    { onChange(entry) }
                },
            )
        }
    }
}

/** Subtitle line beneath the filter chips: "X of Y" — only rendered while filtering. */
@Composable
private fun FilterCountLine(visible: Int, total: Int) {
    Text(
        text = stringResource(R.string.knotwork_library_filter_count, visible, total),
        style = KnotworkTextStyles.BodySm,
        color = KnotworkTheme.extended.onSurfaceMuted,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = KnotworkTheme.spacing.sp4, vertical = KnotworkTheme.spacing.sp1),
    )
}

@Composable
private fun LibraryEmptyState(callbacks: PipelineLibraryCallbacks) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(KnotworkTheme.spacing.sp2),
        modifier = Modifier
            .fillMaxSize()
            .padding(KnotworkTheme.spacing.sp6),
    ) {
        EmptyState(
            title = stringResource(R.string.knotwork_library_empty_title),
            subtitle = stringResource(R.string.knotwork_library_empty_subtitle),
            ctaLabel = stringResource(R.string.knotwork_library_empty_cta_new),
            onCtaClick = callbacks.onNewPipeline,
        )
        KnotworkSecondaryButton(
            text = stringResource(R.string.knotwork_library_empty_cta_templates),
            onClick = callbacks.onBrowseTemplates,
        )
    }
}

@Composable
private fun LibraryLoadingState() {
    Column(
        verticalArrangement = Arrangement.spacedBy(KnotworkTheme.spacing.sp2),
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = KnotworkTheme.spacing.sp4, vertical = KnotworkTheme.spacing.sp2),
    ) {
        repeat(LOADING_SKELETON_ROWS) {
            StripedPlaceholder(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(SkeletonRowHeight),
            )
        }
    }
}

@Composable
private fun LibraryErrorState(state: PipelineLibraryViewState, callbacks: PipelineLibraryCallbacks) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(KnotworkTheme.spacing.sp2),
        modifier = Modifier
            .fillMaxSize()
            .padding(KnotworkTheme.spacing.sp6),
    ) {
        Icon(
            imageVector = Icons.Outlined.WarningAmber,
            contentDescription = null,
            tint = KnotworkTheme.extended.signalError,
            modifier = Modifier.size(KnotworkTheme.spacing.sp16),
        )
        EmptyState(
            title = stringResource(R.string.knotwork_library_error_title),
            subtitle = state.errorMessage.orEmpty(),
            illustration = { /* Icon already drawn above; suppress default stripe. */ },
            ctaLabel = stringResource(R.string.knotwork_library_error_retry),
            onCtaClick = callbacks.onErrorRetry,
        )
        KnotworkTextButton(
            text = stringResource(R.string.knotwork_library_error_report),
            onClick = callbacks.onErrorReport,
        )
    }
}

@Composable
private fun LibraryNoMatchState(callbacks: PipelineLibraryCallbacks, query: String) {
    EmptyState(
        title = stringResource(R.string.knotwork_library_no_match_title),
        subtitle = stringResource(R.string.knotwork_library_no_match_subtitle) +
            (if (query.isNotEmpty()) " (\"$query\")" else ""),
        illustration = { /* No striped placeholder for the no-match tile. */ },
        ctaLabel = stringResource(R.string.knotwork_library_no_match_clear),
        onCtaClick = callbacks.onClearSearch,
    )
}

@Composable
private fun LibraryList(state: PipelineLibraryViewState, callbacks: PipelineLibraryCallbacks) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(vertical = KnotworkTheme.spacing.sp2),
        verticalArrangement = Arrangement.spacedBy(KnotworkTheme.spacing.sp1),
    ) {
        items(items = state.pipelines, key = { it.id }) { row ->
            PipelineLibraryRowAdapter(row = row, callbacks = callbacks)
        }
    }
}

@Composable
private fun PipelineLibraryRowAdapter(row: PipelineLibraryRow, callbacks: PipelineLibraryCallbacks) {
    PipelineListRow(
        title = row.title,
        subtitle = row.subtitle,
        status = row.status,
        leadingTint = row.leadingTint,
        leadingIcon = row.leadingIcon,
        onClick = { callbacks.onPipelineClick(row.id) },
        onOverflow = { callbacks.onPipelineOverflow(row.id) },
        onAction = { action ->
            when (action) {
                PipelineSwipeAction.Duplicate -> callbacks.onDuplicate(row.id)
                PipelineSwipeAction.Archive -> callbacks.onArchive(row.id)
                PipelineSwipeAction.Delete -> callbacks.onDelete(row.id)
            }
        },
        revealed = if (row.revealed) true else null,
    )
}

/** Resolves the localised label for a filter chip. */
@Composable
private fun filterLabel(filter: PipelineLibraryFilter): String = stringResource(
    when (filter) {
        PipelineLibraryFilter.All -> R.string.knotwork_library_filter_all
        PipelineLibraryFilter.Recent -> R.string.knotwork_library_filter_recent
        PipelineLibraryFilter.Shared -> R.string.knotwork_library_filter_shared
        PipelineLibraryFilter.Mine -> R.string.knotwork_library_filter_mine
    },
)
