package app.knotwork.design.screens.prompts

/**
 * Preview fixtures for the save-prompt-as-preset dialog.
 *
 * The cap is stated here rather than imported: the catalog does not own the
 * number — the application does — so a fixture that pretended otherwise would
 * be asserting a domain rule from the wrong module.
 */
object SavePromptAsPresetPreview {

    /** The name cap the application supplies today. */
    const val CAP: Int = 60

    /** The ordinary form, with a real prompt to save. */
    fun form(): SavePromptAsPresetDialogUi = SavePromptAsPresetDialogUi(
        title = "Save as prompt preset",
        subtitle = "From LITE_RT",
        promptPreview = "You are a careful assistant. Answer in at most three sentences, " +
            "and say plainly when you do not know.",
        nameLabel = "Name",
        descriptionLabel = "Description",
        tagsLabel = "Tags",
        tagsHint = "Comma-separated.",
        blankPromptError = "There is no prompt to save — write one first.",
        nameTooLongError = "Keep the name to $CAP characters or fewer.",
        saveLabel = "Save",
        cancelLabel = "Cancel",
        maxNameLength = CAP,
    )

    /** The same form opened on an empty prompt field: nothing to save. */
    fun blankPrompt(): SavePromptAsPresetDialogUi = form().copy(promptPreview = "")
}
