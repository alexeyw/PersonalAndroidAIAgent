@file:Suppress("LongMethod")

package app.knotwork.design.screens.settings

import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.knotwork.design.R
import app.knotwork.design.components.controls.KnotworkSegmentedControl
import app.knotwork.design.components.misc.StripedPlaceholder
import app.knotwork.design.components.topbar.KnotworkTopAppBarShell
import app.knotwork.design.icons.AppIcons
import app.knotwork.design.theme.KnotworkTheme
import app.knotwork.design.tokens.KnotworkTextStyles

/**
 * Static hub metadata for one category navigation row.
 *
 * @property id Stable category id (routed on tap).
 * @property icon Leading glyph (reuses an existing `AppIcons.*`).
 * @property titleRes Localised category title.
 * @property summaryRes Localised one-line summary.
 */
private data class HubCategoryMeta(
    val id: SettingsCategoryId,
    val icon: ImageVector,
    @StringRes val titleRes: Int,
    @StringRes val summaryRes: Int,
)

private val HUB_CATEGORIES: List<HubCategoryMeta> = listOf(
    HubCategoryMeta(
        SettingsCategoryId.Generation,
        AppIcons.Spark,
        R.string.knotwork_settings_cat_generation_title,
        R.string.knotwork_settings_cat_generation_summary,
    ),
    HubCategoryMeta(
        SettingsCategoryId.Models,
        AppIcons.Chip,
        R.string.knotwork_settings_cat_models_title,
        R.string.knotwork_settings_cat_models_summary,
    ),
    HubCategoryMeta(
        SettingsCategoryId.Memory,
        AppIcons.Brain,
        R.string.knotwork_settings_cat_memory_title,
        R.string.knotwork_settings_cat_memory_summary,
    ),
    HubCategoryMeta(
        SettingsCategoryId.Pipelines,
        AppIcons.Flow,
        R.string.knotwork_settings_cat_pipelines_title,
        R.string.knotwork_settings_cat_pipelines_summary,
    ),
    HubCategoryMeta(
        SettingsCategoryId.Tools,
        AppIcons.Tool,
        R.string.knotwork_settings_cat_tools_title,
        R.string.knotwork_settings_cat_tools_summary,
    ),
    HubCategoryMeta(
        SettingsCategoryId.Background,
        AppIcons.History,
        R.string.knotwork_settings_cat_background_title,
        R.string.knotwork_settings_cat_background_summary,
    ),
    HubCategoryMeta(
        SettingsCategoryId.Privacy,
        AppIcons.Shield,
        R.string.knotwork_settings_cat_privacy_title,
        R.string.knotwork_settings_cat_privacy_summary,
    ),
    HubCategoryMeta(
        SettingsCategoryId.About,
        AppIcons.Info,
        R.string.knotwork_settings_cat_about_title,
        R.string.knotwork_settings_cat_about_summary,
    ),
)

