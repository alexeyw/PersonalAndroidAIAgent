package app.knotwork.design.screens.pipelines

/**
 * Preview fixtures for the preset dialogs.
 *
 * Categories mirror the six the application actually offers, with their real
 * labels: a fixture that invented three plausible buckets would photograph a
 * row that never wraps, and wrapping is the behaviour `FlowRow` is here for.
 */
object PresetDialogsPreview {

    /** The save-as-preset form as it opens: name pre-filled, `Other` selected. */
    fun saveAsPreset(): SaveAsPresetDialogUi = SaveAsPresetDialogUi(
        initialName = "Research assistant",
        categories = listOf(
            PresetCategoryOptionUi(id = "LOCAL", label = "Local"),
            PresetCategoryOptionUi(id = "CLOUD", label = "Cloud"),
            PresetCategoryOptionUi(id = "HYBRID", label = "Hybrid"),
            PresetCategoryOptionUi(id = "TOOL", label = "Tools"),
            PresetCategoryOptionUi(id = "RESEARCH", label = "Research"),
            PresetCategoryOptionUi(id = "OTHER", label = "Other"),
        ),
        initialCategoryId = "OTHER",
        title = "Save as preset",
        nameLabel = "Name",
        descriptionLabel = "Description",
        categoryLabel = "Category",
        tagsLabel = "Tags (comma-separated)",
        saveLabel = "Save",
        cancelLabel = "Cancel",
    )
}
