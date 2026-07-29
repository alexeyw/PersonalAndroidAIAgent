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
            health = TriggerHealthUi.Healthy,
        ),
        TriggerRowUi(
            id = "triage",
            name = "Inbox triage",
            conditionLabel = "Every 30 minutes",
            conditionType = TriggerConditionType.Interval,
            pipelineName = "Inbox triage",
            enabled = true,
            health = TriggerHealthUi.LastRunFailed,
        ),
        TriggerRowUi(
            id = "commute",
            name = "Commute headlines",
            conditionLabel = "When Wi-Fi connects",
            conditionType = TriggerConditionType.Network,
            pipelineName = "News scanner",
            enabled = true,
            health = TriggerHealthUi.Overdue,
        ),
        TriggerRowUi(
            id = "morning",
            name = "Morning briefing",
            conditionLabel = "Every day at 08:00",
            conditionType = TriggerConditionType.Daily,
            pipelineName = "Daily briefing",
            enabled = false,
            health = null,
        ),
        TriggerRowUi(
            id = "backup",
            name = "Photo backup notes",
            conditionLabel = "Every 6 hours",
            conditionType = TriggerConditionType.Interval,
            pipelineName = null,
            enabled = false,
            health = null,
        ),
    )

    private val PIPELINES = listOf(
        TriggerPipelineOptionUi("p1", "Overnight summarizer"),
        TriggerPipelineOptionUi("p2", "Daily briefing"),
        TriggerPipelineOptionUi("p3", "News scanner"),
    )

    private const val SUBTITLE = "5 triggers · 3 active"

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

    // ── Detail screen states ───────────────────────────────────────────────

    /**
     * A populated journal covering every legibility case: a pending fire, a
     * settled success, the three distinct non-success outcomes, both skip
     * sentences, and a re-arm — spread across three day groups. Four of the
     * fired entries also carry a human-in-the-loop line, covering the states
     * that differ visually: still waiting, approved and answered (settled,
     * muted), and timed out (the warn case), with and without the parked
     * qualifier.
     */
    private val DAY_GROUPS = listOf(
        TriggerJournalDayGroupUi(
            headerLabel = "Today",
            entries = listOf(
                TriggerJournalEntryUi(
                    id = "e1",
                    source = TriggerJournalSourceUi.Poll,
                    verdict = TriggerJournalVerdictUi.Fired,
                    outcome = TriggerJournalOutcomeUi.Pending,
                    timestampLabel = "just now",
                    hitl = TriggerJournalHitlUi.Waiting,
                    hitlParked = true,
                ),
                TriggerJournalEntryUi(
                    id = "e2",
                    source = TriggerJournalSourceUi.Poll,
                    verdict = TriggerJournalVerdictUi.Fired,
                    outcome = TriggerJournalOutcomeUi.Success,
                    timestampLabel = "08:15",
                    hitl = TriggerJournalHitlUi.Approved,
                    hitlParked = true,
                ),
                TriggerJournalEntryUi(
                    id = "e3",
                    source = TriggerJournalSourceUi.Poll,
                    verdict = TriggerJournalVerdictUi.Skipped,
                    skipReason = TriggerJournalSkipReasonUi.ConditionNotMet,
                    skipMomentLabel = "07:15",
                    timestampLabel = "07:15",
                ),
            ),
        ),
        TriggerJournalDayGroupUi(
            headerLabel = "Yesterday",
            entries = listOf(
                TriggerJournalEntryUi(
                    id = "e4",
                    source = TriggerJournalSourceUi.Charging,
                    verdict = TriggerJournalVerdictUi.Fired,
                    outcome = TriggerJournalOutcomeUi.CancelledBySystem,
                    timestampLabel = "22:40",
                ),
                TriggerJournalEntryUi(
                    id = "e5",
                    source = TriggerJournalSourceUi.Event,
                    verdict = TriggerJournalVerdictUi.Fired,
                    outcome = TriggerJournalOutcomeUi.Failure,
                    outcomeError = "model timed out",
                    timestampLabel = "18:10",
                ),
                TriggerJournalEntryUi(
                    id = "e6",
                    source = TriggerJournalSourceUi.Poll,
                    verdict = TriggerJournalVerdictUi.Skipped,
                    skipReason = TriggerJournalSkipReasonUi.AlreadyFired,
                    timestampLabel = "12:30",
                ),
                TriggerJournalEntryUi(
                    id = "e7",
                    source = TriggerJournalSourceUi.Event,
                    verdict = TriggerJournalVerdictUi.ReArmed,
                    timestampLabel = "08:15",
                ),
            ),
        ),
        TriggerJournalDayGroupUi(
            headerLabel = "Mon 14 Jul",
            entries = listOf(
                TriggerJournalEntryUi(
                    id = "e8",
                    source = TriggerJournalSourceUi.Poll,
                    verdict = TriggerJournalVerdictUi.Fired,
                    outcome = TriggerJournalOutcomeUi.HitlTimeout,
                    timestampLabel = "19:05",
                    hitl = TriggerJournalHitlUi.TimedOut,
                    hitlParked = true,
                ),
                TriggerJournalEntryUi(
                    id = "e9",
                    source = TriggerJournalSourceUi.Event,
                    verdict = TriggerJournalVerdictUi.Fired,
                    outcome = TriggerJournalOutcomeUi.Cancelled,
                    timestampLabel = "15:20",
                    hitl = TriggerJournalHitlUi.Answered,
                ),
                TriggerJournalEntryUi(
                    id = "e10",
                    source = TriggerJournalSourceUi.Poll,
                    verdict = TriggerJournalVerdictUi.Skipped,
                    skipReason = TriggerJournalSkipReasonUi.Disabled,
                    timestampLabel = "08:15",
                ),
            ),
        ),
    )

    private fun baseDetail(): TriggerDetailViewState = TriggerDetailViewState(
        name = "Inbox triage",
        conditionType = TriggerConditionType.Interval,
        conditionLabel = "Every 30 minutes",
        pipelineName = "Inbox triage",
        enabled = true,
    )

    fun detailPopulated(): TriggerDetailViewState = baseDetail().copy(
        journalState = TriggerJournalVisualState.Populated,
        dayGroups = DAY_GROUPS,
    )

    fun detailStale(): TriggerDetailViewState = detailPopulated().copy(
        showStaleBanner = true,
        staleSinceLabel = "07:15",
    )

    fun detailEmpty(): TriggerDetailViewState = baseDetail().copy(
        name = "Morning briefing",
        conditionType = TriggerConditionType.Daily,
        conditionLabel = "Every day at 08:00",
        pipelineName = "Daily briefing",
        journalState = TriggerJournalVisualState.Empty,
    )

    fun detailLoading(): TriggerDetailViewState = baseDetail().copy(journalState = TriggerJournalVisualState.Loading)

    fun detailUnbound(): TriggerDetailViewState = baseDetail().copy(
        name = "Photo backup notes",
        pipelineName = null,
        enabled = false,
        journalState = TriggerJournalVisualState.Empty,
    )
}
