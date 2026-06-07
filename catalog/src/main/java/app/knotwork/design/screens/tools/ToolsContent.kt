@file:Suppress("MatchingDeclarationName") // Hosts ToolsContent, ToolDetailContent, McpServerConfigContent.

package app.knotwork.design.screens.tools

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.knotwork.design.R
import app.knotwork.design.components.buttons.KnotworkPrimaryButton
import app.knotwork.design.components.buttons.KnotworkTextButton
import app.knotwork.design.components.misc.EmptyState
import app.knotwork.design.components.misc.StripedPlaceholder
import app.knotwork.design.icons.AppIcons
import app.knotwork.design.theme.KnotworkTheme
import app.knotwork.design.tokens.KnotworkTextStyles

private val LeadingTileSize = 40.dp
private val LeadingIconSize = 18.dp
private val RiskPillHeight = 22.dp
private val StatusDotSize = 10.dp
private val SchemaPreviewHeight = 240.dp

// Material3's default Switch (52×32 dp) dwarfs the Knotwork row title scale.
// `SettingsContent` shrinks it to ~78 % via `Modifier.scale` so the visual
// weight matches our 14 sp row titles; the parent Row's `clickable` still
// guarantees a 48 dp touch target. Keep this in sync with the Settings
// constant of the same name.
private const val SWITCH_SCALE = 0.78f

/** Number of section / row skeletons rendered while the surface is loading. */
private const val LOADING_SECTION_COUNT = 2
private const val LOADING_ROWS_PER_SECTION = 3

/**
 * Opacity applied to the server row and its nested tools when the server's
 * connection state is [McpConnectionState.Disconnected]. Pairs the colour-only
 * signal (warning dot) with a second visual cue for accessibility.
 */
private const val DISCONNECTED_ROW_ALPHA = 0.6f

/**
 * Composite key for the server-row subtitle's `AnimatedContent`. Re-keys the
 * crossfade on both connection-state transitions (Connecting → Connected →
 * Error) and label-only changes (Connected `42 ms` → `318 ms`) so the
 * mono-text re-flows through the same animation channel.
 */
private data class ServerSubtitle(val state: McpConnectionState, val label: String, val count: Int)

