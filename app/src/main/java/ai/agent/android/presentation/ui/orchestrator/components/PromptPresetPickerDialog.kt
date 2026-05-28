package ai.agent.android.presentation.ui.orchestrator.components

import ai.agent.android.R
import ai.agent.android.domain.models.NodeType
import ai.agent.android.domain.models.PromptPreset
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.knotwork.design.components.buttons.KnotworkTextButton
import app.knotwork.design.components.chips.KnotworkChipSize
import app.knotwork.design.components.chips.KnotworkFilterChip
import app.knotwork.design.components.chips.KnotworkSuggestionChip
import app.knotwork.design.theme.KnotworkTheme
import kotlinx.coroutines.delay

/**
 * Two-tabbed picker dialog for Phase 24 prompt presets — replaces the legacy
 * `PromptLibraryDialog` for the production pipeline editor.
 *
 * The dialog is intentionally stateless: the caller hands in the bundled and
 * user catalogues already filtered by the active node type (see
 * `PromptPresetRepository.getPresetsForType`) and the dialog handles the
 * presentation concerns — tab toggle, tag-chip multi-filter, debounced search,
 * per-row Preview / Apply.
 *
 * @param nodeType the [NodeType] the active node lives under; rendered as the
 *  dialog subtitle so the user has a clear sense of scope.
 * @param bundled bundled presets (read-only, ship inside the APK) already
 *  filtered to [nodeType].
 * @param mine user-saved presets already filtered to [nodeType].
 * @param onApply invoked with the picked preset's `systemPrompt` when the user
 *  taps "Apply" on a row. The dialog dismisses itself afterwards.
 * @param onPreview invoked with a preset's `systemPrompt` when the user taps
 *  "Preview"; the screen renders its own `PromptPreviewBottomSheet`.
 * @param onDismiss invoked when the user dismisses the dialog without applying.
 */
@Composable
@Suppress("LongMethod") // The dialog is the single picker seam — splitting hides intent.
fun PromptPresetPickerDialog(
    nodeType: NodeType,
    bundled: List<PromptPreset>,
    mine: List<PromptPreset>,
    onApply: (systemPrompt: String) -> Unit,
    onPreview: (systemPrompt: String) -> Unit,
    onDismiss: () -> Unit,
) {
    var selectedTab by remember { mutableStateOf(PromptPresetTab.BUNDLED) }
    var searchQuery by remember { mutableStateOf("") }
    var debouncedQuery by remember { mutableStateOf("") }
    var selectedTags by remember { mutableStateOf<Set<String>>(emptySet()) }

    LaunchedEffect(searchQuery) {
        // 200ms debounce per spec — the typical user types short search
        // terms ("router", "json") faster than the per-keystroke filter
        // would otherwise re-render the LazyColumn.
        delay(SEARCH_DEBOUNCE_MS)
        debouncedQuery = searchQuery
    }
    // Reset chip filter when switching tabs — the union of tags is
    // tab-specific (a tag that exists in Bundled may not be present in
    // Mine), so carrying over the selection produces a confusing empty
    // list with no obvious "why".
    LaunchedEffect(selectedTab) { selectedTags = emptySet() }

    val sourceList = when (selectedTab) {
        PromptPresetTab.BUNDLED -> bundled
        PromptPresetTab.MINE -> mine
    }
    val availableTags = remember(sourceList) {
        sourceList.flatMap { it.tags }.distinct().sorted()
    }
    val filtered = remember(sourceList, debouncedQuery, selectedTags) {
        filterPresets(sourceList, debouncedQuery, selectedTags)
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.prompt_preset_picker_title),
                        style = MaterialTheme.typography.titleLarge,
                    )
                    Text(
                        text = stringResource(
                            R.string.prompt_preset_picker_subtitle,
                            nodeType.name,
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                IconButton(onClick = onDismiss) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = stringResource(R.string.common_close),
                    )
                }
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = MIN_BODY_HEIGHT_DP.dp, max = MAX_BODY_HEIGHT_DP.dp),
                verticalArrangement = Arrangement.spacedBy(KnotworkTheme.spacing.sp2),
            ) {
                PickerTabs(
                    selected = selectedTab,
                    bundledCount = bundled.size,
                    mineCount = mine.size,
                    onTabSelected = { selectedTab = it },
                )
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text(stringResource(R.string.prompt_preset_picker_search_hint)) },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Outlined.Search,
                            contentDescription = null,
                        )
                    },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                if (availableTags.isNotEmpty()) {
                    TagFilterRow(
                        tags = availableTags,
                        selectedTags = selectedTags,
                        onTagToggle = { tag ->
                            selectedTags = if (tag in selectedTags) {
                                selectedTags - tag
                            } else {
                                selectedTags + tag
                            }
                        },
                        onClearAll = { selectedTags = emptySet() },
                    )
                }
                HorizontalDivider()
                PresetList(
                    presets = filtered,
                    emptyMessage = when (selectedTab) {
                        PromptPresetTab.BUNDLED ->
                            stringResource(R.string.prompt_preset_picker_empty_bundled)
                        PromptPresetTab.MINE ->
                            stringResource(R.string.prompt_preset_picker_empty_mine)
                    },
                    onApply = { picked ->
                        onApply(picked.systemPrompt)
                        onDismiss()
                    },
                    onPreview = { preset -> onPreview(preset.systemPrompt) },
                )
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.common_close))
            }
        },
    )
}

