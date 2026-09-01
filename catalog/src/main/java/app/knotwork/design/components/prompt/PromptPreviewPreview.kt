package app.knotwork.design.components.prompt

/**
 * Preview fixtures for the prompt-preview sheet.
 *
 * The fixture deliberately mixes all three segment kinds in one prompt. A
 * preview showing only resolved values would photograph the happy path, and the
 * whole reason this surface exists is to make an **unresolved** placeholder
 * obvious — which is only legible next to a resolved one.
 */
object PromptPreviewPreview {

    /** Copy as the application resolves it. */
    fun ui(): PromptPreviewSheetUi = PromptPreviewSheetUi(
        title = "Prompt preview",
        variableNotFoundTooltip = "Variable not found",
    )

    /** A prompt with one resolved placeholder and one typo. */
    fun mixed(): List<PromptPreviewSegmentUi> = listOf(
        PromptPreviewSegmentUi.Literal("Today is "),
        PromptPreviewSegmentUi.Resolved("31 August 2026"),
        PromptPreviewSegmentUi.Literal(" and the device speaks "),
        PromptPreviewSegmentUi.Resolved("en-US"),
        PromptPreviewSegmentUi.Literal(".\n\nAnswer briefly. Use "),
        PromptPreviewSegmentUi.Unknown("TOOLZ"),
        PromptPreviewSegmentUi.Literal(" when a lookup would help."),
    )

    /** Every placeholder resolved — the shape a correct template produces. */
    fun resolved(): List<PromptPreviewSegmentUi> = listOf(
        PromptPreviewSegmentUi.Literal("Today is "),
        PromptPreviewSegmentUi.Resolved("31 August 2026"),
        PromptPreviewSegmentUi.Literal(". Answer briefly."),
    )
}