/**
 * Stateless Knotwork tools surface.
 *
 *  - TopAppBar with title + monospace "N built-in · M MCP" subtitle;
 *    trailing overflow icon.
 *  - Section 1 (`BUILT-IN (APPFUNCTIONS)`): per-tool row with leading
 *    edit-glyph tile, monospace title, an outline risk pill
 *    (Read only / Sensitive / Destructive) next to the title, a wrapping
 *    body subtitle, and a trailing Switch.
 *  - Section 2 (`MCP SERVERS` + `+ Add MCP` link): one row per server
 *    with a leading status dot, monospace URL, monospace
 *    "N tools · X ms" / "N tools · disabled" subtitle, trailing trash
 *    icon.
 *  - Inline add-server form rendered at the bottom of the list when
 *    [ToolsViewState.addServerForm] is non-null. The catalog ships the
 *    visuals; the host owns persistence + URL validation.
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
            app.knotwork.design.components.topbar.KnotworkTopAppBarShell {
                ToolsTopBar(state = state)
            }
        },
        // The outer `AppShellScaffold` already absorbs both the system
        // navigation bar and the in-app bottom-nav strip via its own
        // inner padding. Letting this Scaffold default to `safeDrawing`
        // would double-count the bottom inset and leave a visible gap
        // between the list and the bottom-nav strip.
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
    ) { padding ->
        when (state.visualState) {
            ToolsVisualState.Empty -> ToolsEmpty(callbacks = callbacks, padding = padding)
            ToolsVisualState.Loading -> ToolsLoading(padding = padding)
            ToolsVisualState.Error -> ToolsError(state = state, callbacks = callbacks, padding = padding)
            ToolsVisualState.Default -> ToolsList(state = state, callbacks = callbacks, padding = padding)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ToolsTopBar(state: ToolsViewState) {
    TopAppBar(
        title = {
            Column {
                Text(
                    text = stringResource(R.string.knotwork_tools_title),
                    style = KnotworkTextStyles.TitleMd,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = androidx.compose.ui.res.pluralStringResource(
                        R.plurals.knotwork_tools_topbar_subtitle,
                        state.builtInTools.size,
                        state.builtInTools.size,
                        state.mcpServers.size,
                    ),
                    style = KnotworkTextStyles.MonoSm,
                    color = KnotworkTheme.extended.onSurfaceMuted,
                )
            }
        },
        // No top-bar overflow: connect-server lives on the FAB / empty-state
        // CTA and per-server actions live inline on each server card, so the
        // menu had nothing to host.
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.surface,
            titleContentColor = MaterialTheme.colorScheme.onSurface,
        ),
    )
}

@Composable
private fun ToolsEmpty(callbacks: ToolsCallbacks, padding: PaddingValues) {
    Box(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
        EmptyState(
            title = stringResource(R.string.knotwork_tools_empty_title),
            subtitle = stringResource(R.string.knotwork_tools_empty_subtitle),
            ctaLabel = stringResource(R.string.knotwork_tools_empty_cta),
            onCtaClick = callbacks.onAddServerOpen,
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
            StripedPlaceholder(modifier = Modifier.fillMaxWidth().height(LeadingTileSize))
            repeat(LOADING_ROWS_PER_SECTION) {
                StripedPlaceholder(modifier = Modifier.fillMaxWidth().height(LeadingTileSize + LeadingTileSize))
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
            imageVector = AppIcons.Warn,
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
        contentPadding = PaddingValues(bottom = KnotworkTheme.spacing.sp2),
    ) {
        item(key = "built-in-header") {
            SimpleSectionHeader(
                title = stringResource(R.string.knotwork_tools_section_built_in),
                trailing = null,
            )
        }
        items(items = state.builtInTools, key = { "builtin-${it.id}" }) { tool ->
            BuiltInToolRowView(tool = tool, callbacks = callbacks)
            HorizontalDivider(color = KnotworkTheme.extended.divider)
        }
        item(key = "mcp-header") {
            SimpleSectionHeader(
                title = stringResource(R.string.knotwork_tools_section_mcp),
                trailing = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(KnotworkTheme.spacing.sp1),
                        modifier = Modifier.clickable(onClick = callbacks.onAddServerOpen),
                    ) {
                        Icon(
                            imageVector = AppIcons.Add,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                        )
                        Text(
                            text = stringResource(R.string.knotwork_tools_add_mcp_link),
                            style = KnotworkTextStyles.LabelLg.copy(fontWeight = FontWeight.SemiBold),
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                },
            )
        }
        state.mcpServers.forEach { server ->
            // In the Disconnected state, the server row plus
            // every nested tool row renders at 60 % opacity so the disabled-by-
            // server-failure affordances read at a glance. The opacity stops at
            // the row level (it does NOT cascade into the catalog `EmptyState`
            // or `StripedPlaceholder` containers, which live outside this loop).
            val rowAlpha = if (server.state == McpConnectionState.Disconnected) DISCONNECTED_ROW_ALPHA else 1f
            item(key = "mcp-${server.id}") {
                McpServerRowView(server = server, callbacks = callbacks, rowAlpha = rowAlpha)
                HorizontalDivider(color = KnotworkTheme.extended.divider)
            }
            if (server.expanded) {
                items(items = server.tools, key = { "mcp-tool-${it.id}" }) { entry ->
                    McpToolEntryRowView(entry = entry, callbacks = callbacks, rowAlpha = rowAlpha)
                    HorizontalDivider(color = KnotworkTheme.extended.divider)
                }
            }
        }
    }
}

@Composable
private fun SimpleSectionHeader(title: String, trailing: (@Composable () -> Unit)?) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(
                horizontal = KnotworkTheme.spacing.sp4,
                vertical = KnotworkTheme.spacing.sp3,
            ),
    ) {
        Text(
            text = title,
            style = KnotworkTextStyles.MonoSm,
            color = KnotworkTheme.extended.onSurfaceMuted,
            modifier = Modifier.weight(1f),
        )
        if (trailing != null) trailing()
    }
    HorizontalDivider(color = KnotworkTheme.extended.divider)
}

@Composable
private fun BuiltInToolRowView(tool: BuiltInToolRow, callbacks: ToolsCallbacks) {
    Row(
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(KnotworkTheme.spacing.sp3),
        modifier = Modifier
            .fillMaxWidth()
            .clickable { callbacks.onToolClick(tool.id) }
            .padding(
                horizontal = KnotworkTheme.spacing.sp4,
                vertical = KnotworkTheme.spacing.sp3,
            ),
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(LeadingTileSize)
                .clip(KnotworkTheme.shapes.sm)
                .background(color = KnotworkTheme.extended.surface2),
        ) {
            Icon(
                imageVector = AppIcons.Edit,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.size(LeadingIconSize),
            )
        }
        Column(
            verticalArrangement = Arrangement.spacedBy(KnotworkTheme.spacing.sp1),
            modifier = Modifier.weight(1f),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(KnotworkTheme.spacing.sp2),
            ) {
                Text(
                    text = tool.name,
                    style = KnotworkTextStyles.MonoBase.copy(fontWeight = FontWeight.SemiBold),
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                RiskOutlinePill(risk = tool.risk)
            }
            if (tool.description.isNotBlank()) {
                Text(
                    text = tool.description,
                    style = KnotworkTextStyles.BodySm,
                    color = KnotworkTheme.extended.onSurfaceMuted,
                )
            }
        }
        Switch(
            checked = tool.enabled,
            onCheckedChange = { callbacks.onToolToggle(tool.id, it) },
            colors = SwitchDefaults.colors(
                checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
                checkedTrackColor = MaterialTheme.colorScheme.primary,
                checkedBorderColor = MaterialTheme.colorScheme.primary,
            ),
            modifier = Modifier.scale(SWITCH_SCALE),
        )
    }
}

@Composable
private fun RiskOutlinePill(risk: BuiltInToolRisk) {
    val accent = riskAccent(risk)
    val label = stringResource(
        when (risk) {
            BuiltInToolRisk.ReadOnly -> R.string.knotwork_tools_pill_readonly
            BuiltInToolRisk.Sensitive -> R.string.knotwork_tools_pill_sensitive
            BuiltInToolRisk.Destructive -> R.string.knotwork_tools_pill_destructive
        },
    )
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(KnotworkTheme.spacing.sp1),
        modifier = Modifier
            .height(RiskPillHeight)
            .clip(KnotworkTheme.shapes.full)
            .border(width = 1.dp, color = accent, shape = KnotworkTheme.shapes.full)
            .padding(horizontal = KnotworkTheme.spacing.sp2)
            .semantics { contentDescription = "Risk level: $label" },
    ) {
        Box(
            modifier = Modifier
                .size(KnotworkTheme.spacing.sp2)
                .background(color = accent, shape = CircleShape),
        )
        Text(
            text = label,
            style = KnotworkTextStyles.LabelSm,
            color = accent,
        )
    }
}

@Composable
private fun riskAccent(risk: BuiltInToolRisk): Color = when (risk) {
    BuiltInToolRisk.ReadOnly -> KnotworkTheme.extended.riskReadonly
    BuiltInToolRisk.Sensitive -> KnotworkTheme.extended.riskSensitive
    BuiltInToolRisk.Destructive -> KnotworkTheme.extended.riskDestructive
}

/**
 * Resolves the leading status-dot colour for one server row.
 *
 * Kept as a small helper so the dot colour can be passed through
 * `animateColorAsState` for the connection-state transition animation
 * without duplicating the `when` table at every call site.
 */