/** Tab identifiers backing the picker's segmented header. */
internal enum class PromptPresetTab { BUNDLED, MINE }

@Composable
private fun PickerTabs(
    selected: PromptPresetTab,
    bundledCount: Int,
    mineCount: Int,
    onTabSelected: (PromptPresetTab) -> Unit,
) {
    PrimaryTabRow(selectedTabIndex = if (selected == PromptPresetTab.BUNDLED) 0 else 1) {
        Tab(
            selected = selected == PromptPresetTab.BUNDLED,
            onClick = { onTabSelected(PromptPresetTab.BUNDLED) },
            text = {
                Text(
                    text = stringResource(R.string.prompt_preset_picker_tab_bundled) +
                        " ($bundledCount)",
                )
            },
        )
        Tab(
            selected = selected == PromptPresetTab.MINE,
            onClick = { onTabSelected(PromptPresetTab.MINE) },
            text = {
                Text(
                    text = stringResource(R.string.prompt_preset_picker_tab_mine) +
                        " ($mineCount)",
                )
            },
        )
    }
}

@Composable
private fun TagFilterRow(
    tags: List<String>,
    selectedTags: Set<String>,
    onTagToggle: (String) -> Unit,
    onClearAll: () -> Unit,
) {
    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(KnotworkTheme.spacing.sp1),
        verticalArrangement = Arrangement.spacedBy(KnotworkTheme.spacing.sp1),
    ) {
        tags.forEach { tag ->
            KnotworkFilterChip(
                label = tag,
                selected = tag in selectedTags,
                onClick = { onTagToggle(tag) },
            )
        }
        if (selectedTags.isNotEmpty()) {
            KnotworkTextButton(
                text = stringResource(R.string.prompt_preset_picker_action_clear_tags),
                onClick = onClearAll,
            )
        }
    }
}

@Composable
private fun PresetList(
    presets: List<PromptPreset>,
    emptyMessage: String,
    onApply: (PromptPreset) -> Unit,
    onPreview: (PromptPreset) -> Unit,
) {
    if (presets.isEmpty()) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(KnotworkTheme.spacing.sp3),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = emptyMessage,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        return
    }
    LazyColumn(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(vertical = KnotworkTheme.spacing.sp1),
    ) {
        items(presets, key = { it.id }) { preset ->
            PresetRow(
                preset = preset,
                onApply = { onApply(preset) },
                onPreview = { onPreview(preset) },
            )
            HorizontalDivider()
        }
    }
}

@Composable
private fun PresetRow(preset: PromptPreset, onApply: () -> Unit, onPreview: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = KnotworkTheme.spacing.sp2),
        verticalArrangement = Arrangement.spacedBy(KnotworkTheme.spacing.sp1),
    ) {
        Text(
            text = preset.name,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.SemiBold,
        )
        if (preset.description.isNotBlank()) {
            Text(
                text = preset.description,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = MAX_DESCRIPTION_LINES,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (preset.tags.isNotEmpty()) {
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(KnotworkTheme.spacing.sp1),
                verticalArrangement = Arrangement.spacedBy(KnotworkTheme.spacing.sp1),
            ) {
                preset.tags.forEach { tag ->
                    // Display-only chip; the suggestion variant ships with the
                    // outline-on-surface palette used elsewhere on the editor for
                    // metadata pills. `onClick` is intentionally a no-op — these
                    // are read-only labels, not filter toggles.
                    KnotworkSuggestionChip(
                        label = tag,
                        onClick = {},
                        size = KnotworkChipSize.Sm,
                    )
                }
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(
                space = KnotworkTheme.spacing.sp2,
                alignment = Alignment.End,
            ),
        ) {
            KnotworkTextButton(
                text = stringResource(R.string.prompt_preset_picker_action_preview),
                onClick = onPreview,
            )
            KnotworkTextButton(
                text = stringResource(R.string.prompt_preset_picker_action_apply),
                onClick = onApply,
            )
        }
        Spacer(modifier = Modifier.size(KnotworkTheme.spacing.sp1))
    }
}

/**
 * Filters [presets] by a case-insensitive substring [query] match on
 * [PromptPreset.name] AND by a tag-intersection rule: a preset matches when
 * every selected tag is present on the preset (AND semantics, mirrors how
 * filter chips usually compose). Pure function so it can be unit-tested
 * without spinning up Compose.
 */
internal fun filterPresets(presets: List<PromptPreset>, query: String, selectedTags: Set<String>): List<PromptPreset> {
    val trimmed = query.trim()
    return presets.filter { preset ->
        val matchesQuery = trimmed.isEmpty() ||
            preset.name.contains(trimmed, ignoreCase = true)
        val matchesTags = selectedTags.isEmpty() ||
            selectedTags.all { tag -> preset.tags.any { it.equals(tag, ignoreCase = true) } }
        matchesQuery && matchesTags
    }
}

/** Debounce window for the search field — matches the spec (200 ms). */
private const val SEARCH_DEBOUNCE_MS: Long = 200L

/** Minimum body height so the dialog doesn't collapse when both lists are empty. */
private const val MIN_BODY_HEIGHT_DP: Int = 320

/** Cap so the dialog stays under the IME / safe-area on small phones. */
private const val MAX_BODY_HEIGHT_DP: Int = 520

/** Description truncation cap — two lines reads as a teaser without bloating the card. */
private const val MAX_DESCRIPTION_LINES: Int = 2
