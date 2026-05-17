@file:Suppress("MatchingDeclarationName") // Hosts ToolsContent, ToolDetailContent, AddMcpServerContent.

package app.knotwork.design.screens.tools

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
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Cable
import androidx.compose.material.icons.outlined.Cloud
import androidx.compose.material.icons.outlined.Computer
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.knotwork.design.R
import app.knotwork.design.components.buttons.KnotworkPrimaryButton
import app.knotwork.design.components.buttons.KnotworkSecondaryButton
import app.knotwork.design.components.buttons.KnotworkTextButton
import app.knotwork.design.components.chips.StatusPill
import app.knotwork.design.components.misc.EmptyState
import app.knotwork.design.components.misc.StripedPlaceholder
import app.knotwork.design.theme.KnotworkTheme
import app.knotwork.design.tokens.KnotworkTextStyles

private val SectionHeaderHeight = 48.dp
private val ToolRowHeight = 64.dp
private val SchemaPreviewHeight = 240.dp

/** Number of section skeletons drawn in the Loading state. */
private const val LOADING_SECTION_COUNT = 3

/** Number of tool-row skeletons drawn beneath each section header in the Loading state. */
private const val LOADING_ROWS_PER_SECTION = 2

/**
 * Stateless Knotwork tools surface. Mirrors
 * `compose/screens/README.md §C4 · Tools / MCP`.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ToolsContent(
    state: ToolsViewState,
    modifier: Modifier = Modifier,
    callbacks: ToolsCallbacks = noopToolsCallbacks(),
) {
    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.surface,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = stringResource(R.string.knotwork_tools_title),
                        style = KnotworkTextStyles.TitleLg,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = MaterialTheme.colorScheme.onSurface,
                ),
            )
        },
    ) { padding ->
        when (state.visualState) {
            ToolsVisualState.Empty -> ToolsEmpty(callbacks = callbacks, padding = padding)
            ToolsVisualState.Loading -> ToolsLoading(padding = padding)
            ToolsVisualState.Error -> ToolsError(state = state, callbacks = callbacks, padding = padding)
            ToolsVisualState.Default -> ToolsList(state = state, callbacks = callbacks, padding = padding)
        }
    }
}

@Composable
private fun ToolsEmpty(callbacks: ToolsCallbacks, padding: PaddingValues) {
    Box(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
        EmptyState(
            title = stringResource(R.string.knotwork_tools_empty_title),
            subtitle = stringResource(R.string.knotwork_tools_empty_subtitle),
            ctaLabel = stringResource(R.string.knotwork_tools_empty_cta),
            onCtaClick = callbacks.onAddMcpServer,
        )
    }
}

@Composable
private fun ToolsLoading(padding: PaddingValues) {
    Column(
        verticalArrangement = Arrangement.spacedBy(KnotworkTheme.spacing.sp2),
        modifier = Modifier.fillMaxSize().padding(padding).padding(KnotworkTheme.spacing.sp4),
    ) {
        repeat(LOADING_SECTION_COUNT) {
            StripedPlaceholder(modifier = Modifier.fillMaxWidth().height(SectionHeaderHeight))
            repeat(LOADING_ROWS_PER_SECTION) {
                StripedPlaceholder(modifier = Modifier.fillMaxWidth().height(ToolRowHeight))
            }
        }
    }
}

@Composable
private fun ToolsError(state: ToolsViewState, callbacks: ToolsCallbacks, padding: PaddingValues) {
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
            title = "Couldn't load tools",
            subtitle = state.errorMessage.orEmpty(),
            illustration = { /* icon above */ },
            ctaLabel = "Retry",
            onCtaClick = callbacks.onErrorRetry,
        )
    }
}

@Composable
private fun ToolsList(state: ToolsViewState, callbacks: ToolsCallbacks, padding: PaddingValues) {
    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(padding),
        contentPadding = PaddingValues(vertical = KnotworkTheme.spacing.sp2),
        verticalArrangement = Arrangement.spacedBy(KnotworkTheme.spacing.sp1),
    ) {
        state.sections.forEach { block ->
            item(key = "section-header-${block.serverId}") {
                ToolsSectionHeader(block = block, callbacks = callbacks)
            }
            items(items = block.tools, key = { "${block.serverId}-${it.id}" }) { tool ->
                ToolRow(tool = tool, callbacks = callbacks)
            }
            if (block.errorMessage != null) {
                item(key = "section-error-${block.serverId}") {
                    ToolsSectionErrorTile(message = block.errorMessage, onRemove = {
                        callbacks.onServerRemove(block.serverId)
                    })
                }
            }
        }
    }
}