@Composable
private fun serverDotColor(state: McpConnectionState): Color = when (state) {
    McpConnectionState.Connected -> KnotworkTheme.extended.signalSuccess
    McpConnectionState.Disconnected -> KnotworkTheme.extended.signalWarn
    McpConnectionState.Syncing -> MaterialTheme.colorScheme.primary
    McpConnectionState.Error -> KnotworkTheme.extended.signalError
    McpConnectionState.Disabled -> KnotworkTheme.extended.onSurfaceMuted
}

@Composable
private fun McpServerRowView(server: McpServerRow, callbacks: ToolsCallbacks, rowAlpha: Float = 1f) {
    val targetDotColor = serverDotColor(state = server.state)
    val reducedMotion = KnotworkTheme.a11y.reducedMotion()
    // Connection-state transitions animate the dot colour (target colour
    // tween) and the subtitle (`AnimatedContent` with `SizeTransform` so the
    // monospace `<N tools · <label>` line re-flows when the label widens —
    // e.g. `Connecting → Connected` shrinks, `Connected → Error("reason")`
    // grows). Under reduced motion both animations collapse to an instant
    // snap.
    val animationDurationMs = if (reducedMotion) 0 else KnotworkTheme.motion.dur3
    val dotColor by animateColorAsState(
        targetValue = targetDotColor,
        animationSpec = tween(durationMillis = animationDurationMs, easing = KnotworkTheme.motion.easeStd),
        label = "mcpServerDotColor",
    )
    val expandable = server.tools.isNotEmpty()
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(KnotworkTheme.spacing.sp3),
        modifier = Modifier
            .fillMaxWidth()
            .alpha(rowAlpha)
            .then(
                if (expandable) Modifier.clickable { callbacks.onServerExpandToggle(server.id) } else Modifier,
            )
            .padding(
                horizontal = KnotworkTheme.spacing.sp4,
                vertical = KnotworkTheme.spacing.sp3,
            ),
    ) {
        Box(
            modifier = Modifier
                .size(StatusDotSize)
                .background(color = dotColor, shape = CircleShape),
        )
        Column(
            verticalArrangement = Arrangement.spacedBy(KnotworkTheme.spacing.sp1),
            modifier = Modifier.weight(1f),
        ) {
            Text(
                text = server.url,
                style = KnotworkTextStyles.MonoBase.copy(fontWeight = FontWeight.SemiBold),
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            AnimatedContent(
                targetState = ServerSubtitle(
                    state = server.state,
                    label = server.latencyLabel,
                    count = server.toolCount,
                ),
                transitionSpec = {
                    val enter = fadeIn(animationSpec = tween(durationMillis = animationDurationMs))
                    val exit = fadeOut(animationSpec = tween(durationMillis = animationDurationMs))
                    enter.togetherWith(exit).using(
                        SizeTransform(clip = false) { _, _ -> tween(durationMillis = animationDurationMs) },
                    )
                },
                label = "mcpServerSubtitle",
            ) { subtitle ->
                Text(
                    text = "${subtitle.count} tools · ${subtitle.label}",
                    style = KnotworkTextStyles.MonoSm,
                    color = KnotworkTheme.extended.onSurfaceMuted,
                )
            }
        }
        if (expandable) {
            IconButton(onClick = { callbacks.onServerExpandToggle(server.id) }) {
                Icon(
                    imageVector = if (server.expanded) {
                        AppIcons.ArrowUp
                    } else {
                        AppIcons.ArrowDown
                    },
                    contentDescription = stringResource(R.string.knotwork_tools_expand_server_cd),
                    tint = KnotworkTheme.extended.onSurfaceMuted,
                )
            }
        }
        ServerRowOverflowMenu(server = server, callbacks = callbacks)
    }
}

