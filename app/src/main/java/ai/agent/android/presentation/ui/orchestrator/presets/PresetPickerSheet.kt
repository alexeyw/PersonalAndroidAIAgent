package ai.agent.android.presentation.ui.orchestrator.presets

import ai.agent.android.R
import ai.agent.android.domain.models.PipelinePreset
import ai.agent.android.domain.models.PresetCategory
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.knotwork.design.icons.AppIcons
import app.knotwork.design.theme.KnotworkTheme
import app.knotwork.design.tokens.KnotworkTextStyles

/**
 * Modal bottom sheet exposing the bundled / user pipeline-preset catalogue
 * as a "tap to instantiate" surface. Layout:
 *
 *  - Header row: `Choose a preset` title + `X` close icon.
 *  - `PresetTabRow` (shared with the manager screen) — Bundled · N / Mine · N.
 *  - `PresetCategoryChipRow` (shared) — `KnotworkFilterChip`s with the
 *    `All · 6 / Local · 2 / Cloud · 1 / Hybrid · 3` pattern.
 *  - `LazyColumn` of `PresetPickerRow` cards — radio-button leading
 *    selection, name + colored category badge, 2-line description, mono
 *    graph-flow preview.
 *  - Footer Row: `Cancel` + primary `✓ Use this preset` CTA. The CTA is
 *    enabled only while a row is selected and a load is not in-flight.
 *
 * Selection state lives inside the sheet — the host (`PipelineLibraryScreen`)
 * only sees the chosen preset id on confirmation. Tab and category-chip
 * selection are owned by [PipelinePresetsViewModel] so they survive
 * configuration changes.
 *
 * @param state Current picker state from `PipelinePresetsViewModel`.
 * @param onTabSelected Forwards tab clicks to the VM.
 * @param onCategorySelected Forwards category-chip clicks to the VM.
 * @param onUsePreset Invoked with the selected preset id when the user
 *   taps the CTA.
 * @param onDismiss Invoked when the user dismisses the sheet without
 *   selecting a preset (drag / scrim tap / X / Cancel).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PresetPickerSheet(
    state: PipelinePresetsUiState,
    onTabSelected: (PresetPickerTab) -> Unit,
    onCategorySelected: (PresetCategory?) -> Unit,
    onUsePreset: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var selectedPresetId by remember { mutableStateOf<String?>(null) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        modifier = Modifier.testTag(tag = PRESET_PICKER_SHEET_TEST_TAG),
        containerColor = MaterialTheme.colorScheme.surface,
    ) {
        // Constrain the sheet body to a deterministic fraction of the
        // viewport so the LazyColumn has a finite vertical budget and the
        // footer Row stays pinned to the bottom. Without this clamp, the
        // outer Column wraps content and the CTA scrolls off-screen when
        // the row list is long.
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(SHEET_HEIGHT_FRACTION),
        ) {
            // Header row: title + close. Compact `titleMedium` —
            // `titleLarge` overpowered the row content.
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(
                        start = KnotworkTheme.spacing.sp6,
                        end = KnotworkTheme.spacing.sp2,
                        bottom = KnotworkTheme.spacing.sp2,
                    ),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(R.string.orchestrator_preset_picker_title),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = onDismiss) {
                    Icon(AppIcons.X, contentDescription = null)
                }
            }

            PresetTabRow(
                activeTab = state.activeTab,
                bundledCount = state.bundledPresets.size,
                userCount = state.userPresets.size,
                onTabSelected = onTabSelected,
            )

            PresetCategoryChipRow(
                visibleCategories = state.visibleCategories,
                presets = state.presetsForActiveTab,
                selectedCategory = state.selectedCategory,
                onCategorySelected = onCategorySelected,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(
                        horizontal = KnotworkTheme.spacing.sp4,
                        vertical = KnotworkTheme.spacing.sp3,
                    ),
            )

            HorizontalDivider(color = KnotworkTheme.extended.divider)

            val visible = state.filteredPresets
            // A selection only counts while its row is still visible under the
            // current tab / category. Switching the filter can hide the
            // previously-picked preset; gating on membership in `visible`
            // (instead of merely `selectedPresetId != null`) keeps the CTA from
            // instantiating a preset the user can no longer see.
            val selectionVisible = selectedPresetId != null && visible.any { it.id == selectedPresetId }
            // List takes the remaining space; weight(1f) is what lets the
            // footer Row underneath stay anchored to the sheet bottom.
            Box(modifier = Modifier.weight(1f)) {
                if (visible.isEmpty()) {
                    Text(
                        text = stringResource(R.string.orchestrator_preset_picker_empty),
                        style = MaterialTheme.typography.bodyMedium,
                        color = KnotworkTheme.extended.onSurfaceMuted,
                        modifier = Modifier
                            .align(Alignment.Center)
                            .padding(KnotworkTheme.spacing.sp6),
                    )
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag(tag = PRESET_PICKER_LIST_TEST_TAG),
                        contentPadding = PaddingValues(vertical = KnotworkTheme.spacing.sp2),
                        verticalArrangement = Arrangement.spacedBy(KnotworkTheme.spacing.sp2),
                    ) {
                        items(items = visible, key = { it.id }) { preset ->
                            PresetPickerRow(
                                preset = preset,
                                selected = preset.id == selectedPresetId,
                                onClick = { selectedPresetId = preset.id },
                            )
                        }
                    }
                }
            }

            // Footer: Cancel + Use this preset, anchored to the bottom of
            // the height-bounded Column.
            HorizontalDivider(color = KnotworkTheme.extended.divider)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(
                        horizontal = KnotworkTheme.spacing.sp4,
                        vertical = KnotworkTheme.spacing.sp3,
                    ),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(onClick = onDismiss) {
                    Text(
                        text = stringResource(R.string.common_cancel),
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
                val ctaEnabled = selectionVisible && !state.isLoading
                TextButton(
                    enabled = ctaEnabled,
                    onClick = { selectedPresetId?.takeIf { id -> visible.any { it.id == id } }?.let(onUsePreset) },
                    modifier = Modifier.testTag(tag = PRESET_PICKER_USE_TEST_TAG),
                ) {
                    Icon(
                        AppIcons.Check,
                        contentDescription = null,
                        modifier = Modifier.padding(end = KnotworkTheme.spacing.sp1),
                    )
                    Text(stringResource(R.string.orchestrator_preset_picker_use))
                }
            }
        }
    }
}

/**
 * One picker row: leading [RadioButton] + name + colored category badge +
 * 2-line description + mono graph-flow preview. The whole row is tappable
 * so the user does not have to aim for the radio dot.
 *
 * When [selected] is `true`, the row gets a primary-tinted background and
 * a 1.5 dp primary border so the user can confirm which preset is about
 * to be instantiated. Row outer padding stays uniform across selected /
 * unselected so the list doesn't jump as the user moves the selection.
 */
