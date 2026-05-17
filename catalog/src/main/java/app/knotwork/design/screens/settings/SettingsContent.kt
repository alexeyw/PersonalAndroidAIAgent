@file:Suppress("MatchingDeclarationName") // Hosts SettingsContent and helpers.

package app.knotwork.design.screens.settings

import androidx.compose.foundation.background
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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.knotwork.design.R
import app.knotwork.design.components.buttons.KnotworkPrimaryButton
import app.knotwork.design.components.buttons.KnotworkTextButton
import app.knotwork.design.components.misc.KnotworkLoader
import app.knotwork.design.components.misc.StripedPlaceholder
import app.knotwork.design.theme.KnotworkTheme
import app.knotwork.design.tokens.KnotworkTextStyles

/**
 * Stateless Knotwork settings surface. Mirrors
 * `compose/screens/README.md §C7 · Settings`.
 *
 * The screen renders sectioned headers + rows. Each row is rendered via
 * [SettingsRow] which exposes a trailing control slot — `:app` code passes
 * the matching `Switch` / `Slider` / dropdown for the row.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsContent(
    state: SettingsViewState,
    modifier: Modifier = Modifier,
    callbacks: SettingsCallbacks = noopSettingsCallbacks(),
    trailingControl: @Composable (SettingsRowState) -> Unit = { _ -> },
) {
    Box(modifier = modifier.fillMaxSize()) {
        Scaffold(
            containerColor = MaterialTheme.colorScheme.surface,
            topBar = { SettingsTopBar() },
            modifier = Modifier.fillMaxSize(),
        ) { padding ->
            SettingsBody(state = state, padding = padding, trailingControl = trailingControl)
        }
        if (state.visualState == SettingsVisualState.RestartRequired) {
            RestartBanner(message = state.restartRequiredMessage.orEmpty(), onRestart = callbacks.onRestart)
        }
        if (state.visualState == SettingsVisualState.DestructiveAction) {
            DestructiveConfirmDialog(
                message = state.destructiveActionMessage.orEmpty(),
                onConfirm = callbacks.onDestructiveConfirm,
                onCancel = callbacks.onDestructiveCancel,
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SettingsTopBar() {
    TopAppBar(
        title = {
            Text(
                text = androidx.compose.ui.res.stringResource(R.string.knotwork_settings_title),
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

@Composable
private fun SettingsBody(
    state: SettingsViewState,
    padding: PaddingValues,
    trailingControl: @Composable (SettingsRowState) -> Unit,
) {
    when (state.visualState) {
        SettingsVisualState.Loading -> SettingsLoading(padding = padding)
        else -> LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(vertical = KnotworkTheme.spacing.sp2),
            verticalArrangement = Arrangement.spacedBy(KnotworkTheme.spacing.sp1),
        ) {
            state.sections.forEach { block ->
                item(key = "section-header-${block.section.name}") {
                    SettingsSectionHeader(section = block.section)
                }
                items(items = block.rows, key = { row -> "${block.section.name}-${row.id}" }) { row ->
                    SettingsRow(row = row) { trailingControl(row) }
                }
                if (block.errorMessage != null) {
                    item(key = "section-error-${block.section.name}") {
                        SettingsSectionErrorTile(message = block.errorMessage)
                    }
                }
            }
        }
    }
}

@Composable
private fun SettingsLoading(padding: PaddingValues) {
    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(padding),
        verticalArrangement = Arrangement.spacedBy(KnotworkTheme.spacing.sp2),
    ) {
        items(items = SettingsSection.entries) {
            StripedPlaceholder(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(LOADING_ROW_HEIGHT)
                    .padding(horizontal = KnotworkTheme.spacing.sp4),
            )
        }
    }
}

@Composable
private fun SettingsSectionHeader(section: SettingsSection) {
    val titleResId = when (section) {
        SettingsSection.Appearance -> R.string.knotwork_settings_section_appearance
        SettingsSection.Models -> R.string.knotwork_settings_section_models
        SettingsSection.Privacy -> R.string.knotwork_settings_section_privacy
        SettingsSection.Memory -> R.string.knotwork_settings_section_memory
        SettingsSection.Mcp -> R.string.knotwork_settings_section_mcp
        SettingsSection.About -> R.string.knotwork_settings_section_about
    }
    Text(
        text = androidx.compose.ui.res.stringResource(titleResId),
        style = KnotworkTextStyles.TitleMd,
        color = MaterialTheme.colorScheme.onSurface,
        modifier = Modifier
            .fillMaxWidth()
            .padding(
                horizontal = KnotworkTheme.spacing.sp4,
                vertical = KnotworkTheme.spacing.sp3,
            )
            .background(color = MaterialTheme.colorScheme.surface),
    )
}

/**
 * Single settings row: title + optional subtitle + trailing control slot.
 *
 * @param row the immutable row payload.
 * @param trailing slot exposed to the caller for the per-row control
 * (Switch / Slider / dropdown). The catalog owns no Material controls so
 * downstream features can compose any control they need.
 */
