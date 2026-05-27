package ai.agent.android.presentation.ui.orchestrator.presets

import ai.agent.android.R
import ai.agent.android.domain.models.PipelinePreset
import ai.agent.android.domain.models.PresetCategory
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Tab
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.knotwork.design.theme.KnotworkTheme

/**
 * Modal bottom sheet exposing the bundled / user pipeline-preset catalogue
 * as a "tap to instantiate" surface.
 *
 * Layout:
 *
 *  - `PrimaryTabRow` (Bundled / Mine).
 *  - Horizontal chip row listing the [PresetCategory]s present under the
 *    active tab (omits chips that would produce empty results).
 *  - `LazyColumn` of preset cards — each renders name, description, tag
 *    chips and a 1-line graph preview built by [GraphFlowPreview].
 *  - Footer-anchored "Use this preset" CTA, enabled when a row is selected
 *    and disabled while a load is in-flight.
 *
 * Selection state is held inside the sheet — the host
 * (`PipelineLibraryScreen`) only sees the chosen preset id on
 * confirmation. Tab and category-chip selection are owned by
 * [PipelinePresetsViewModel] so they survive configuration changes.
 *
 * @param state Current picker state from `PipelinePresetsViewModel`.
 * @param onTabSelected Forwards tab clicks to the VM.
 * @param onCategorySelected Forwards category-chip clicks to the VM.
 * @param onUsePreset Invoked with the selected preset id when the user
 *   taps the CTA.
 * @param onDismiss Invoked when the user dismisses the sheet without
 *   selecting a preset (drag / scrim tap).
 */
@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
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
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(
                    start = KnotworkTheme.spacing.sp6,
                    end = KnotworkTheme.spacing.sp6,
                    bottom = KnotworkTheme.spacing.sp4,
                ),
            verticalArrangement = Arrangement.spacedBy(KnotworkTheme.spacing.sp3),
        ) {
            Text(
                text = stringResource(R.string.orchestrator_preset_picker_title),
                style = MaterialTheme.typography.titleMedium,
            )

            PrimaryTabRow(selectedTabIndex = state.activeTab.ordinal) {
                PresetPickerTab.entries.forEach { tab ->
                    Tab(
                        selected = state.activeTab == tab,
                        onClick = { onTabSelected(tab) },
                        text = { Text(presetTabLabel(tab)) },
                        modifier = Modifier.testTag(tag = presetTabTestTag(tab)),
                    )
                }
            }

            if (state.visibleCategories.isNotEmpty()) {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(KnotworkTheme.spacing.sp2),
                    verticalArrangement = Arrangement.spacedBy(KnotworkTheme.spacing.sp2),
                ) {
                    FilterChip(
                        selected = state.selectedCategory == null,
                        onClick = { onCategorySelected(null) },
                        label = { Text(stringResource(R.string.orchestrator_preset_picker_chip_all)) },
                    )
                    state.visibleCategories.forEach { category ->
                        FilterChip(
                            selected = state.selectedCategory == category,
                            onClick = { onCategorySelected(category) },
                            label = { Text(presetCategoryLabel(category)) },
                        )
                    }
                }
            }

            val visible = state.filteredPresets
            if (visible.isEmpty()) {
                Text(
                    text = stringResource(R.string.orchestrator_preset_picker_empty),
                    style = MaterialTheme.typography.bodyMedium,
                    color = KnotworkTheme.extended.onSurfaceMuted,
                )
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag(tag = PRESET_PICKER_LIST_TEST_TAG),
                    verticalArrangement = Arrangement.spacedBy(KnotworkTheme.spacing.sp2),
                    contentPadding = PaddingValues(vertical = KnotworkTheme.spacing.sp1),
                ) {
                    items(items = visible, key = { it.id }) { preset ->
                        PresetCard(
                            preset = preset,
                            selected = preset.id == selectedPresetId,
                            onClick = { selectedPresetId = preset.id },
                        )
                    }
                }
            }

            val ctaEnabled = selectedPresetId != null && !state.isLoading
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
            ) {
                TextButton(onClick = onDismiss) {
                    Text(stringResource(R.string.common_cancel))
                }
                TextButton(
                    enabled = ctaEnabled,
                    onClick = { selectedPresetId?.let(onUsePreset) },
                    modifier = Modifier.testTag(tag = PRESET_PICKER_USE_TEST_TAG),
                ) {
                    Text(stringResource(R.string.orchestrator_preset_picker_use))
                }
            }
        }
    }
}

/**
 * One preset row inside the picker. Renders name, description, tag chips
 * and the [GraphFlowPreview] one-liner. Tappable surface that toggles the
 * sheet-local selection state.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PresetCard(preset: PipelinePreset, selected: Boolean, onClick: () -> Unit) {
    val borderColor = if (selected) {
        MaterialTheme.colorScheme.primary
    } else {
        KnotworkTheme.extended.onSurfaceMuted.copy(alpha = SELECTED_BORDER_ALPHA)
    }
    val bg = if (selected) {
        MaterialTheme.colorScheme.primary.copy(alpha = SELECTED_BG_ALPHA)
    } else {
        Color.Transparent
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(size = KnotworkTheme.spacing.sp2))
            .background(bg)
            .border(width = 1.dp, color = borderColor, shape = RoundedCornerShape(size = KnotworkTheme.spacing.sp2))
            .clickable(onClick = onClick)
            .padding(KnotworkTheme.spacing.sp3)
            .testTag(tag = presetRowTestTag(preset.id)),
        verticalArrangement = Arrangement.spacedBy(KnotworkTheme.spacing.sp2),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(KnotworkTheme.spacing.sp2),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(
                text = preset.name,
                style = MaterialTheme.typography.titleSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .padding(end = KnotworkTheme.spacing.sp2)
                    .testTag(tag = "preset_name_${preset.id}")
                    .weight(weight = 1f),
            )
            AssistChip(
                onClick = onClick,
                label = { Text(presetCategoryLabel(preset.category)) },
                colors = AssistChipDefaults.assistChipColors(),
            )
        }
        if (preset.description.isNotBlank()) {
            Text(
                text = preset.description,
                style = MaterialTheme.typography.bodySmall,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Text(
            text = GraphFlowPreview.render(preset.graph),
            style = MaterialTheme.typography.labelSmall,
            color = KnotworkTheme.extended.onSurfaceMuted,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        if (preset.tags.isNotEmpty()) {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(KnotworkTheme.spacing.sp1),
                verticalArrangement = Arrangement.spacedBy(KnotworkTheme.spacing.sp1),
            ) {
                preset.tags.forEach { tag ->
                    AssistChip(onClick = onClick, label = { Text(tag) })
                }
            }
        }
    }
}

/** Resolves the localised label for a picker tab. */
@Composable
private fun presetTabLabel(tab: PresetPickerTab): String = when (tab) {
    PresetPickerTab.Bundled -> stringResource(R.string.orchestrator_preset_picker_tab_bundled)
    PresetPickerTab.Mine -> stringResource(R.string.orchestrator_preset_picker_tab_mine)
}

/** Stable per-tab test tag. */
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

/** Background tint alpha for the selected preset card. */
private const val SELECTED_BG_ALPHA = 0.08f

/** Border tint alpha for the unselected preset card. */
private const val SELECTED_BORDER_ALPHA = 0.32f