@Composable
private fun PresetPickerRow(preset: PipelinePreset, selected: Boolean, onClick: () -> Unit) {
    val bg = if (selected) {
        MaterialTheme.colorScheme.primary.copy(alpha = SELECTED_BG_ALPHA)
    } else {
        Color.Transparent
    }
    val borderColor = if (selected) {
        MaterialTheme.colorScheme.primary
    } else {
        Color.Transparent
    }
    val shape = RoundedCornerShape(size = KnotworkTheme.spacing.sp2)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = KnotworkTheme.spacing.sp4)
            .background(color = bg, shape = shape)
            .border(width = SELECTED_BORDER_WIDTH, color = borderColor, shape = shape)
            .clickable(onClick = onClick)
            .padding(
                horizontal = KnotworkTheme.spacing.sp2,
                vertical = KnotworkTheme.spacing.sp2,
            )
            .testTag(tag = presetRowTestTag(preset.id)),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Radio sits in its own Row aligned with the title line. Wrapping
        // the whole row in `CenterVertically` puts the radio on the centre
        // of the multi-line column, which would float around the
        // description — keeping the radio aligned with the first text line
        // requires anchoring the radio via the title row baseline.
        RadioButton(selected = selected, onClick = onClick)
        Column(
            modifier = Modifier
                .padding(start = KnotworkTheme.spacing.sp1)
                .weight(1f),
            verticalArrangement = Arrangement.spacedBy(KnotworkTheme.spacing.sp1),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(KnotworkTheme.spacing.sp2),
            ) {
                Text(
                    text = preset.name,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                PresetCategoryBadge(category = preset.category)
            }
            if (preset.description.isNotBlank()) {
                Text(
                    text = preset.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = KnotworkTheme.extended.onSurfaceMuted,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Text(
                text = GraphFlowPreview.render(preset.graph),
                style = KnotworkTextStyles.MonoSm,
                color = KnotworkTheme.extended.onSurfaceMuted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** Background alpha for the selected picker row tint. */
private const val SELECTED_BG_ALPHA = 0.10f

/** Border thickness for the selected picker row. */
private val SELECTED_BORDER_WIDTH = 1.5.dp

/** Fraction of the viewport the picker sheet pins to so the footer stays anchored. */
private const val SHEET_HEIGHT_FRACTION = 0.85f

/** Stable per-tab test tag (kept here for backwards-compat with existing tests). */
internal fun presetTabTestTag(tab: PresetPickerTab): String = when (tab) {
    PresetPickerTab.Bundled -> "preset_picker_tab_bundled"
    PresetPickerTab.Mine -> "preset_picker_tab_mine"
}

/** Stable per-row test tag. */
internal fun presetRowTestTag(presetId: String): String = "preset_row_$presetId"

/** Test-tag for the picker sheet root. */
internal const val PRESET_PICKER_SHEET_TEST_TAG = "preset_picker_sheet"

/** Test-tag for the preset list inside the picker. */
internal const val PRESET_PICKER_LIST_TEST_TAG = "preset_picker_list"

/** Test-tag for the "Use this preset" CTA. */
internal const val PRESET_PICKER_USE_TEST_TAG = "preset_picker_use"
