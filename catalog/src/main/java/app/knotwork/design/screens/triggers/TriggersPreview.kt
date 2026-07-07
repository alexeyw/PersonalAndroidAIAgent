package app.knotwork.design.screens.triggers

/**
 * Canonical sample states for the Triggers surfaces, shared by Android Studio
 * `@Preview`s and the Roborazzi snapshot baselines so the previews and the
 * recorded images never drift.
 */
object TriggersPreview {

    private val ROWS = listOf(
        TriggerRowUi(
            id = "overnight",
            name = "Overnight summary",
            conditionLabel = "When charging connected",
            conditionType = TriggerConditionType.Charging,
            pipelineName = "Overnight summarizer",
            enabled = true,
        ),
        TriggerRowUi(
            id = "morning",
            name = "Morning briefing",
            conditionLabel = "Every day at 08:00",
            conditionType = TriggerConditionType.Daily,
            pipelineName = "Daily briefing",
            enabled = false,
        ),
        TriggerRowUi(
            id = "commute",
            name = "Commute headlines",
            conditionLabel = "When Wi-Fi connects",
            conditionType = TriggerConditionType.Network,
            pipelineName = "News scanner",
            enabled = true,
        ),
        TriggerRowUi(
            id = "backup",
            name = "Photo backup notes",
            conditionLabel = "Every 6 hours",
            conditionType = TriggerConditionType.Interval,
            pipelineName = null,
            enabled = false,
        ),
    )

    private val PIPELINES = listOf(
        TriggerPipelineOptionUi("p1", "Overnight summarizer"),
        TriggerPipelineOptionUi("p2", "Daily briefing"),
        TriggerPipelineOptionUi("p3", "News scanner"),
    )

    private const val SUBTITLE = "4 triggers · 2 active"

    // ── List states ────────────────────────────────────────────────────────

    fun populated(): TriggersViewState = TriggersViewState(
        visualState = TriggersVisualState.Default,
        rows = ROWS,
        subtitle = SUBTITLE,
    )

    fun rowMenu(): TriggersViewState = populated().copy(openMenuRowId = "morning")

    fun empty(): TriggersViewState = TriggersViewState(
        visualState = TriggersVisualState.Empty,
        rows = emptyList(),
        subtitle = "",
    )

    fun loading(): TriggersViewState = TriggersViewState(visualState = TriggersVisualState.Loading)

    fun error(): TriggersViewState = TriggersViewState(
        visualState = TriggersVisualState.Error,
        errorMessage = "The on-device trigger store didn't respond. Your triggers and their schedules are safe — " +
            "this is just a read.",
    )

    // ── Editor states ──────────────────────────────────────────────────────

    private fun baseEditor(): TriggerEditorUi = TriggerEditorUi(
        id = "morning",
        name = "Morning briefing",
        type = TriggerConditionType.Daily,
        intervalCustom = false,
        intervalMinutes = 360L,
        intervalCustomAmount = "45",
        intervalCustomUnitHours = false,
        intervalError = false,
        dailyHour = 8,
        dailyMinute = 0,
        wifiOnly = false,
        ssids = emptyList(),
        pipelines = PIPELINES,
        pipelineName = "Daily briefing",
        prompt = "Summarize today's calendar and unread mail into a short briefing.",
        enabled = true,
        nameError = false,
        canSave = true,
    )

    fun editorCreate(): TriggerEditorUi = TriggerEditorUi(
        id = null,
        name = "",
        type = TriggerConditionType.Daily,
        intervalCustom = false,
        intervalMinutes = 30L,
        intervalCustomAmount = "",
        intervalCustomUnitHours = false,
        intervalError = false,
        dailyHour = 8,
        dailyMinute = 0,
        wifiOnly = false,
        ssids = emptyList(),
        pipelines = PIPELINES,
        pipelineName = null,
        prompt = "",
        enabled = true,
        nameError = false,
        canSave = false,
    )

    fun editorDaily(): TriggerEditorUi = baseEditor()

    fun editorInterval(): TriggerEditorUi = baseEditor().copy(
        type = TriggerConditionType.Interval,
        intervalCustom = true,
        intervalCustomAmount = "45",
        intervalCustomUnitHours = false,
    )

    fun editorCharging(): TriggerEditorUi = baseEditor().copy(type = TriggerConditionType.Charging)

    fun editorNetwork(): TriggerEditorUi = baseEditor().copy(type = TriggerConditionType.Network, wifiOnly = true)

    fun editorNetworkScoped(): TriggerEditorUi = baseEditor().copy(
        type = TriggerConditionType.Network,
        wifiOnly = true,
        ssids = listOf("Home", "Office"),
    )

    fun editorInvalid(): TriggerEditorUi = editorCreate().copy(
        type = TriggerConditionType.Interval,
        intervalCustom = true,
        intervalCustomAmount = "0",
        intervalError = true,
        nameError = true,
    )

    // ── Delete dialog state ────────────────────────────────────────────────

    fun delete(): TriggerDeleteUi = TriggerDeleteUi(triggerName = "Morning briefing")
}
