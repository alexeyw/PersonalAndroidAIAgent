package app.knotwork.design.screens.skills

import app.knotwork.design.components.chips.Risk

/**
 * Canonical sample states for the Skill Library surfaces, shared by Android
 * Studio `@Preview`s and the Roborazzi snapshot baselines so the previews and
 * the recorded images never drift.
 */
object SkillLibraryPreview {

    private val BUNDLED_ROWS = listOf(
        SkillRowUi(
            id = "summarizer",
            name = "Summarizer",
            description = "Condense any text into a tight, faithful summary.",
            toolIndicator = SkillToolIndicator.None,
            isBundled = true,
        ),
        SkillRowUi(
            id = "translator",
            name = "Translator",
            description = "Translate into the device language, preserving tone.",
            toolIndicator = SkillToolIndicator.None,
            isBundled = true,
        ),
        SkillRowUi(
            id = "report_writer",
            name = "Report Writer",
            description = "Draft a structured report and save it to a file.",
            toolIndicator = SkillToolIndicator.Subset,
            toolCount = 1,
            isBundled = true,
        ),
    )

    private val MINE_ROWS = listOf(
        SkillRowUi(
            id = "standup",
            name = "Standup digest",
            description = "Summarize my day from notes into three bullets.",
            toolIndicator = SkillToolIndicator.Subset,
            toolCount = 2,
            isBundled = false,
        ),
        SkillRowUi(
            id = "curt",
            name = "Curt tone",
            description = "Rewrite replies to be short, direct and free of filler.",
            toolIndicator = SkillToolIndicator.None,
            isBundled = false,
        ),
        SkillRowUi(
            id = "researcher",
            name = "Open researcher",
            description = "Search, read and synthesize sources into a cited answer.",
            toolIndicator = SkillToolIndicator.All,
            isBundled = false,
        ),
    )

    private val SAMPLE_TOOLS = listOf(
        SkillToolOptionUi("search_tool", Risk.Readonly, "Web & knowledge-base lookup", selected = true),
        SkillToolOptionUi("http_request", Risk.Readonly, "Fetch an allowed URL", selected = true),
        SkillToolOptionUi("read_file", Risk.Readonly, "Read from the workspace", selected = true),
        SkillToolOptionUi("write_file", Risk.Destructive, "Write / overwrite a file", selected = false),
        SkillToolOptionUi("schedule_task", Risk.Sensitive, "Run a task later", selected = false),
    )

    private fun contextFlags(allOn: Boolean): List<SkillContextFlagUi> {
        val on = if (allOn) {
            mapOf("chat" to true, "task" to true, "input" to true, "memory" to true, "tools" to true)
        } else {
            mapOf("chat" to true, "task" to true, "input" to false, "memory" to true, "tools" to false)
        }
        return listOf(
            SkillContextFlagUi("chat", "Chat history", "Prior turns in this conversation", on.getValue("chat")),
            SkillContextFlagUi("task", "Original task", "The user's initial request", on.getValue("task")),
            SkillContextFlagUi("input", "Node input", "Output handed in from upstream", on.getValue("input")),
            SkillContextFlagUi("memory", "Long-term memory", "Relevant vector-memory chunks", on.getValue("memory")),
            SkillContextFlagUi("tools", "Tool results", "Outputs from earlier tool calls", on.getValue("tools")),
        )
    }

    private val SAMPLE_VARIABLES = listOf("\$DATE", "\$LANG", "\$TOOLS", "\$MEMORY_SUMMARY", "\$MODEL")

    private const val SUBTITLE = "3 bundled · 3 mine"

    // ── List states ────────────────────────────────────────────────────────

    fun bundledPopulated(): SkillLibraryViewState = SkillLibraryViewState(
        visualState = SkillLibraryVisualState.Default,
        tab = SkillLibraryTab.Bundled,
        bundledCount = BUNDLED_ROWS.size,
        mineCount = MINE_ROWS.size,
        rows = BUNDLED_ROWS,
        subtitle = SUBTITLE,
    )

    fun bundledRowMenu(): SkillLibraryViewState = bundledPopulated().copy(openMenuRowId = "report_writer")