@Composable
private fun ToolsSectionHeader(block: ToolsSectionBlock, callbacks: ToolsCallbacks) {
    val isLocal = block.serverId == ToolsSectionBlock.LOCAL_SERVER_ID
    val icon = if (isLocal) Icons.Outlined.Computer else Icons.Outlined.Cloud
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(KnotworkTheme.spacing.sp2),
        modifier = Modifier
            .fillMaxWidth()
            .height(SectionHeaderHeight)
            .padding(horizontal = KnotworkTheme.spacing.sp4),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = KnotworkTheme.extended.onSurfaceMuted,
            modifier = Modifier.size(KnotworkTheme.spacing.sp5),
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = block.displayName,
                style = KnotworkTextStyles.TitleMd,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (block.subtitle != null) {
                Text(
                    text = block.subtitle,
                    style = KnotworkTextStyles.BodySm,
                    color = KnotworkTheme.extended.onSurfaceMuted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        StatusPill(status = block.statusPill)
        if (!isLocal && block.connectionState == McpConnectionState.Disconnected) {
            KnotworkTextButton(
                text = stringResource(R.string.knotwork_tools_reconnect),
                onClick = { callbacks.onServerReconnect(block.serverId) },
            )
        }
        if (!isLocal) {
            IconButton(onClick = { callbacks.onServerRemove(block.serverId) }) {
                Icon(
                    imageVector = Icons.Outlined.DeleteOutline,
                    contentDescription = stringResource(R.string.knotwork_tools_remove_server_cd),
                    tint = KnotworkTheme.extended.onSurfaceMuted,
                )
            }
        }
    }
}

@Composable
private fun ToolRow(tool: ToolRowState, callbacks: ToolsCallbacks) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(KnotworkTheme.spacing.sp3),
        modifier = Modifier
            .fillMaxWidth()
            .height(ToolRowHeight)
            .clickable { callbacks.onToolClick(tool.id) }
            .padding(horizontal = KnotworkTheme.spacing.sp4),
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = tool.name,
                style = KnotworkTextStyles.TitleMd,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (tool.description.isNotBlank()) {
                Text(
                    text = tool.description,
                    style = KnotworkTextStyles.BodySm,
                    color = KnotworkTheme.extended.onSurfaceMuted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Switch(
            checked = tool.enabled,
            onCheckedChange = { callbacks.onToolToggle(tool.id, it) },
        )
    }
}

@Composable
private fun ToolsSectionErrorTile(message: String, onRemove: () -> Unit) {
    Surface(
        shape = KnotworkTheme.shapes.md,
        color = KnotworkTheme.extended.surface1,
        modifier = Modifier.fillMaxWidth().padding(KnotworkTheme.spacing.sp4),
    ) {
        Column(modifier = Modifier.padding(KnotworkTheme.spacing.sp3)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Outlined.WarningAmber,
                    contentDescription = null,
                    tint = KnotworkTheme.extended.signalError,
                )
                Spacer(modifier = Modifier.size(KnotworkTheme.spacing.sp2))
                Text(
                    text = message,
                    style = KnotworkTextStyles.BodyBase,
                    color = KnotworkTheme.extended.signalError,
                )
            }
            Spacer(modifier = Modifier.size(KnotworkTheme.spacing.sp2))
            KnotworkSecondaryButton(text = "Remove server", onClick = onRemove)
        }
    }
}

// ------------------------- ToolDetailContent -------------------------

