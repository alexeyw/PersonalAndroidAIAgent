@file:Suppress(
    "MatchingDeclarationName",
    "LongMethod",
    "LargeClass",
    "TooManyFunctions",
)

package app.knotwork.design.screens.settings

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Block
import androidx.compose.material.icons.outlined.Bolt
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.knotwork.design.R
import app.knotwork.design.components.buttons.KnotworkPrimaryButton
import app.knotwork.design.components.buttons.KnotworkSecondaryButton
import app.knotwork.design.components.buttons.KnotworkTextButton
import app.knotwork.design.components.chips.ChipStyle
import app.knotwork.design.components.chips.KnotworkChip
import app.knotwork.design.components.controls.KnotworkSegmentedControl
import app.knotwork.design.components.misc.KnotworkLoader
import app.knotwork.design.components.misc.KnotworkSectionAction
import app.knotwork.design.components.misc.KnotworkStatCell
import app.knotwork.design.components.misc.StripedPlaceholder
import app.knotwork.design.theme.KnotworkTheme
import app.knotwork.design.tokens.KnotworkTextStyles

/**
 * Stateless Knotwork Settings surface — Phase 22 / Task 9 redesign.
 *
 * Renders the full settings stack described in `compose/screens/README.md
 * §C7`: identity card, system instructions textarea, restrictions panel,
 * LLM parameters sliders, local-model card, external-provider list,
 * memory stats + actions, notifications, and the privacy crash-reporting
 * toggle.
 *
 * The composable is stateless: every value originates in [state] and
 * every interaction routes through [callbacks]. Overlays
 * (restart-required banner, destructive typed-confirm dialog) flip on
 * via [state]`.visualState`.
 *
 * @param state Immutable input describing every card.
 * @param modifier Layout modifier applied to the outer Box.
 * @param callbacks Bundle of typed callbacks for every interaction.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsContent(
    state: SettingsViewState,
    modifier: Modifier = Modifier,
    callbacks: SettingsCallbacks = noopSettingsCallbacks(),
) {
    Box(modifier = modifier.fillMaxSize()) {
        Scaffold(
            containerColor = MaterialTheme.colorScheme.surface,
            topBar = { SettingsTopBar(state = state, onBack = callbacks.onBack, onSearch = callbacks.onSearchClick) },
            modifier = Modifier.fillMaxSize(),
        ) { padding ->
            when (state.visualState) {
                SettingsVisualState.Loading -> SettingsLoadingBody(padding)
                else -> SettingsBody(state = state, callbacks = callbacks, padding = padding)
            }
        }
        if (state.visualState == SettingsVisualState.RestartRequired) {
            RestartBanner(
                message = state.restartRequiredMessage.orEmpty(),
                onRestart = callbacks.onRestartClick,
            )
        }
        if (state.visualState == SettingsVisualState.DestructiveAction && state.destructiveAction != null) {
            DestructiveTypedConfirmDialog(
                payload = state.destructiveAction,
                onTypedConfirmChange = callbacks.onDestructiveTypedConfirmChange,
                onConfirm = callbacks.onDestructiveConfirm,
                onCancel = callbacks.onDestructiveCancel,
            )
        }
    }
}

// ─── Scaffolding ────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SettingsTopBar(state: SettingsViewState, onBack: () -> Unit, onSearch: () -> Unit) {
    TopAppBar(
        title = {
            Column {
                Text(
                    text = androidx.compose.ui.res.stringResource(R.string.knotwork_settings_title),
                    style = KnotworkTextStyles.TitleLg,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                val subtitle = listOf(
                    "v${state.subtitleVersion}",
                    state.subtitleChannel,
                    state.subtitleBuildDate,
                ).filter { it.isNotBlank() }.joinToString(" · ")
                if (subtitle.isNotBlank()) {
                    Text(
                        text = subtitle,
                        style = KnotworkTextStyles.LabelSm,
                        color = KnotworkTheme.extended.onSurfaceMuted,
                    )
                }
            }
        },
        navigationIcon = {
            IconButton(onClick = onBack) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = androidx.compose.ui.res.stringResource(R.string.knotwork_settings_back),
                    tint = MaterialTheme.colorScheme.onSurface,
                )
            }
        },
        actions = {
            IconButton(onClick = onSearch) {
                Icon(
                    imageVector = Icons.Outlined.Search,
                    contentDescription = androidx.compose.ui.res.stringResource(R.string.knotwork_settings_search_cd),
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

@Composable
private fun SettingsLoadingBody(padding: PaddingValues) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(padding)
            .padding(KnotworkTheme.spacing.sp4),
        verticalArrangement = Arrangement.spacedBy(KnotworkTheme.spacing.sp3),
    ) {
        repeat(SETTINGS_LOADING_ROWS) {
            StripedPlaceholder(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(LOADING_ROW_HEIGHT),
            )
        }
    }
}

@Composable
private fun SettingsBody(state: SettingsViewState, callbacks: SettingsCallbacks, padding: PaddingValues) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(padding)
            .verticalScroll(rememberScrollState())
            .padding(
                horizontal = KnotworkTheme.spacing.sp4,
                vertical = KnotworkTheme.spacing.sp3,
            )
            .testTag(SETTINGS_BODY_TEST_TAG),
        verticalArrangement = Arrangement.spacedBy(KnotworkTheme.spacing.sp6),
    ) {
        IdentityCard(state = state.identity)
        SystemInstructionsCard(state = state.systemInstructions, callbacks = callbacks)
        RestrictionsCard(state = state.restrictions, callbacks = callbacks)
        LlmParametersCard(state = state.llmParameters, callbacks = callbacks)
        LocalModelCard(state = state.localModel, callbacks = callbacks)
        ExternalProvidersCard(state = state.externalProviders, callbacks = callbacks)
        MemoryCard(state = state.memory, callbacks = callbacks)
        NotificationsCard(state = state.notifications, callbacks = callbacks)
        PrivacyCard(state = state.privacy, callbacks = callbacks)
        Spacer(modifier = Modifier.height(KnotworkTheme.spacing.sp6))
    }
}

// ─── Identity card ──────────────────────────────────────────────────────────

@Composable
private fun IdentityCard(state: IdentityCardState?) {
    Surface(
        shape = KnotworkTheme.shapes.md,
        color = KnotworkTheme.extended.surface1,
        modifier = Modifier
            .fillMaxWidth()
            .testTag(IDENTITY_CARD_TEST_TAG),
    ) {
        if (state == null) {
            StripedPlaceholder(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(IDENTITY_LOADING_HEIGHT)
                    .padding(KnotworkTheme.spacing.sp4),
            )
            return@Surface
        }
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(KnotworkTheme.spacing.sp3),
            modifier = Modifier
                .fillMaxWidth()
                .padding(KnotworkTheme.spacing.sp4),
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(IDENTITY_AVATAR_SIZE)
                    .clip(KnotworkTheme.shapes.full)
                    .background(color = KnotworkTheme.extended.surface3),
            ) {
                Text(
                    text = state.avatarInitials,
                    style = KnotworkTextStyles.LabelMd.copy(fontWeight = FontWeight.SemiBold),
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = state.displayName,
                    style = KnotworkTextStyles.TitleMd.copy(fontWeight = FontWeight.SemiBold),
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = state.metaLine,
                    style = KnotworkTextStyles.MonoSm,
                    color = KnotworkTheme.extended.onSurfaceMuted,
                )
            }
        }
    }
}

// ─── System instructions card ───────────────────────────────────────────────

@Composable
private fun SystemInstructionsCard(state: SystemInstructionsCardState, callbacks: SettingsCallbacks) {
    SettingsSection(
        title = androidx.compose.ui.res.stringResource(R.string.knotwork_settings_section_system_instructions),
        trailing = {
            KnotworkSectionAction(
                label = androidx.compose.ui.res.stringResource(
                    R.string.knotwork_settings_system_instructions_insert,
                ),
                icon = Icons.Outlined.Add,
                onClick = callbacks.onInsertVariableClick,
            )
        },
    ) {
        OutlinedTextField(
            value = state.value,
            onValueChange = callbacks.onSystemInstructionsChange,
            placeholder = { Text(state.placeholder, style = KnotworkTextStyles.MonoSm) },
            textStyle = KnotworkTextStyles.MonoSm,
            minLines = SYSTEM_INSTRUCTIONS_MIN_LINES,
            maxLines = SYSTEM_INSTRUCTIONS_MAX_LINES,
            isError = state.validationError != null,
            shape = KnotworkTheme.shapes.md,
            modifier = Modifier
                .fillMaxWidth()
                .testTag(SYSTEM_INSTRUCTIONS_FIELD_TEST_TAG),
        )
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(KnotworkTheme.spacing.sp1),
            modifier = Modifier.fillMaxWidth(),
        ) {
            // Variable chips wrap into multiple rows via a simple horizontal Row +
            // FlowRow-equivalent (custom layout) — keep it minimal with an
            // overflowing Row scrollable horizontally so 6 chips always fit.
            state.variableChips.forEach { placeholder ->
                KnotworkChip(
                    label = placeholder,
                    style = ChipStyle.Outline,
                    onClick = { callbacks.onChipInsert(placeholder) },
                )
            }
        }
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth(),
        ) {
            val helper = state.helperText
            if (helper.isNotBlank()) {
                Text(
                    text = helper,
                    style = KnotworkTextStyles.BodySm,
                    color = KnotworkTheme.extended.onSurfaceMuted,
                    modifier = Modifier.weight(1f),
                )
            } else {
                Spacer(modifier = Modifier.weight(1f))
            }
            Text(
                text = androidx.compose.ui.res.stringResource(
                    R.string.knotwork_settings_system_instructions_counter,
                    state.characterCount,
                    state.characterLimit,
                    state.approximateTokens,
                ),
                style = KnotworkTextStyles.LabelSm,
                color = KnotworkTheme.extended.onSurfaceMuted,
            )
        }
        if (state.validationError != null) {
            Text(
                text = state.validationError,
                style = KnotworkTextStyles.BodySm,
                color = KnotworkTheme.extended.signalError,
            )
        }
    }
}

// ─── Restrictions card ──────────────────────────────────────────────────────

@Composable
private fun RestrictionsCard(state: RestrictionsCardState, callbacks: SettingsCallbacks) {
    SettingsSection(
        title = androidx.compose.ui.res.stringResource(R.string.knotwork_settings_section_restrictions),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(KnotworkTheme.spacing.sp3),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(
                text = androidx.compose.ui.res.stringResource(R.string.knotwork_settings_restrictions_approve),
                style = KnotworkTextStyles.BodyBase,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f),
            )
            KnotworkSegmentedControl(
                options = listOf(state.approveAllLabel, state.approveSensitiveLabel, state.approveNeverLabel),
                selectedIndex = when (state.approveSelection) {
                    ApproveToolCallsOption.AllCalls -> 0
                    ApproveToolCallsOption.Sensitive -> 1
                    ApproveToolCallsOption.Never -> 2
                },
                onSelect = { index ->
                    val option = when (index) {
                        0 -> ApproveToolCallsOption.AllCalls
                        1 -> ApproveToolCallsOption.Sensitive
                        else -> ApproveToolCallsOption.Never
                    }
                    callbacks.onApproveSelectionChange(option)
                },
                modifier = Modifier.weight(SEGMENTED_TRAILING_WEIGHT),
            )
        }
        IconToggleRow(
            icon = Icons.Outlined.Shield,
            title = androidx.compose.ui.res.stringResource(R.string.knotwork_settings_restrictions_block_destructive),
            subtitle = state.blockDestructiveSubtitle,
            checked = state.blockDestructive,
            onCheckedChange = callbacks.onBlockDestructiveChange,
        )
        IconToggleRow(
            icon = Icons.Outlined.Block,
            title = androidx.compose.ui.res.stringResource(R.string.knotwork_settings_restrictions_block_network),
            subtitle = state.blockNetworkSubtitle,
            checked = state.blockNetwork,
            onCheckedChange = callbacks.onBlockNetworkChange,
        )
        IconValueRow(
            icon = Icons.Outlined.WarningAmber,
            title = androidx.compose.ui.res.stringResource(R.string.knotwork_settings_restrictions_cap_steps),
            subtitle = state.capStepsSubtitle,
            valueLabel = state.capSteps.toString(),
        )
    }
}

@Composable
private fun IconToggleRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(KnotworkTheme.spacing.sp3),
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) },
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = KnotworkTheme.extended.onSurfaceMuted,
            modifier = Modifier.size(KnotworkTheme.spacing.sp5),
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = KnotworkTextStyles.BodyBase.copy(fontWeight = FontWeight.SemiBold),
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = subtitle,
                style = KnotworkTextStyles.BodySm,
                color = KnotworkTheme.extended.onSurfaceMuted,
            )
        }
        Switch(
            checked = checked,
            onCheckedChange = null,
            colors = SwitchDefaults.colors(
                checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
                checkedTrackColor = MaterialTheme.colorScheme.primary,
                checkedBorderColor = MaterialTheme.colorScheme.primary,
            ),
        )
    }
}

@Composable
private fun IconValueRow(icon: ImageVector, title: String, subtitle: String, valueLabel: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(KnotworkTheme.spacing.sp3),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = KnotworkTheme.extended.onSurfaceMuted,
            modifier = Modifier.size(KnotworkTheme.spacing.sp5),
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = KnotworkTextStyles.BodyBase.copy(fontWeight = FontWeight.SemiBold),
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = subtitle,
                style = KnotworkTextStyles.BodySm,
                color = KnotworkTheme.extended.onSurfaceMuted,
            )
        }
        Text(
            text = valueLabel,
            style = KnotworkTextStyles.TitleMd.copy(fontWeight = FontWeight.SemiBold),
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

// ─── LLM parameters card ────────────────────────────────────────────────────

@Composable
private fun LlmParametersCard(state: LlmParametersCardState, callbacks: SettingsCallbacks) {
    SettingsSection(
        title = androidx.compose.ui.res.stringResource(R.string.knotwork_settings_section_llm),
        trailing = {
            KnotworkSectionAction(
                label = androidx.compose.ui.res.stringResource(R.string.knotwork_settings_llm_reset),
                onClick = callbacks.onResetLlmDefaults,
            )
        },
    ) {
        state.sliders.forEach { slider ->
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag(SLIDER_ROW_TAG_PREFIX + slider.id),
                verticalArrangement = Arrangement.spacedBy(KnotworkTheme.spacing.sp1),
            ) {
                Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = slider.title,
                        style = KnotworkTextStyles.BodyBase.copy(fontWeight = FontWeight.SemiBold),
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        text = slider.valueLabel,
                        style = KnotworkTextStyles.MonoSm,
                        color = KnotworkTheme.extended.onSurfaceMuted,
                    )
                }
                Slider(
                    value = slider.value,
                    onValueChange = { newValue -> callbacks.onSliderChange(slider.id, newValue) },
                    valueRange = slider.valueRange,
                    steps = slider.steps,
                )
            }
        }
    }
}

// ─── Local-model card ───────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LocalModelCard(state: LocalModelCardState, callbacks: SettingsCallbacks) {
    SettingsSection(
        title = androidx.compose.ui.res.stringResource(R.string.knotwork_settings_section_local_model),
        trailing = {
            KnotworkSectionAction(
                label = androidx.compose.ui.res.stringResource(R.string.knotwork_settings_local_model_manage),
                icon = Icons.Outlined.Download,
                onClick = callbacks.onManageModelsClick,
            )
        },
    ) {
        Surface(
            shape = KnotworkTheme.shapes.md,
            color = KnotworkTheme.extended.surface1,
            border = BorderStroke(SectionCardBorder, KnotworkTheme.extended.divider),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(KnotworkTheme.spacing.sp3),
                modifier = Modifier.padding(KnotworkTheme.spacing.sp3),
            ) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(LOCAL_MODEL_TILE_SIZE)
                        .clip(KnotworkTheme.shapes.sm)
                        .background(color = KnotworkTheme.extended.surface2),
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Bolt,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurface,
                    )
                }
                Column(modifier = Modifier.weight(1f)) {
                    val modelName = state.modelName ?: androidx.compose.ui.res.stringResource(
                        R.string.knotwork_settings_local_model_empty,
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(
                            KnotworkTheme.spacing.sp2,
                        ),
                    ) {
                        Text(
                            text = modelName,
                            style = KnotworkTextStyles.BodyBase.copy(fontWeight = FontWeight.SemiBold),
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        if (state.modelName != null) {
                            ActivePill()
                        }
                    }
                    if (state.metaLine != null) {
                        Text(
                            text = state.metaLine,
                            style = KnotworkTextStyles.MonoSm,
                            color = KnotworkTheme.extended.onSurfaceMuted,
                        )
                    }
                }
                KnotworkTextButton(
                    text = androidx.compose.ui.res.stringResource(R.string.knotwork_settings_local_model_change),
                    onClick = callbacks.onChangeModelClick,
                )
            }
        }

        var backendExpanded by remember { mutableStateOf(false) }
        ExposedDropdownMenuBox(
            expanded = backendExpanded,
            onExpandedChange = { backendExpanded = it },
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(KnotworkTheme.spacing.sp3),
                modifier = Modifier
                    .fillMaxWidth()
                    .menuAnchor(
                        androidx.compose.material3.ExposedDropdownMenuAnchorType.PrimaryNotEditable,
                        enabled = true,
                    ),
            ) {
                Icon(
                    imageVector = Icons.Outlined.Bolt,
                    contentDescription = null,
                    tint = KnotworkTheme.extended.onSurfaceMuted,
                    modifier = Modifier.size(KnotworkTheme.spacing.sp5),
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = androidx.compose.ui.res.stringResource(
                            R.string.knotwork_settings_local_model_backend_title,
                        ),
                        style = KnotworkTextStyles.BodyBase.copy(fontWeight = FontWeight.SemiBold),
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        text = state.backendLabel,
                        style = KnotworkTextStyles.BodySm,
                        color = KnotworkTheme.extended.onSurfaceMuted,
                    )
                }
                Text(
                    text = state.selectedBackend,
                    style = KnotworkTextStyles.MonoSm,
                    color = KnotworkTheme.extended.onSurfaceMuted,
                )
                Icon(
                    imageVector = Icons.Outlined.ExpandMore,
                    contentDescription = null,
                    tint = KnotworkTheme.extended.onSurfaceMuted,
                )
            }
            ExposedDropdownMenu(
                expanded = backendExpanded,
                onDismissRequest = { backendExpanded = false },
            ) {
                state.backendOptions.forEach { option ->
                    DropdownMenuItem(
                        text = { Text(option) },
                        onClick = {
                            callbacks.onBackendSelected(option)
                            backendExpanded = false
                        },
                    )
                }
            }
        }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(KnotworkTheme.spacing.sp3),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Icon(
                imageVector = Icons.Outlined.Bolt,
                contentDescription = null,
                tint = KnotworkTheme.extended.onSurfaceMuted,
                modifier = Modifier.size(KnotworkTheme.spacing.sp5),
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = androidx.compose.ui.res.stringResource(
                        R.string.knotwork_settings_local_model_test_title,
                    ),
                    style = KnotworkTextStyles.BodyBase.copy(fontWeight = FontWeight.SemiBold),
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = state.testProbeText,
                    style = KnotworkTextStyles.BodySm,
                    color = if (state.testProbeIsError) {
                        KnotworkTheme.extended.signalError
                    } else {
                        KnotworkTheme.extended.onSurfaceMuted
                    },
                )
            }
            KnotworkSecondaryButton(
                text = androidx.compose.ui.res.stringResource(R.string.knotwork_settings_local_model_test_run),
                onClick = callbacks.onTestBackendClick,
            )
        }
    }
}

@Composable
private fun ActivePill() {
    Surface(
        shape = KnotworkTheme.shapes.full,
        color = KnotworkTheme.extended.signalSuccess.copy(alpha = ACTIVE_PILL_ALPHA),
    ) {
        Text(
            text = androidx.compose.ui.res.stringResource(R.string.knotwork_settings_local_model_active_pill),
            style = KnotworkTextStyles.LabelSm.copy(fontWeight = FontWeight.SemiBold),
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(
                horizontal = KnotworkTheme.spacing.sp2,
                vertical = KnotworkTheme.spacing.sp1,
            ),
        )
    }
}

// ─── External providers list ────────────────────────────────────────────────

@Composable
private fun ExternalProvidersCard(state: ExternalProvidersCardState, callbacks: SettingsCallbacks) {
    SettingsSection(
        title = androidx.compose.ui.res.stringResource(R.string.knotwork_settings_section_providers),
        trailing = {
            KnotworkSectionAction(
                label = androidx.compose.ui.res.stringResource(R.string.knotwork_settings_providers_add),
                icon = Icons.Outlined.Add,
                onClick = callbacks.onAddProviderClick,
            )
        },
    ) {
        state.rows.forEach { row ->
            ProviderNavRow(row = row, onClick = { callbacks.onProviderRowClick(row.id) })
        }
    }
}

@Composable
private fun ProviderNavRow(row: ProviderRowState, onClick: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(KnotworkTheme.spacing.sp3),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .testTag(PROVIDER_ROW_TAG_PREFIX + row.id),
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(LOCAL_MODEL_TILE_SIZE)
                .clip(KnotworkTheme.shapes.sm)
                .background(color = KnotworkTheme.extended.surface2),
        ) {
            Icon(
                imageVector = Icons.Outlined.Bolt,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurface,
            )
        }
        Column(modifier = Modifier.weight(1f)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(KnotworkTheme.spacing.sp2),
            ) {
                Text(
                    text = row.title,
                    style = KnotworkTextStyles.BodyBase.copy(fontWeight = FontWeight.SemiBold),
                    color = MaterialTheme.colorScheme.onSurface,
                )
                if (row.isLan) {
                    Surface(
                        shape = KnotworkTheme.shapes.full,
                        color = KnotworkTheme.extended.surface2,
                    ) {
                        Text(
                            text = androidx.compose.ui.res.stringResource(R.string.knotwork_settings_providers_lan),
                            style = KnotworkTextStyles.LabelSm.copy(fontWeight = FontWeight.SemiBold),
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.padding(
                                horizontal = KnotworkTheme.spacing.sp2,
                                vertical = KnotworkTheme.spacing.sp1,
                            ),
                        )
                    }
                }
            }
            val subtitle = when {
                row.fingerprint == null -> androidx.compose.ui.res.stringResource(
                    R.string.knotwork_settings_providers_not_configured,
                )
                row.endpointHint != null -> "${row.endpointHint} · ${row.model.orEmpty()}"
                row.model != null -> "${row.fingerprint} · ${row.model}"
                else -> row.fingerprint
            }
            Text(
                text = subtitle,
                style = KnotworkTextStyles.MonoSm,
                color = KnotworkTheme.extended.onSurfaceMuted,
            )
        }
        Icon(
            imageVector = Icons.Outlined.ChevronRight,
            contentDescription = null,
            tint = KnotworkTheme.extended.onSurfaceMuted,
        )
    }
}

// ─── Memory card ────────────────────────────────────────────────────────────

@Composable
private fun MemoryCard(state: MemoryCardState, callbacks: SettingsCallbacks) {
    SettingsSection(
        title = androidx.compose.ui.res.stringResource(R.string.knotwork_settings_section_memory),
    ) {
        Surface(
            shape = KnotworkTheme.shapes.md,
            color = KnotworkTheme.extended.surface1,
            border = BorderStroke(SectionCardBorder, KnotworkTheme.extended.divider),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(KnotworkTheme.spacing.sp3),
                horizontalArrangement = Arrangement.spacedBy(KnotworkTheme.spacing.sp3),
            ) {
                state.stats.forEach { cell ->
                    KnotworkStatCell(value = cell.value, label = cell.label, modifier = Modifier.weight(1f))
                }
            }
        }
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(KnotworkTheme.spacing.sp1),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = state.autoSummarizeLabel,
                    style = KnotworkTextStyles.BodyBase.copy(fontWeight = FontWeight.SemiBold),
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = "${state.autoSummarizeThreshold} %",
                    style = KnotworkTextStyles.MonoSm,
                    color = KnotworkTheme.extended.onSurfaceMuted,
                )
            }
            Slider(
                value = state.autoSummarizeThreshold.toFloat(),
                onValueChange = { newValue -> callbacks.onAutoSummarizeChange(newValue.toInt()) },
                valueRange = 0f..100f,
            )
        }
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(
                KnotworkTheme.spacing.sp3,
            ),
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = state.embeddingTitle,
                    style = KnotworkTextStyles.BodyBase.copy(fontWeight = FontWeight.SemiBold),
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = state.embeddingSubtitle,
                    style = KnotworkTextStyles.MonoSm,
                    color = KnotworkTheme.extended.onSurfaceMuted,
                )
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(KnotworkTheme.spacing.sp2),
        ) {
            KnotworkSecondaryButton(
                text = state.exportLabel,
                onClick = callbacks.onExportMemoryClick,
                modifier = Modifier.weight(1f),
            )
            KnotworkSecondaryButton(
                text = state.reembedLabel,
                onClick = callbacks.onReembedClick,
                modifier = Modifier.weight(1f),
            )
            KnotworkSecondaryButton(
                text = state.clearLabel,
                onClick = callbacks.onClearMemoryClick,
                modifier = Modifier.weight(1f),
            )
        }
        val progress = state.reembedProgressPercent
        if (progress != null) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(KnotworkTheme.spacing.sp1),
            ) {
                LinearProgressIndicator(
                    progress = { progress / 100f },
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(
                        KnotworkTheme.spacing.sp1,
                    ),
                ) {
                    Box(modifier = Modifier.size(KnotworkTheme.spacing.sp4)) { KnotworkLoader() }
                    Text(
                        text = "Re-embedding · $progress %",
                        style = KnotworkTextStyles.BodySm,
                        color = KnotworkTheme.extended.onSurfaceMuted,
                    )
                }
            }
        }
    }
}

// ─── Notifications card ────────────────────────────────────────────────────

@Composable
private fun NotificationsCard(state: NotificationsCardState, callbacks: SettingsCallbacks) {
    SettingsSection(
        title = androidx.compose.ui.res.stringResource(R.string.knotwork_settings_section_notifications),
    ) {
        IconToggleRow(
            icon = Icons.Outlined.Refresh,
            title = androidx.compose.ui.res.stringResource(R.string.knotwork_settings_notifications_long_running),
            subtitle = androidx.compose.ui.res.stringResource(
                R.string.knotwork_settings_notifications_long_running_subtitle,
            ),
            checked = state.longRunningEnabled,
            onCheckedChange = callbacks.onLongRunningToggle,
        )
    }
}

// ─── Privacy card ───────────────────────────────────────────────────────────

@Composable
private fun PrivacyCard(state: PrivacyCardState, callbacks: SettingsCallbacks) {
    SettingsSection(
        title = androidx.compose.ui.res.stringResource(R.string.knotwork_settings_section_privacy),
    ) {
        IconToggleRow(
            icon = Icons.Outlined.Shield,
            title = androidx.compose.ui.res.stringResource(R.string.knotwork_settings_crash_reporting_label),
            subtitle = androidx.compose.ui.res.stringResource(R.string.knotwork_settings_crash_reporting_hint),
            checked = state.crashReportingEnabled,
            onCheckedChange = callbacks.onCrashReportingToggle,
        )
        KnotworkTextButton(
            text = androidx.compose.ui.res.stringResource(R.string.knotwork_settings_reset_button),
            onClick = callbacks.onResetSettingsClick,
        )
    }
}

// ─── Section frame ─────────────────────────────────────────────────────────

@Composable
private fun SettingsSection(title: String, trailing: @Composable () -> Unit = {}, content: @Composable () -> Unit) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(KnotworkTheme.spacing.sp3),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = title.uppercase(),
                style = KnotworkTextStyles.LabelSm.copy(fontWeight = FontWeight.SemiBold),
                color = KnotworkTheme.extended.onSurfaceMuted,
                modifier = Modifier.weight(1f),
            )
            trailing()
        }
        content()
    }
}

// ─── Restart banner & destructive dialog ────────────────────────────────────

@Composable
private fun RestartBanner(message: String, onRestart: () -> Unit) {
    Box(
        contentAlignment = Alignment.BottomCenter,
        modifier = Modifier
            .fillMaxSize()
            .padding(KnotworkTheme.spacing.sp4),
    ) {
        Surface(
            shape = KnotworkTheme.shapes.md,
            color = KnotworkTheme.extended.surface3,
            modifier = Modifier
                .fillMaxWidth()
                .testTag(RESTART_BANNER_TEST_TAG),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(
                        horizontal = KnotworkTheme.spacing.sp3,
                        vertical = KnotworkTheme.spacing.sp2,
                    ),
            ) {
                Text(
                    text = message,
                    style = KnotworkTextStyles.BodyBase,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f),
                )
                KnotworkTextButton(
                    text = androidx.compose.ui.res.stringResource(R.string.knotwork_settings_restart_action),
                    onClick = onRestart,
                )
            }
        }
    }
}

@Composable
private fun DestructiveTypedConfirmDialog(
    payload: DestructiveActionState,
    onTypedConfirmChange: (String) -> Unit,
    onConfirm: () -> Unit,
    onCancel: () -> Unit,
) {
    val canConfirm = payload.pendingInput.trim().equals(payload.keyword, ignoreCase = true)
    AlertDialog(
        onDismissRequest = onCancel,
        title = { Text(payload.title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(KnotworkTheme.spacing.sp2)) {
                Text(text = payload.body, style = KnotworkTextStyles.BodyBase)
                OutlinedTextField(
                    value = payload.pendingInput,
                    onValueChange = onTypedConfirmChange,
                    placeholder = { Text(payload.hint, style = KnotworkTextStyles.BodySm) },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag(DESTRUCTIVE_TYPED_FIELD_TEST_TAG),
                )
            }
        },
        confirmButton = {
            KnotworkPrimaryButton(
                text = androidx.compose.ui.res.stringResource(R.string.knotwork_settings_destructive_confirm),
                onClick = onConfirm,
                enabled = canConfirm,
                modifier = Modifier.testTag(DESTRUCTIVE_CONFIRM_BUTTON_TEST_TAG),
            )
        },
        dismissButton = {
            TextButton(onClick = onCancel) {
                Text(
                    text = androidx.compose.ui.res.stringResource(R.string.knotwork_settings_destructive_cancel),
                )
            }
        },
    )
}

// ─── Constants ─────────────────────────────────────────────────────────────

/** Test tag for the scrollable Settings body — used by instrumented tests. */
const val SETTINGS_BODY_TEST_TAG: String = "settings_body"
const val IDENTITY_CARD_TEST_TAG: String = "settings_identity_card"
const val SYSTEM_INSTRUCTIONS_FIELD_TEST_TAG: String = "settings_system_instructions_field"
const val RESTART_BANNER_TEST_TAG: String = "settings_restart_banner"
const val DESTRUCTIVE_TYPED_FIELD_TEST_TAG: String = "settings_destructive_typed_field"
const val DESTRUCTIVE_CONFIRM_BUTTON_TEST_TAG: String = "settings_destructive_confirm"
const val SLIDER_ROW_TAG_PREFIX: String = "settings_slider_"
const val PROVIDER_ROW_TAG_PREFIX: String = "settings_provider_row_"

private val LOCAL_MODEL_TILE_SIZE = 40.dp
private val SectionCardBorder = 1.dp
private val LOADING_ROW_HEIGHT = 56.dp
private val IDENTITY_LOADING_HEIGHT = 72.dp
private val IDENTITY_AVATAR_SIZE = 48.dp
private const val SETTINGS_LOADING_ROWS = 6
private const val SYSTEM_INSTRUCTIONS_MIN_LINES = 5
private const val SYSTEM_INSTRUCTIONS_MAX_LINES = 12
private const val ACTIVE_PILL_ALPHA = 0.18f

/**
 * Relative weight of the trailing segmented control inside the restrictions
 * card's "Approve tool calls" row. The label takes weight 1 and the
 * segmented control takes 1.5 so the three options stay readable.
 */
private const val SEGMENTED_TRAILING_WEIGHT = 1.5f
