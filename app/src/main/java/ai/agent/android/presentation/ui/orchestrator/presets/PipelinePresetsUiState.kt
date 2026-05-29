package ai.agent.android.presentation.ui.orchestrator.presets

import ai.agent.android.domain.models.PipelinePreset
import ai.agent.android.domain.models.PresetCategory
import ai.agent.android.presentation.ui.common.UiText

/**
 * Tabs surfaced by the `PresetPickerSheet` and the
 * `PipelinePresetsManagerScreen`. Two-bucket split mirrors the bundled /
 * user-owned distinction baked into [PipelinePreset.isBundled].
 */
enum class PresetPickerTab {
    /** Read-only catalogue shipped inside the APK. */
    Bundled,

    /** User-saved presets persisted in Room. */
    Mine,
}

/**
 * UI state held by `PipelinePresetsViewModel`. Drives both the bottom-sheet
 * picker (`PresetPickerSheet`) and the full-screen manager
 * (`PipelinePresetsManagerScreen`); the two surfaces project different
 * subsets of the same state.
 *
 * @property bundledPresets Read-only presets from `assets/presets/pipelines`.
 * @property userPresets User-saved presets persisted in Room.
 * @property activeTab Currently-selected tab in the picker / manager.
 * @property selectedCategory Category-chip filter; `null` = "All".
 * @property pendingPipelineIdFromPreset Non-null id of the pipeline that
 *   was just materialised from a preset — the picker host observes this
 *   to navigate the editor.
 * @property errorMessage One-shot error text for the host's Snackbar.
 * @property feedbackMessage One-shot success-flavoured message.
 * @property isLoading `true` while a save / delete / load operation is
 *   in-flight; the picker disables its CTA to prevent double-tap.
 */
data class PipelinePresetsUiState(
    val bundledPresets: List<PipelinePreset> = emptyList(),
    val userPresets: List<PipelinePreset> = emptyList(),
    val activeTab: PresetPickerTab = PresetPickerTab.Bundled,
    val selectedCategory: PresetCategory? = null,
    val pendingPipelineIdFromPreset: String? = null,
    val errorMessage: UiText? = null,
    val feedbackMessage: UiText? = null,
    val isLoading: Boolean = false,
) {

    /**
     * Presets visible under [activeTab] *before* the category filter is
     * applied. Computed lazily so re-renders that only change the chip
     * selection skip the list copy.
     */
    val presetsForActiveTab: List<PipelinePreset>
        get() = when (activeTab) {
            PresetPickerTab.Bundled -> bundledPresets
            PresetPickerTab.Mine -> userPresets
        }

    /**
     * Presets visible after applying both [activeTab] and the
     * [selectedCategory] chip. Empty list means "no presets match the
     * current filter" — the surface should render an inline empty state.
     */
    val filteredPresets: List<PipelinePreset>
        get() = presetsForActiveTab.let { list ->
            val category = selectedCategory ?: return list
            list.filter { it.category == category }
        }

    /**
     * Distinct categories present in [presetsForActiveTab], in declaration
     * order of [PresetCategory]. Used by the picker to render only the
     * chips that would actually produce a non-empty result.
     */
    val visibleCategories: List<PresetCategory>
        get() {
            val present = presetsForActiveTab.map { it.category }.toSet()
            return PresetCategory.entries.filter { it in present }
        }
}