/**
 * Stateless tool-detail surface — schema preview + enable/disable toggle.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ToolDetailContent(
    state: ToolDetailViewState,
    modifier: Modifier = Modifier,
    callbacks: ToolDetailCallbacks = noopToolDetailCallbacks(),
) {
    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.surface,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = state.toolName,
                        style = KnotworkTextStyles.TitleLg,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = callbacks.onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                            contentDescription = stringResource(R.string.knotwork_tools_detail_back),
                            tint = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = MaterialTheme.colorScheme.onSurface,
                ),
            )
        },
    ) { padding ->
        Column(
            verticalArrangement = Arrangement.spacedBy(KnotworkTheme.spacing.sp3),
            modifier = Modifier.fillMaxSize().padding(padding).padding(KnotworkTheme.spacing.sp4),
        ) {
            Text(
                text = state.serverDisplayName,
                style = KnotworkTextStyles.BodySm,
                color = KnotworkTheme.extended.onSurfaceMuted,
            )
            Text(
                text = state.description,
                style = KnotworkTextStyles.BodyBase,
                color = MaterialTheme.colorScheme.onSurface,
            )
            if (state.lastUsed != null) {
                Text(
                    text = state.lastUsed,
                    style = KnotworkTextStyles.Caption,
                    color = KnotworkTheme.extended.onSurfaceMuted,
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = if (state.enabled) "Enabled" else "Disabled",
                    style = KnotworkTextStyles.TitleMd,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f),
                )
                Switch(checked = state.enabled, onCheckedChange = callbacks.onToggle)
            }
            Text(
                text = stringResource(R.string.knotwork_tools_detail_schema),
                style = KnotworkTextStyles.TitleMd,
                color = MaterialTheme.colorScheme.onSurface,
            )
            when (state.visualState) {
                ToolDetailVisualState.Loading -> StripedPlaceholder(
                    modifier = Modifier.fillMaxWidth().height(SchemaPreviewHeight),
                )
                ToolDetailVisualState.SchemaError -> Surface(
                    shape = KnotworkTheme.shapes.md,
                    color = KnotworkTheme.extended.surface1,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        text = stringResource(R.string.knotwork_tools_detail_schema_error),
                        style = KnotworkTextStyles.BodyBase,
                        color = KnotworkTheme.extended.signalError,
                        modifier = Modifier.padding(KnotworkTheme.spacing.sp3),
                    )
                }
                ToolDetailVisualState.Default -> Surface(
                    shape = KnotworkTheme.shapes.md,
                    color = KnotworkTheme.extended.consoleBg,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        text = state.schemaJson.orEmpty(),
                        style = KnotworkTextStyles.MonoBase,
                        color = KnotworkTheme.extended.consoleFg,
                        modifier = Modifier.padding(KnotworkTheme.spacing.sp3),
                    )
                }
            }
        }
    }
}

// ------------------------- AddMcpServerContent -------------------------

/**
 * Stateless add-MCP-server surface. URL validation lives in the host.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddMcpServerContent(
    state: AddMcpServerViewState,
    modifier: Modifier = Modifier,
    callbacks: AddMcpServerCallbacks = noopAddMcpServerCallbacks(),
) {
    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.surface,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = stringResource(R.string.knotwork_tools_add_server_title),
                        style = KnotworkTextStyles.TitleLg,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = callbacks.onCancel) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                            contentDescription = stringResource(R.string.knotwork_tools_add_server_cancel),
                            tint = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = MaterialTheme.colorScheme.onSurface,
                ),
            )
        },
    ) { padding ->
        Column(
            verticalArrangement = Arrangement.spacedBy(KnotworkTheme.spacing.sp3),
            modifier = Modifier.fillMaxSize().padding(padding).padding(KnotworkTheme.spacing.sp4),
        ) {
            Text(
                text = stringResource(R.string.knotwork_tools_add_server_url_label),
                style = KnotworkTextStyles.TitleMd,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(KnotworkTheme.shapes.md)
                    .background(color = KnotworkTheme.extended.surface2)
                    .padding(KnotworkTheme.spacing.sp3),
            ) {
                BasicTextField(
                    value = state.url,
                    onValueChange = callbacks.onUrlChange,
                    singleLine = true,
                    textStyle = KnotworkTextStyles.MonoBase.copy(color = MaterialTheme.colorScheme.onSurface),
                    cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                    modifier = Modifier.fillMaxWidth(),
                )
                if (state.url.isEmpty()) {
                    Text(
                        text = stringResource(R.string.knotwork_tools_add_server_url_placeholder),
                        style = KnotworkTextStyles.MonoBase,
                        color = KnotworkTheme.extended.onSurfaceMuted,
                    )
                }
            }
            if (state.urlError != null) {
                Text(
                    text = state.urlError,
                    style = KnotworkTextStyles.BodySm,
                    color = KnotworkTheme.extended.signalError,
                )
            }
            Spacer(modifier = Modifier.weight(1f))
            Row(modifier = Modifier.fillMaxWidth()) {
                KnotworkSecondaryButton(
                    text = stringResource(R.string.knotwork_tools_add_server_cancel),
                    onClick = callbacks.onCancel,
                )
                Spacer(modifier = Modifier.weight(1f))
                KnotworkPrimaryButton(
                    text = stringResource(R.string.knotwork_tools_add_server_submit),
                    onClick = callbacks.onSubmit,
                    enabled = state.canSubmit,
                )
            }
        }
    }
}

// Suppress lint about unused import — Cable icon is referenced by the
// onboarding empty-state spec; keep for forward compat.
@Suppress("UnusedSymbol")
private val Reserved = Icons.Outlined.Cable

@Suppress("UnusedSymbol")
private val ReservedRefresh = Icons.Outlined.Refresh