@Composable
private fun ServerRowOverflowMenu(server: McpServerRow, callbacks: ToolsCallbacks) {
    var menuOpen by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { menuOpen = true }) {
            Icon(
                imageVector = AppIcons.More,
                contentDescription = stringResource(R.string.knotwork_tools_row_overflow_cd),
                tint = KnotworkTheme.extended.onSurfaceMuted,
            )
        }
        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
            DropdownMenuItem(
                text = { Text(text = stringResource(R.string.knotwork_tools_row_action_refresh)) },
                onClick = {
                    menuOpen = false
                    callbacks.onServerRefresh(server.id)
                },
                leadingIcon = {
                    Icon(
                        imageVector = AppIcons.Refresh,
                        contentDescription = null,
                    )
                },
            )
            DropdownMenuItem(
                text = { Text(text = stringResource(R.string.knotwork_tools_row_action_edit)) },
                onClick = {
                    menuOpen = false
                    callbacks.onServerEdit(server.id)
                },
                leadingIcon = {
                    Icon(
                        imageVector = AppIcons.Edit,
                        contentDescription = null,
                    )
                },
            )
            DropdownMenuItem(
                text = {
                    Text(
                        text = stringResource(R.string.knotwork_tools_row_action_delete),
                        color = KnotworkTheme.extended.signalError,
                    )
                },
                onClick = {
                    menuOpen = false
                    callbacks.onServerRemove(server.id)
                },
                leadingIcon = {
                    Icon(
                        imageVector = AppIcons.Trash,
                        contentDescription = null,
                        tint = KnotworkTheme.extended.signalError,
                    )
                },
            )
        }
    }
}