@Composable
fun SettingsRow(row: SettingsRowState, trailing: @Composable () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = KnotworkTheme.spacing.sp4, vertical = KnotworkTheme.spacing.sp2),
    ) {
        Row(
            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(KnotworkTheme.spacing.sp3),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(KnotworkTheme.spacing.sp1),
            ) {
                Text(
                    text = row.title,
                    style = KnotworkTextStyles.TitleMd,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                if (row.subtitle != null) {
                    Text(
                        text = row.subtitle,
                        style = KnotworkTextStyles.BodySm,
                        color = KnotworkTheme.extended.onSurfaceMuted,
                    )
                }
            }
            if (row.pendingChange) {
                Box(modifier = Modifier.size(KnotworkTheme.spacing.sp4)) { KnotworkLoader() }
            }
            trailing()
        }
        if (row.validationError != null) {
            Spacer(modifier = Modifier.height(KnotworkTheme.spacing.sp1))
            Text(
                text = row.validationError,
                style = KnotworkTextStyles.BodySm,
                color = KnotworkTheme.extended.signalError,
            )
        }
    }
}

@Composable
private fun SettingsSectionErrorTile(message: String) {
    Surface(
        shape = KnotworkTheme.shapes.md,
        color = KnotworkTheme.extended.surface1,
        modifier = Modifier
            .fillMaxWidth()
            .padding(KnotworkTheme.spacing.sp4),
    ) {
        Text(
            text = message,
            style = KnotworkTextStyles.BodyBase,
            color = KnotworkTheme.extended.signalError,
            modifier = Modifier.padding(KnotworkTheme.spacing.sp3),
        )
    }
}

@Composable
private fun RestartBanner(message: String, onRestart: () -> Unit) {
    Box(
        contentAlignment = androidx.compose.ui.Alignment.BottomCenter,
        modifier = Modifier.fillMaxSize().padding(KnotworkTheme.spacing.sp4),
    ) {
        Surface(
            shape = KnotworkTheme.shapes.md,
            color = KnotworkTheme.extended.surface3,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Row(
                verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = KnotworkTheme.spacing.sp3, vertical = KnotworkTheme.spacing.sp2),
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
private fun DestructiveConfirmDialog(message: String, onConfirm: () -> Unit, onCancel: () -> Unit) {
    AlertDialog(
        onDismissRequest = onCancel,
        title = {
            Text(text = androidx.compose.ui.res.stringResource(R.string.knotwork_settings_destructive_confirm_title))
        },
        text = { Text(text = message) },
        confirmButton = {
            KnotworkPrimaryButton(
                text = androidx.compose.ui.res.stringResource(
                    R.string.knotwork_settings_destructive_confirm_yes,
                ),
                onClick = onConfirm,
            )
        },
        dismissButton = {
            TextButton(onClick = onCancel) {
                Text(
                    text = androidx.compose.ui.res.stringResource(
                        R.string.knotwork_settings_destructive_confirm_cancel,
                    ),
                )
            }
        },
    )
}

/** Height of a single skeleton row in the Loading state. */
private val LOADING_ROW_HEIGHT = 56.dp