/**
 * Stateless settings hub: a top-bar subtitle, an inline "Basic" block of the six
 * most-touched cross-category controls, and the eight category navigation rows.
 *
 * The search field that the design reserves at the top of the hub is wired in a
 * later task (settings search); it is intentionally omitted here so the hub ships
 * without a non-functional control.
 *
 * @param state Immutable hub state.
 * @param modifier Layout modifier applied to the outer Box.
 * @param callbacks Interaction callbacks.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsHubContent(
    state: SettingsHubViewState,
    modifier: Modifier = Modifier,
    callbacks: SettingsCallbacks = noopSettingsCallbacks(),
) {
    Box(modifier = modifier.fillMaxSize()) {
        Scaffold(
            containerColor = MaterialTheme.colorScheme.surface,
            topBar = {
                KnotworkTopAppBarShell { HubTopBar(state = state, onBack = callbacks.onBack) }
            },
            contentWindowInsets = androidx.compose.foundation.layout.WindowInsets(0, 0, 0, 0),
            modifier = Modifier.fillMaxSize(),
        ) { padding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = KnotworkTheme.spacing.sp4, vertical = KnotworkTheme.spacing.sp3)
                    .testTag(SETTINGS_HUB_BODY_TEST_TAG),
                verticalArrangement = Arrangement.spacedBy(KnotworkTheme.spacing.sp4),
            ) {
                HubBasicBlock(state = state, callbacks = callbacks)
                HorizontalDivider(color = KnotworkTheme.extended.divider)
                HubCategoriesBlock(loading = state.loading, onOpenCategory = callbacks.onOpenCategory)
            }
        }
        if (state.restartRequiredMessage != null) {
            RestartBanner(message = state.restartRequiredMessage, onRestart = callbacks.onRestartClick)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HubTopBar(state: SettingsHubViewState, onBack: () -> Unit) {
    TopAppBar(
        title = {
            Column {
                Text(
                    text = stringResource(R.string.knotwork_settings_title),
                    style = KnotworkTextStyles.TitleMd,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                val subtitle = listOf("v${state.subtitleVersion}", state.subtitleChannel, state.subtitleBuildDate)
                    .filter { it.isNotBlank() }
                    .joinToString(" · ")
                if (subtitle.isNotBlank()) {
                    Text(
                        text = subtitle,
                        style = KnotworkTextStyles.MonoSm,
                        color = KnotworkTheme.extended.onSurfaceMuted,
                    )
                }
            }
        },
        navigationIcon = {
            IconButton(onClick = onBack) {
                Icon(
                    imageVector = AppIcons.Back,
                    contentDescription = stringResource(R.string.knotwork_settings_back),
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
private fun HubBasicBlock(state: SettingsHubViewState, callbacks: SettingsCallbacks) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(KnotworkTheme.spacing.sp3),
    ) {
        SettingsSectionLabel(text = stringResource(R.string.knotwork_settings_hub_basic))
        NavLinkRow(
            icon = AppIcons.Spark,
            title = stringResource(R.string.knotwork_settings_section_system_instructions),
            subtitle = state.systemInstructionsPreview.ifBlank {
                stringResource(R.string.knotwork_settings_hub_system_instructions_empty)
            },
            onClick = callbacks.onOpenSystemInstructions,
        )
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(KnotworkTheme.spacing.sp3),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(
                text = stringResource(R.string.knotwork_settings_restrictions_approve),
                style = KnotworkTextStyles.BodySm.copy(fontWeight = FontWeight.SemiBold),
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 2,
                modifier = Modifier.weight(1f),
            )
            KnotworkSegmentedControl(
                options = listOf(state.approveAllLabel, state.approveSensitiveLabel, state.approveNeverLabel),
                selectedIndex = state.approveSelection.toIndex(),
                onSelect = { callbacks.onApproveSelectionChange(approveOptionFromIndex(it)) },
                modifier = Modifier.weight(SEGMENTED_TRAILING_WEIGHT),
            )
        }
        IconToggleRow(
            icon = AppIcons.Shield,
            title = stringResource(R.string.knotwork_settings_restrictions_block_destructive),
            subtitle = "",
            checked = state.blockDestructive,
            onCheckedChange = callbacks.onBlockDestructiveChange,
        )
        BackendDropdownRow(
            title = stringResource(R.string.knotwork_settings_local_model_backend_title),
            backendLabel = state.backendLabel,
            selectedBackend = state.selectedBackend,
            options = state.backendOptions,
            onSelected = callbacks.onBackendSelected,
        )
        IconToggleRow(
            icon = AppIcons.Bolt,
            title = stringResource(R.string.knotwork_settings_notifications_long_running),
            subtitle = "",
            checked = state.longRunningEnabled,
            onCheckedChange = callbacks.onLongRunningToggle,
        )
        IconToggleRow(
            icon = AppIcons.Shield,
            title = stringResource(R.string.knotwork_settings_crash_reporting_label),
            subtitle = "",
            checked = state.crashReportingEnabled,
            onCheckedChange = callbacks.onCrashReportingToggle,
        )
    }
}

@Composable
private fun HubCategoriesBlock(loading: Boolean, onOpenCategory: (SettingsCategoryId) -> Unit) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(KnotworkTheme.spacing.sp3),
    ) {
        SettingsSectionLabel(text = stringResource(R.string.knotwork_settings_hub_categories))
        HUB_CATEGORIES.forEach { meta ->
            HubCategoryRow(meta = meta, loading = loading, onClick = { onOpenCategory(meta.id) })
        }
    }
}

@Composable
private fun HubCategoryRow(meta: HubCategoryMeta, loading: Boolean, onClick: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(KnotworkTheme.spacing.sp3),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .testTag(HUB_CATEGORY_ROW_TAG_PREFIX + meta.id.name),
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(HUB_CATEGORY_TILE_SIZE)
                .clip(KnotworkTheme.shapes.md)
                .background(color = KnotworkTheme.extended.surface2)
                .alpha(if (loading) LOADING_TILE_ALPHA else 1f),
        ) {
            Icon(imageVector = meta.icon, contentDescription = null, tint = KnotworkTheme.extended.onSurfaceMuted)
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = stringResource(meta.titleRes),
                style = KnotworkTextStyles.BodyBase.copy(fontWeight = FontWeight.SemiBold),
                color = MaterialTheme.colorScheme.onSurface,
            )
            if (loading) {
                StripedPlaceholder(
                    modifier = Modifier
                        .padding(top = KnotworkTheme.spacing.sp1)
                        .fillMaxWidth(LOADING_SUMMARY_FRACTION)
                        .height(LOADING_SUMMARY_HEIGHT),
                )
            } else {
                Text(
                    text = stringResource(meta.summaryRes),
                    style = KnotworkTextStyles.MonoSm,
                    color = KnotworkTheme.extended.onSurfaceMuted,
                )
            }
        }
        Icon(imageVector = AppIcons.ArrowR, contentDescription = null, tint = KnotworkTheme.extended.onSurfaceMuted)
    }
}

internal fun ApproveToolCallsOption.toIndex(): Int = when (this) {
    ApproveToolCallsOption.AllCalls -> 0
    ApproveToolCallsOption.Sensitive -> 1
    ApproveToolCallsOption.Never -> 2
}

internal fun approveOptionFromIndex(index: Int): ApproveToolCallsOption = when (index) {
    0 -> ApproveToolCallsOption.AllCalls
    1 -> ApproveToolCallsOption.Sensitive
    else -> ApproveToolCallsOption.Never
}

/** Test tag for the scrollable hub body. */
const val SETTINGS_HUB_BODY_TEST_TAG: String = "settings_hub_body"

private val HUB_CATEGORY_TILE_SIZE = 40.dp
private val LOADING_SUMMARY_HEIGHT = 9.dp
private const val LOADING_SUMMARY_FRACTION = 0.68f
private const val LOADING_TILE_ALPHA = 0.5f
