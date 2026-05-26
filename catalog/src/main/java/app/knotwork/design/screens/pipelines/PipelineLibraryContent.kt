@file:Suppress("MatchingDeclarationName") // File hosts PipelineLibraryContent and its helper composables.

package app.knotwork.design.screens.pipelines

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.outlined.AccountTree
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.DriveFileRenameOutline
import androidx.compose.material.icons.outlined.FileDownload
import androidx.compose.material.icons.outlined.FileUpload
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.knotwork.design.R
import app.knotwork.design.components.buttons.KnotworkSecondaryButton
import app.knotwork.design.components.buttons.KnotworkTextButton
import app.knotwork.design.components.chips.KnotworkChip
import app.knotwork.design.components.misc.EmptyState
import app.knotwork.design.components.misc.StripedPlaceholder
import app.knotwork.design.theme.KnotworkTheme
import app.knotwork.design.tokens.KnotworkTextStyles

/** Number of skeleton rows rendered while the repository is loading. */
private const val LOADING_SKELETON_ROWS = 3

/** Height of the skeleton row used while the repository is loading. */
private val SkeletonRowHeight = 80.dp

/** Diameter of the row's leading icon tile. */
private val RowLeadingSize = 48.dp

/** Inner size of the icon inside the leading tile. */
private val RowLeadingIconSize = 24.dp

/** Height of the "DEFAULT" pill rendered next to the row title. */
private val DefaultBadgeHeight = 22.dp

/**
 * Stateless Knotwork pipeline-library surface. Mirrors the spec mockups
 * shipped in `compose/screens/<C3-mockup>.png` (Phase 21 / Task 10):
 *
 *  - `TopAppBar` with title `Pipelines` + `N saved · M default` subtitle,
 *    leading drawer (hamburger) icon, trailing search + overflow icons.
 *  - `LIBRARY` section header (all-caps, muted) introducing the list.
 *  - One row per pipeline: 48 dp leading mark + monospace title + `DEFAULT`
 *    pill when applicable + "N nodes · {flavour}" subtitle + secondary
 *    status line ("Active default" / "N chats" / "unbound"). The
 *    currently-active pipeline (id = `loaded` in the editor) renders with
 *    a warm full-row tint.
 *  - Footer block: `FROM BROWSER EDITOR` header + `Import JSON` link +
 *    explanatory body.
 *  - Per-row overflow opens an anchored [DropdownMenu] with
 *    `Load in editor / Set as default / Rename / Duplicate / Export JSON
 *    / Delete`. Visibility is driven by [PipelineLibraryViewState.openOverflowRowId]
 *    so the host owns the open/close transitions.
 *
 * The pill FAB (`+ New pipeline`) is **not** rendered here. The mockup
 * places it half-overlapping the bottom-nav, which only the host
 * `AppShellScaffold` can position cleanly — `:app` overlays the catalog
 * `PipelineLibraryFab` composable above the bottom nav.
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
            app.knotwork.design.components.topbar.KnotworkTopAppBarShell {
                if (state.visualState == PipelineLibraryVisualState.MultiSelect) {
                    MultiSelectToolbar(state = state, callbacks = callbacks)
                } else {
                    LibraryTopBar(state = state, callbacks = callbacks)
                }
            }
        },
    ) { padding ->
        LibraryBody(state = state, callbacks = callbacks, padding = padding)
    }
}

/**
 * Standalone pill FAB rendered outside [PipelineLibraryContent] so the host
 * can stack it over the bottom navigation bar per the spec mockup. Returns
 * `Unit` and is positioned by the caller; this composable owns colours and
 * label only.
 */
@Composable
fun PipelineLibraryFab(onClick: () -> Unit, modifier: Modifier = Modifier) {
    ExtendedFloatingActionButton(
        onClick = onClick,
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
        modifier = modifier,
    )
}

/**
 * Hides the standalone FAB in the visual states where it has no meaningful
 * action. The catalog exposes this as a public property so the host can
 * conditionally render [PipelineLibraryFab] outside the content.
 */