@Composable
private fun McpToolEntryRowView(entry: McpToolEntry, callbacks: ToolsCallbacks, rowAlpha: Float = 1f) {
    Row(
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(KnotworkTheme.spacing.sp3),
        modifier = Modifier
            .fillMaxWidth()
            .alpha(rowAlpha)
            .clickable { callbacks.onMcpToolClick(entry.id) }
            .padding(
                start = KnotworkTheme.spacing.sp8,
                end = KnotworkTheme.spacing.sp4,
                top = KnotworkTheme.spacing.sp3,
                bottom = KnotworkTheme.spacing.sp3,
            ),
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(LeadingTileSize)
                .clip(KnotworkTheme.shapes.sm)
                .background(color = KnotworkTheme.extended.surface2),
        ) {
            Icon(
                imageVector = AppIcons.Edit,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.size(LeadingIconSize),
            )
        }
        Column(
            verticalArrangement = Arrangement.spacedBy(KnotworkTheme.spacing.sp1),
            modifier = Modifier.weight(1f),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(KnotworkTheme.spacing.sp2),
            ) {
                Text(
                    text = entry.name,
                    style = KnotworkTextStyles.MonoBase.copy(fontWeight = FontWeight.SemiBold),
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                RiskOutlinePill(risk = entry.risk)
            }
            if (entry.description.isNotBlank()) {
                Text(
                    text = entry.description,
                    style = KnotworkTextStyles.BodySm,
                    color = KnotworkTheme.extended.onSurfaceMuted,
                )
            }
        }
        Switch(
            checked = entry.enabled,
            onCheckedChange = { callbacks.onMcpToolToggle(entry.id, it) },
            colors = SwitchDefaults.colors(
                checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
                checkedTrackColor = MaterialTheme.colorScheme.primary,
                checkedBorderColor = MaterialTheme.colorScheme.primary,
            ),
            modifier = Modifier.scale(SWITCH_SCALE),
        )
    }
}

@Composable
private fun FormSectionLabel(text: String) {
    Text(
        text = text,
        style = KnotworkTextStyles.MonoSm,
        color = KnotworkTheme.extended.onSurfaceMuted,
    )
}

