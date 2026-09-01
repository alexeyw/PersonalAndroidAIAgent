package app.knotwork.design.screens.prompts

import app.knotwork.design.components.pipelineeditor.NodeType

/**
 * Preview fixtures for the prompt-preset picker.
 *
 * The populated fixture carries a current row, a multi-tag row and a row with
 * no tags at all, because those are the three shapes the row layout has to
 * survive — a `● CURRENT` pill competing with a token count, a tag list long
 * enough to wrap, and a row with neither.
 */
object PromptPresetPickerPreview {

    /** The Bundled tab with presets to choose from. */
    fun populated(): PromptPresetPickerViewState = PromptPresetPickerViewState(
        nodeType = NodeType.LITE_RT,
        selectedTab = PromptPresetPickerTab.BUNDLED,
        bundledCount = 3,
        mineCount = 2,
        tagChips = listOf(
            PromptPresetTagChip(tag = null, label = "All", count = 3),
            PromptPresetTagChip(tag = "concise", label = "concise", count = 2),
            PromptPresetTagChip(tag = "reasoning", label = "reasoning", count = 1),
        ),
        selectedTagFilter = null,
        rows = listOf(
            PromptPresetPickerRow(
                id = "p1",
                name = "Concise answers",
                description = "Three sentences at most, and says plainly when it does not know.",
                tags = listOf("concise", "safe"),
                tokens = 84,
                isCurrent = true,
            ),
            PromptPresetPickerRow(
                id = "p2",
                name = "Step-by-step reasoning",
                description = "Works the problem out loud before answering.",
                tags = listOf("reasoning"),
                tokens = 142,
            ),
            PromptPresetPickerRow(
                id = "p3",
                name = "Plain rewrite",
                description = "Rewrites the input in plain language, changing nothing else.",
                tags = emptyList(),
                tokens = 61,
            ),
        ),
        selectedRowId = "p2",
    )

    /** The Mine tab with nothing saved yet. */
    fun empty(): PromptPresetPickerViewState = PromptPresetPickerViewState(
        nodeType = NodeType.LITE_RT,
        selectedTab = PromptPresetPickerTab.MINE,
        bundledCount = 3,
        mineCount = 0,
        tagChips = listOf(PromptPresetTagChip(tag = null, label = "All", count = 0)),
        rows = emptyList(),
        emptyMessage = "You have not saved a prompt for this node type yet.",
    )
}
