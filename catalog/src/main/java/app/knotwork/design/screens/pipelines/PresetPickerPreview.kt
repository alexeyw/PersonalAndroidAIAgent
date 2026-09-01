package app.knotwork.design.screens.pipelines

/**
 * Preview fixtures for the preset picker.
 *
 * The populated fixture carries a deliberately long title next to a badge. That
 * is the case `PresetCategoryBadgeLayoutTest` guards on a real device: without
 * `weight(fill = false)` on the title, the badge is measured at zero width and
 * renders as a one-character-per-line sliver.
 */
object PresetPickerPreview {

    /** The Bundled tab with a selection made. */
    fun populated(): PresetPickerViewState = PresetPickerViewState(
        title = "Start from a preset",
        tabs = listOf(
            PresetTabUi(id = "bundled", label = "Bundled", count = 3, selected = true),
            PresetTabUi(id = "mine", label = "Mine", count = 1, selected = false),
        ),
        chips = listOf(
            PresetChipUi(id = null, label = "All", count = 3, selected = true),
            PresetChipUi(id = "LOCAL", label = "Local", count = 2, selected = false),
            PresetChipUi(id = "CLOUD", label = "Cloud", count = 1, selected = false),
        ),
        rows = listOf(
            PresetPickerRowUi(
                id = "p1",
                name = "Local question and answer",
                description = "One local model, no tools, nothing leaves the device.",
                flowPreview = "INPUT → LITE_RT → OUTPUT",
                categoryLabel = "Local",
                categoryTone = PresetCategoryToneUi.Local,
            ),
            PresetPickerRowUi(
                id = "p2",
                name = "Research assistant that writes its findings to a file",
                description = "Searches, distils, then saves the result to the workspace.",
                flowPreview = "INPUT → LITE_RT → TOOL → LITE_RT → TOOL → OUTPUT",
                categoryLabel = "Research",
                categoryTone = PresetCategoryToneUi.Research,
            ),
            PresetPickerRowUi(
                id = "p3",
                name = "Cloud assist",
                description = "",
                flowPreview = "INPUT → CLOUD → OUTPUT",
                categoryLabel = "Cloud",
                categoryTone = PresetCategoryToneUi.Cloud,
            ),
        ),
        selectedRowId = "p2",
        emptyMessage = "No presets in this category.",
        isLoading = false,
        cancelLabel = "Cancel",
        useLabel = "Use this preset",
        closeContentDescription = "Close",
    )

    /** The Mine tab before anything has been saved. */
    fun empty(): PresetPickerViewState = populated().copy(
        tabs = listOf(
            PresetTabUi(id = "bundled", label = "Bundled", count = 3, selected = false),
            PresetTabUi(id = "mine", label = "Mine", count = 0, selected = true),
        ),
        rows = emptyList(),
        selectedRowId = null,
    )

    /**
     * A selection that the current filter has hidden.
     *
     * The confirm CTA must be disabled here. The state is reachable in one tap —
     * pick a preset, then switch category — and without the visibility check it
     * would instantiate a preset the user can no longer see.
     */
    fun selectionFilteredAway(): PresetPickerViewState = populated().copy(
        rows = populated().rows.filter { it.id != "p2" },
    )
}