@Composable
private fun OutlinedFormTextField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    isError: Boolean,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(KnotworkTheme.shapes.sm)
            .border(
                width = 1.dp,
                color = if (isError) KnotworkTheme.extended.signalError else KnotworkTheme.extended.outlineStrong,
                shape = KnotworkTheme.shapes.sm,
            )
            .padding(KnotworkTheme.spacing.sp3),
    ) {
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            singleLine = true,
            textStyle = KnotworkTextStyles.MonoBase.copy(color = MaterialTheme.colorScheme.onSurface),
            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
            modifier = Modifier.fillMaxWidth(),
        )
        if (value.isEmpty()) {
            Text(
                text = placeholder,
                style = KnotworkTextStyles.MonoBase,
                color = KnotworkTheme.extended.onSurfaceMuted,
            )
        }
    }
}

@Composable
private fun AuthChip(option: McpAuthSelector, selected: Boolean, onClick: () -> Unit) {
    val borderColor = if (selected) MaterialTheme.colorScheme.primary else KnotworkTheme.extended.outlineStrong
    val labelColor = if (selected) MaterialTheme.colorScheme.primary else KnotworkTheme.extended.onSurfaceMuted
    Box(
        modifier = Modifier
            .clip(KnotworkTheme.shapes.full)
            .border(width = 1.dp, color = borderColor, shape = KnotworkTheme.shapes.full)
            .clickable(onClick = onClick)
            .padding(horizontal = KnotworkTheme.spacing.sp3, vertical = KnotworkTheme.spacing.sp1),
    ) {
        Text(
            text = option.label,
            style = KnotworkTextStyles.LabelMd,
            color = labelColor,
        )
    }
}

@Composable
private fun AuthFields(form: AddMcpServerForm, callbacks: McpServerConfigCallbacks) {
    when (form.authType) {
        McpAuthSelector.NONE -> Unit
        McpAuthSelector.BEARER -> OutlinedFormTextField(
            value = form.bearerToken,
            onValueChange = callbacks.onBearerTokenChange,
            placeholder = stringResource(R.string.knotwork_tools_form_auth_bearer_placeholder),
            isError = false,
        )
        McpAuthSelector.BASIC -> {
            OutlinedFormTextField(
                value = form.basicUsername,
                onValueChange = callbacks.onBasicUsernameChange,
                placeholder = stringResource(R.string.knotwork_tools_form_auth_basic_user_placeholder),
                isError = false,
            )
            OutlinedFormTextField(
                value = form.basicPassword,
                onValueChange = callbacks.onBasicPasswordChange,
                placeholder = stringResource(R.string.knotwork_tools_form_auth_basic_pass_placeholder),
                isError = false,
            )
        }
        McpAuthSelector.API_KEY -> {
            OutlinedFormTextField(
                value = form.apiKeyHeaderName,
                onValueChange = callbacks.onApiKeyHeaderNameChange,
                placeholder = stringResource(R.string.knotwork_tools_form_auth_apikey_name_placeholder),
                isError = false,
            )
            OutlinedFormTextField(
                value = form.apiKeyValue,
                onValueChange = callbacks.onApiKeyValueChange,
                placeholder = stringResource(R.string.knotwork_tools_form_auth_apikey_value_placeholder),
                isError = false,
            )
        }
    }
}

@Composable
private fun TransportChip(option: McpTransportOption, selected: Boolean, onClick: () -> Unit) {
    val borderColor = if (selected) MaterialTheme.colorScheme.primary else KnotworkTheme.extended.outlineStrong
    val labelColor = if (selected) MaterialTheme.colorScheme.primary else KnotworkTheme.extended.onSurfaceMuted
    Box(
        modifier = Modifier
            .clip(KnotworkTheme.shapes.full)
            .border(width = 1.dp, color = borderColor, shape = KnotworkTheme.shapes.full)
            .clickable(onClick = onClick)
            .padding(horizontal = KnotworkTheme.spacing.sp3, vertical = KnotworkTheme.spacing.sp1),
    ) {
        Text(
            text = option.label,
            style = KnotworkTextStyles.LabelMd,
            color = labelColor,
        )
    }
}