val PipelineLibraryViewState.isFabHidden: Boolean
    get() = visualState == PipelineLibraryVisualState.Error ||
        visualState == PipelineLibraryVisualState.MultiSelect

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LibraryTopBar(state: PipelineLibraryViewState, callbacks: PipelineLibraryCallbacks) {
    TopAppBar(
        title = {
            Column {
                Text(
                    text = stringResource(R.string.knotwork_library_title),
                    style = KnotworkTextStyles.TitleLg,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = androidx.compose.ui.res.pluralStringResource(
                        R.plurals.knotwork_library_topbar_subtitle,
                        state.totalCount,
                        state.totalCount,
                        state.defaultCount,
                    ),
                    style = KnotworkTextStyles.MonoSm,
                    color = KnotworkTheme.extended.onSurfaceMuted,
                )
            }
        },
        actions = {
            IconButton(onClick = callbacks.onOpenSearch) {
                Icon(
                    imageVector = Icons.Outlined.Search,
                    contentDescription = stringResource(R.string.knotwork_library_search_icon_cd),
                    tint = MaterialTheme.colorScheme.onSurface,
                )
            }
            IconButton(onClick = callbacks.onTopOverflow) {
                Icon(
                    imageVector = Icons.Outlined.MoreVert,
                    contentDescription = stringResource(R.string.knotwork_library_top_overflow_cd),
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
        // The inline search field + filter-chip row only render when the
        // host explicitly puts the surface in a filtering state (search
        // text non-empty or a chip other than All selected). The default
        // mockup has neither — surface starts with the LIBRARY header.
        val showSearchChrome = state.searchQuery.isNotEmpty() ||
            state.activeFilter != PipelineLibraryFilter.All ||
            state.visualState == PipelineLibraryVisualState.Filtering
        if (showSearchChrome && state.visualState != PipelineLibraryVisualState.Error) {
            LibrarySearchField(query = state.searchQuery, onQueryChange = callbacks.onSearchQueryChange)
            LibraryFilterRow(active = state.activeFilter, onChange = callbacks.onFilterChange)
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
            // The `Shared` filter is disabled until the sync backend lands.
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
            illustration = { /* Icon already drawn above. */ },
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
        contentPadding = PaddingValues(bottom = KnotworkTheme.spacing.sp16),
    ) {
        item(key = "library-header") { LibrarySectionHeader() }
        items(items = state.pipelines, key = { it.id }) { row ->
            PipelineLibraryListRow(
                row = row,
                overflowOpen = state.openOverflowRowId == row.id,
                callbacks = callbacks,
            )
            HorizontalDivider(
                modifier = Modifier.padding(start = KnotworkTheme.spacing.sp4),
                color = KnotworkTheme.extended.divider,
            )
        }
        item(key = "browser-footer") { BrowserEditorFooter(callbacks = callbacks) }
    }
}

@Composable
private fun LibrarySectionHeader() {
    Text(
        text = stringResource(R.string.knotwork_library_section_library),
        style = KnotworkTextStyles.MonoSm,
        color = KnotworkTheme.extended.onSurfaceMuted,
        modifier = Modifier
            .fillMaxWidth()
            .padding(
                horizontal = KnotworkTheme.spacing.sp4,
                vertical = KnotworkTheme.spacing.sp3,
            ),
    )
}

@Composable
private fun PipelineLibraryListRow(
    row: PipelineLibraryRow,
    overflowOpen: Boolean,
    callbacks: PipelineLibraryCallbacks,
) {
    val rowBg = if (row.isActive) KnotworkTheme.extended.surface3 else MaterialTheme.colorScheme.surface
    Row(
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(KnotworkTheme.spacing.sp3),
        modifier = Modifier
            .fillMaxWidth()
            .background(color = rowBg)
            .clickable { callbacks.onPipelineClick(row.id) }
            .padding(
                horizontal = KnotworkTheme.spacing.sp4,
                vertical = KnotworkTheme.spacing.sp3,
            ),
    ) {
        // Leading: rounded tile with the pipeline glyph. Tint comes from
        // the host so each pipeline can colour-code its primary node hue.
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(RowLeadingSize)
                .clip(KnotworkTheme.shapes.md)
                .background(color = KnotworkTheme.extended.surface2),
        ) {
            Icon(
                imageVector = Icons.Outlined.AccountTree,
                contentDescription = null,
                tint = row.leadingTint,
                modifier = Modifier.size(RowLeadingIconSize),
            )
        }
        // Centre column: title + DEFAULT pill + node-shape subtitle + status line.
        Column(modifier = Modifier.weight(1f)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(KnotworkTheme.spacing.sp2),
            ) {
                Text(
                    text = row.title,
                    style = KnotworkTextStyles.MonoBase.copy(
                        fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold,
                    ),
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                if (row.isDefault) DefaultBadge()
            }
            Spacer(modifier = Modifier.height(KnotworkTheme.spacing.sp1))
            Text(
                text = row.subtitle,
                style = KnotworkTextStyles.MonoSm,
                color = KnotworkTheme.extended.onSurfaceMuted,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            if (row.secondaryLine != null) {
                Spacer(modifier = Modifier.height(KnotworkTheme.spacing.sp1))
                Text(
                    text = row.secondaryLine,
                    style = KnotworkTextStyles.MonoSm,
                    color = when (row.secondaryLineKind) {
                        PipelineSecondaryLineKind.Default -> KnotworkTheme.extended.onSurfaceMuted
                        PipelineSecondaryLineKind.Unbound -> KnotworkTheme.extended.signalError
                    },
                )
            }
        }
        // Trailing: overflow icon + anchored dropdown menu.
        Box {
            IconButton(onClick = { callbacks.onPipelineOverflow(row.id) }) {
                Icon(
                    imageVector = Icons.Outlined.MoreVert,
                    contentDescription = stringResource(R.string.knotwork_library_row_overflow_cd, row.title),
                    tint = KnotworkTheme.extended.onSurfaceMuted,
                )
            }
            RowOverflowMenu(row = row, expanded = overflowOpen, callbacks = callbacks)
        }
    }
}

@Composable
private fun DefaultBadge() {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .height(DefaultBadgeHeight)
            .clip(KnotworkTheme.shapes.sm)
            .background(
                color = KnotworkTheme.extended.riskDestructive.copy(alpha = 0f).let {
                    // Use a brand-warm brown for the pill — `Accent700` reads as
                    // "default" without competing with the orange primary.
                    app.knotwork.design.tokens.KnotworkPalette.Accent700
                },
            )
            .padding(horizontal = KnotworkTheme.spacing.sp2),
    ) {
        Text(
            text = stringResource(R.string.knotwork_library_badge_default),
            style = KnotworkTextStyles.LabelSm.copy(
                fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold,
            ),
            color = MaterialTheme.colorScheme.onPrimary,
        )
    }
}

@Composable
private fun RowOverflowMenu(row: PipelineLibraryRow, expanded: Boolean, callbacks: PipelineLibraryCallbacks) {
    DropdownMenu(
        expanded = expanded,
        onDismissRequest = callbacks.onOverflowDismiss,
    ) {
        DropdownMenuItem(
            leadingIcon = {
                Icon(
                    imageVector = Icons.AutoMirrored.Outlined.OpenInNew,
                    contentDescription = null,
                )
            },
            text = { Text(stringResource(R.string.knotwork_library_menu_load)) },
            onClick = {
                callbacks.onOverflowDismiss()
                callbacks.onLoadInEditor(row.id)
            },
        )
        DropdownMenuItem(
            leadingIcon = {
                Icon(
                    imageVector = Icons.Outlined.Check,
                    contentDescription = null,
                    tint = if (row.isDefault) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        KnotworkTheme.extended.onSurfaceMuted
                    },
                )
            },
            text = {
                Text(
                    stringResource(
                        if (row.isDefault) {
                            R.string.knotwork_library_menu_set_default_active
                        } else {
                            R.string.knotwork_library_menu_set_default
                        },
                    ),
                )
            },
            enabled = !row.isDefault,
            onClick = {
                callbacks.onOverflowDismiss()
                callbacks.onSetAsDefault(row.id)
            },
        )
        DropdownMenuItem(
            leadingIcon = {
                Icon(
                    imageVector = Icons.Outlined.DriveFileRenameOutline,
                    contentDescription = null,
                )
            },
            text = { Text(stringResource(R.string.knotwork_library_menu_rename)) },
            onClick = {
                callbacks.onOverflowDismiss()
                callbacks.onRename(row.id)
            },
        )
        DropdownMenuItem(
            leadingIcon = {
                Icon(
                    imageVector = Icons.Outlined.ContentCopy,
                    contentDescription = null,
                )
            },
            text = { Text(stringResource(R.string.knotwork_library_menu_duplicate)) },
            onClick = {
                callbacks.onOverflowDismiss()
                callbacks.onDuplicate(row.id)
            },
        )
        DropdownMenuItem(
            leadingIcon = {
                Icon(
                    imageVector = Icons.Outlined.FileDownload,
                    contentDescription = null,
                )
            },
            text = { Text(stringResource(R.string.knotwork_library_menu_export)) },
            onClick = {
                callbacks.onOverflowDismiss()
                callbacks.onExportJson(row.id)
            },
        )
        HorizontalDivider()
        DropdownMenuItem(
            leadingIcon = {
                Icon(
                    imageVector = Icons.Outlined.DeleteOutline,
                    contentDescription = null,
                    tint = KnotworkTheme.extended.signalError,
                )
            },
            text = {
                Text(
                    text = stringResource(R.string.knotwork_library_menu_delete),
                    color = KnotworkTheme.extended.signalError,
                )
            },
            onClick = {
                callbacks.onOverflowDismiss()
                callbacks.onDelete(row.id)
            },
        )
    }
}

@Composable
private fun BrowserEditorFooter(callbacks: PipelineLibraryCallbacks) {
    Column(
        verticalArrangement = Arrangement.spacedBy(KnotworkTheme.spacing.sp2),
        modifier = Modifier
            .fillMaxWidth()
            .padding(KnotworkTheme.spacing.sp4),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(
                text = stringResource(R.string.knotwork_library_footer_header),
                style = KnotworkTextStyles.MonoSm,
                color = KnotworkTheme.extended.onSurfaceMuted,
                modifier = Modifier.weight(1f),
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(KnotworkTheme.spacing.sp1),
                modifier = Modifier.clickable { callbacks.onImportJson() },
            ) {
                Icon(
                    imageVector = Icons.Outlined.FileUpload,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                )
                Text(
                    text = stringResource(R.string.knotwork_library_footer_import),
                    style = KnotworkTextStyles.LabelLg.copy(
                        fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold,
                    ),
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }
        Text(
            text = stringResource(R.string.knotwork_library_footer_body),
            style = KnotworkTextStyles.BodyBase,
            color = KnotworkTheme.extended.onSurfaceMuted,
        )
    }
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