    fun minePopulated(): SkillLibraryViewState = SkillLibraryViewState(
        visualState = SkillLibraryVisualState.Default,
        tab = SkillLibraryTab.Mine,
        bundledCount = BUNDLED_ROWS.size,
        mineCount = MINE_ROWS.size,
        rows = MINE_ROWS,
        subtitle = SUBTITLE,
    )

    fun mineRowMenu(): SkillLibraryViewState = minePopulated().copy(openMenuRowId = "standup")

    fun mineEmpty(): SkillLibraryViewState = SkillLibraryViewState(
        visualState = SkillLibraryVisualState.EmptyMine,
        tab = SkillLibraryTab.Mine,
        bundledCount = BUNDLED_ROWS.size,
        mineCount = 0,
        rows = emptyList(),
        subtitle = "3 bundled · 0 mine",
    )

    fun loading(): SkillLibraryViewState = SkillLibraryViewState(
        visualState = SkillLibraryVisualState.Loading,
        bundledCount = 0,
        mineCount = 0,
    )

    fun error(): SkillLibraryViewState = SkillLibraryViewState(
        visualState = SkillLibraryVisualState.Error,
        errorMessage = "The on-device skill store didn't respond. Your skills are safe — this is just a read.",
    )

    // ── Editor states ──────────────────────────────────────────────────────

    fun editorCreate(): SkillEditorUi = SkillEditorUi(
        id = null,
        fromBundled = false,
        name = "",
        description = "",
        instruction = "",
        availableVariables = SAMPLE_VARIABLES,
        toolMode = SkillToolMode.All,
        tools = SAMPLE_TOOLS,
        toolTotal = SAMPLE_TOOLS.size,
        contextFlags = contextFlags(allOn = false),
        nameError = false,
        instructionError = false,
        canSave = false,
    )

    fun editorEditRestrict(): SkillEditorUi = SkillEditorUi(
        id = "standup",
        fromBundled = false,
        name = "Standup digest",
        description = "Summarize my day from notes into three bullets.",
        instruction = "You are a standup assistant. Read the notes and write a 3-bullet daily " +
            "standup — Yesterday, Today, Blockers. Today is \$DATE. Keep it under 60 words.",
        availableVariables = SAMPLE_VARIABLES,
        toolMode = SkillToolMode.Restrict,
        tools = SAMPLE_TOOLS,
        toolTotal = SAMPLE_TOOLS.size,
        contextFlags = contextFlags(allOn = false),
        nameError = false,
        instructionError = false,
        canSave = true,
    )

    fun editorNoTools(): SkillEditorUi = editorEditRestrict().copy(toolMode = SkillToolMode.None)

    fun editorAllContext(): SkillEditorUi = editorEditRestrict().copy(
        toolMode = SkillToolMode.All,
        contextFlags = contextFlags(allOn = true),
    )

    fun editorInvalid(): SkillEditorUi = editorCreate().copy(nameError = true, instructionError = true)

    fun editorDuplicatedBundled(): SkillEditorUi = SkillEditorUi(
        id = null,
        fromBundled = true,
        name = "Report Writer (copy)",
        description = "Draft a structured report and save it to a file.",
        instruction = "You are a report writer. Draft a structured report with an overview, findings, " +
            "and a conclusion, then save it with write_file. Today is \$DATE.",
        availableVariables = SAMPLE_VARIABLES,
        toolMode = SkillToolMode.Restrict,
        tools = SAMPLE_TOOLS,
        toolTotal = SAMPLE_TOOLS.size,
        contextFlags = contextFlags(allOn = false),
        nameError = false,
        instructionError = false,
        canSave = true,
    )

    // ── Delete dialog states ───────────────────────────────────────────────

    fun deleteNoDependents(): SkillDeleteUi = SkillDeleteUi(skillName = "Standup digest")

    fun deleteOneDependent(): SkillDeleteUi =
        SkillDeleteUi(skillName = "Standup digest", dependentPipelineNames = listOf("weekly-report"))

    fun deleteManyDependents(): SkillDeleteUi = SkillDeleteUi(
        skillName = "Standup digest",
        dependentPipelineNames = listOf("triage-router", "weekly-report", "inbox-triage"),
    )
}
