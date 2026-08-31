package app.knotwork.design.screens.pipelines

/**
 * Fixtures for the preset manager's states.
 *
 * In `main` rather than `test`, following [PipelineLibraryPreview], so `@Preview`
 * functions can use them as well as the snapshot suite.
 */
internal object PresetManagerPreview {

    /** The bundled tab with a full catalogue. */
    fun bundled(): PresetManagerViewState = PresetManagerViewState(
        title = "Pipeline presets",
        subtitle = "4 bundled · 2 saved",
        backContentDescription = "Back",
        tabs = listOf(
            PresetTabUi(id = "bundled", label = "Bundled", count = 4, selected = true),
            PresetTabUi(id = "mine", label = "Mine", count = 2, selected = false),
        ),
        chips = listOf(
            PresetChipUi(id = null, label = "All", count = 4, selected = true),
            PresetChipUi(id = "LOCAL", label = "Local", count = 2, selected = false),
            PresetChipUi(id = "CLOUD", label = "Cloud", count = 1, selected = false),
            PresetChipUi(id = "TOOL", label = "Tool", count = 1, selected = false),
        ),
        rows = listOf(
            row("p1", "Showcase", PresetCategoryToneUi.Hybrid, "Hybrid", bundled = true),
            row("p2", "On-device answer", PresetCategoryToneUi.Local, "Local", bundled = true),
            row("p3", "Cloud research", PresetCategoryToneUi.Cloud, "Cloud", bundled = true),
            row("p4", "Search and summarise", PresetCategoryToneUi.Tool, "Tool", bundled = true),
        ),
        emptyText = "No bundled presets.",
        actionLabels = ACTIONS,
    )

    /** The saved tab, where rows carry rename and delete as well. */
    fun mine(): PresetManagerViewState = bundled().copy(
        tabs = listOf(
            PresetTabUi(id = "bundled", label = "Bundled", count = 4, selected = false),
            PresetTabUi(id = "mine", label = "Mine", count = 2, selected = true),
        ),
        chips = listOf(
            PresetChipUi(id = null, label = "All", count = 2, selected = true),
            PresetChipUi(id = "RESEARCH", label = "Research", count = 2, selected = false),
        ),
        rows = listOf(
            row("m1", "Morning brief", PresetCategoryToneUi.Research, "Research", bundled = false),
            row(
                id = "m2",
                name = "Virtual Companion (Mood Router) with an even longer trailing name",
                tone = PresetCategoryToneUi.Other,
                label = "Other",
                bundled = false,
            ),
        ),
    )

    /** The saved tab with nothing in it. */
    fun empty(): PresetManagerViewState = mine().copy(
        rows = emptyList(),
        emptyText = "Nothing saved yet. Save a pipeline as a preset to see it here.",
        chips = listOf(PresetChipUi(id = null, label = "All", count = 0, selected = true)),
    )

    /** The rename dialog over the saved tab. */
    fun renaming(): PresetManagerViewState = mine().copy(
        rename = PresetRenameDialogUi(
            initialName = "Morning brief",
            title = "Rename preset",
            label = "Name",
            confirmLabel = "Save",
            cancelLabel = "Cancel",
        ),
    )

    /** The delete confirmation over the saved tab. */
    fun deleting(): PresetManagerViewState = mine().copy(
        delete = PresetDeleteDialogUi(
            title = "Delete preset?",
            body = "\"Morning brief\" will be removed. This cannot be undone.",
            confirmLabel = "Delete",
            cancelLabel = "Cancel",
        ),
    )

    private fun row(
        id: String,
        name: String,
        tone: PresetCategoryToneUi,
        label: String,
        bundled: Boolean,
    ): PresetRowUi = PresetRowUi(
        id = id,
        name = name,
        description = "Routes the message, answers on-device, and summarises what it found.",
        flowPreview = "INPUT → INTENT_ROUTER → LITE_RT → OUTPUT",
        categoryLabel = label,
        categoryTone = tone,
        canRename = !bundled,
        canDelete = !bundled,
    )

    private val ACTIONS = PresetRowActionLabels(rename = "Rename", export = "Export JSON", delete = "Delete")
}