@Composable
private fun HeaderRow(
    row: McpHeaderRow,
    onKeyChange: (String) -> Unit,
    onValueChange: (String) -> Unit,
    onRemove: () -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(KnotworkTheme.spacing.sp2),
        modifier = Modifier.fillMaxWidth(),
    ) {
        OutlinedFormTextField(
            value = row.key,
            onValueChange = onKeyChange,
            placeholder = stringResource(R.string.knotwork_tools_form_header_key_placeholder),
            isError = false,
            modifier = Modifier.weight(1f),
        )
        OutlinedFormTextField(
            value = row.value,
            onValueChange = onValueChange,
            placeholder = stringResource(R.string.knotwork_tools_form_header_value_placeholder),
            isError = false,
            modifier = Modifier.weight(1f),
        )
        IconButton(onClick = onRemove) {
            Icon(
                imageVector = AppIcons.Trash,
                contentDescription = stringResource(R.string.knotwork_tools_form_header_remove_cd),
                tint = KnotworkTheme.extended.onSurfaceMuted,
            )
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
        // The outer `AppShellScaffold` already absorbs the system navigation
        // bar (and the in-app bottom-nav strip). Letting this Scaffold default
        // to `safeDrawing` would double-count the bottom inset and leave a
        // visible gap under the schema box.
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            app.knotwork.design.components.topbar.KnotworkTopAppBarShell {
                TopAppBar(
                    title = {
                        Text(
                            text = state.toolName,
                            style = KnotworkTextStyles.TitleMd,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = callbacks.onBack) {
                            Icon(
                                imageVector = AppIcons.Back,
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
            }
        },
    ) { padding ->
        Column(
            verticalArrangement = Arrangement.spacedBy(KnotworkTheme.spacing.sp3),
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(state = rememberScrollState())
                .padding(KnotworkTheme.spacing.sp4),
        ) {
            Text(
                text = state.serverDisplayName,
                style = KnotworkTextStyles.BodySm,
                color = KnotworkTheme.extended.onSurfaceMuted,
            )
            Text(
                text = state.description,
                style = KnotworkTextStyles.BodySm,
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
                Switch(
                    checked = state.enabled,
                    onCheckedChange = callbacks.onToggle,
                    modifier = Modifier.scale(SWITCH_SCALE),
                )
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
                    // Horizontal-scroll the monospace schema preview so long
                    // lines (deep JSON-Schema, MCP tool inputs) stay legible
                    // without wrapping — required at fontScale 2× so the
                    // schema-preview remains horizontally-scrollable.
                    Text(
                        text = state.schemaJson.orEmpty(),
                        style = KnotworkTextStyles.MonoBase,
                        color = KnotworkTheme.extended.consoleFg,
                        softWrap = false,
                        modifier = Modifier
                            .horizontalScroll(state = rememberScrollState())
                            .padding(KnotworkTheme.spacing.sp3),
                    )
                }
            }
        }
    }
}

// ------------------------- McpServerConfigContent -------------------------

/**
 * Full-screen MCP-server configuration surface. Hosts the rich form
 * (URL, optional display name, transport selector, repeating headers)
 * for both Add (`form.editingUrl == null`) and Edit
 * (`form.editingUrl == <original URL>`) flows.
 *
 * The host composable (app-layer screen) owns the [AddMcpServerForm]
 * state and translates submissions into persistence calls; this
 * composable renders the chrome and dispatches per-field callbacks.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun McpServerConfigContent(
    form: AddMcpServerForm,
    modifier: Modifier = Modifier,
    callbacks: McpServerConfigCallbacks = noopMcpServerConfigCallbacks(),
) {
    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.surface,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            app.knotwork.design.components.topbar.KnotworkTopAppBarShell {
                TopAppBar(
                    title = {
                        Text(
                            text = if (form.isEdit) {
                                stringResource(R.string.knotwork_tools_form_title_edit)
                            } else {
                                stringResource(R.string.knotwork_tools_form_title_add)
                            },
                            style = KnotworkTextStyles.TitleMd,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = callbacks.onCancel) {
                            Icon(
                                imageVector = AppIcons.Back,
                                contentDescription = stringResource(R.string.knotwork_tools_add_form_cancel),
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
        },
    ) { padding ->
        Column(
            verticalArrangement = Arrangement.spacedBy(KnotworkTheme.spacing.sp3),
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(state = rememberScrollState())
                .padding(KnotworkTheme.spacing.sp4),
        ) {
            FormSectionLabel(text = stringResource(R.string.knotwork_tools_add_form_header))
            OutlinedFormTextField(
                value = form.url,
                onValueChange = callbacks.onUrlChange,
                placeholder = stringResource(R.string.knotwork_tools_add_form_placeholder),
                isError = form.urlError != null,
            )
            if (form.urlError != null) {
                Text(
                    text = form.urlError,
                    style = KnotworkTextStyles.BodySm,
                    color = KnotworkTheme.extended.signalError,
                )
            }

            FormSectionLabel(text = stringResource(R.string.knotwork_tools_form_name_label))
            OutlinedFormTextField(
                value = form.name,
                onValueChange = callbacks.onNameChange,
                placeholder = stringResource(R.string.knotwork_tools_form_name_placeholder),
                isError = false,
            )

            FormSectionLabel(text = stringResource(R.string.knotwork_tools_form_transport_label))
            Row(horizontalArrangement = Arrangement.spacedBy(KnotworkTheme.spacing.sp2)) {
                // Render the selectable options plus, if the form was loaded
                // with a non-selectable choice (e.g. an older "Streamable HTTP"
                // pick), surface that chip too so the user can see what's
                // persisted instead of being silently downgraded.
                val visible = McpTransportOption.entries.filter { it.selectable || it == form.transport }
                visible.forEach { option ->
                    TransportChip(
                        option = option,
                        selected = form.transport == option,
                        onClick = { callbacks.onTransportSelect(option) },
                    )
                }
            }

            FormSectionLabel(text = stringResource(R.string.knotwork_tools_form_auth_label))
            Row(
                horizontalArrangement = Arrangement.spacedBy(KnotworkTheme.spacing.sp2),
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(state = rememberScrollState()),
            ) {
                McpAuthSelector.entries.forEach { option ->
                    AuthChip(
                        option = option,
                        selected = form.authType == option,
                        onClick = { callbacks.onAuthTypeSelect(option) },
                    )
                }
            }
            AuthFields(form = form, callbacks = callbacks)

            FormSectionLabel(text = stringResource(R.string.knotwork_tools_form_headers_label))
            form.headers.forEachIndexed { index, row ->
                HeaderRow(
                    row = row,
                    onKeyChange = { newKey -> callbacks.onHeaderChange(index, newKey, row.value) },
                    onValueChange = { newValue -> callbacks.onHeaderChange(index, row.key, newValue) },
                    onRemove = { callbacks.onHeaderRemove(index) },
                )
            }
            KnotworkTextButton(
                text = stringResource(R.string.knotwork_tools_form_headers_add),
                onClick = callbacks.onHeaderAdd,
            )

            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Spacer(modifier = Modifier.weight(1f))
                KnotworkTextButton(
                    text = stringResource(R.string.knotwork_tools_add_form_cancel),
                    onClick = callbacks.onCancel,
                )
                Spacer(modifier = Modifier.size(KnotworkTheme.spacing.sp2))
                KnotworkPrimaryButton(
                    text = if (form.isEdit) {
                        stringResource(R.string.knotwork_tools_form_submit_save)
                    } else {
                        stringResource(R.string.knotwork_tools_add_form_submit)
                    },
                    onClick = callbacks.onSubmit,
                    enabled = form.canSubmit,
                )
            }
        }
    }
}
