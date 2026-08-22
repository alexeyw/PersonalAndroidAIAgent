package app.knotwork.design.screens.automation

/**
 * Canonical sample states for the external-automation surfaces, shared by Android
 * Studio `@Preview`s and the Roborazzi snapshot baselines so the previews and the
 * recorded images never drift.
 *
 * The fixtures deliberately cover the shapes that are awkward rather than the
 * ones that are pretty: a caller looping against a switched-off contract, a
 * request that tried to redirect the answer at a third package, and an admission
 * whose run has not settled yet.
 */
object ExternalAutomationPreview {

    // Meanings track the shipped strings rather than being shortened for the
    // fixture. The block carries no required/optional tag, so the meaning line is
    // the only place a key's condition can appear — a fixture that drops the
    // conditions would record a baseline of the design not doing its one job.
    private val CALL_KEYS = listOf(
        ExternalCallKeyUi("pipeline_id", "Id of the pipeline to run. Use this or the name, never both."),
        ExternalCallKeyUi("pipeline_name", "Name of the pipeline to run, matched exactly."),
        ExternalCallKeyUi("prompt", "The message the pipeline runs on."),
        ExternalCallKeyUi("prompt_b64", "The same message, base64-encoded, for shells that mangle quoting."),
        ExternalCallKeyUi(
            "request_id",
            "Your own id. It comes back with the answer, so you can match them up. " +
                "Needed only if you ask for an answer.",
        ),
        ExternalCallKeyUi("return_action", "Action to send the answer with. Defaults to the contract's own."),
        ExternalCallKeyUi("return_package", "Package to deliver the answer to. Leave it out for fire-and-forget."),
    )

    private val TODAY = ExternalRequestDayGroupUi(
        headerLabel = "Today",
        entries = listOf(
            ExternalRequestEntryUi(
                id = "r1",
                status = ExternalRequestStatusUi.Accepted,
                targetLabel = "Morning digest",
                senderLabel = "net.dinglisch.android.taskerm",
                senderKind = ExternalRequestSenderKindUi.Claimed,
                requestIdLabel = "tsk-4f3a…",
                timestampLabel = "just now",
            ),
            ExternalRequestEntryUi(
                id = "r2",
                status = ExternalRequestStatusUi.Completed,
                targetLabel = "Morning digest",
                senderLabel = "net.dinglisch.android.taskerm",
                senderKind = ExternalRequestSenderKindUi.Claimed,
                requestIdLabel = "tsk-4f39…",
                timestampLabel = "12m ago",
            ),
            ExternalRequestEntryUi(
                id = "r3",
                status = ExternalRequestStatusUi.Blocked,
                reason = ExternalRequestReasonUi.RateLimited,
                targetLabel = "Morning digest",
                senderLabel = "net.dinglisch.android.taskerm",
                senderKind = ExternalRequestSenderKindUi.Claimed,
                requestIdLabel = "tsk-4f38…",
                timestampLabel = "41m ago",
                repeatCount = 4,
            ),
            ExternalRequestEntryUi(
                id = "r4",
                status = ExternalRequestStatusUi.Failed,
                targetLabel = "Morning digest",
                requestIdLabel = "adb-0007",
                timestampLabel = "09:12",
            ),
        ),
    )

    private val YESTERDAY = ExternalRequestDayGroupUi(
        headerLabel = "Yesterday",
        entries = listOf(
            ExternalRequestEntryUi(
                id = "r5",
                status = ExternalRequestStatusUi.Rejected,
                reason = ExternalRequestReasonUi.TargetNotAllowed,
                targetLabel = "Expense report",
                senderLabel = "com.arlosoft.macrodroid",
                senderKind = ExternalRequestSenderKindUi.Claimed,
                requestIdLabel = "md-1188",
                timestampLabel = "18:44",
                repeatCount = 3,
            ),
            ExternalRequestEntryUi(
                id = "r6",
                status = ExternalRequestStatusUi.Rejected,
                reason = ExternalRequestReasonUi.ReturnPackageMismatch,
                targetLabel = "Morning digest",
                senderLabel = "com.example.wallet",
                senderKind = ExternalRequestSenderKindUi.Attested,
                requestIdLabel = "x-9001",
                timestampLabel = "16:02",
            ),
            ExternalRequestEntryUi(
                id = "r7",
                status = ExternalRequestStatusUi.Rejected,
                reason = ExternalRequestReasonUi.UnknownAction,
                actionLabel = "app.knotwork.android.action.RUN_PIPLINE",
                showAction = true,
                requestIdLabel = "adb-0004",
                timestampLabel = "11:37",
            ),
        ),
    )

    private fun base(): ExternalAutomationJournalViewState = ExternalAutomationJournalViewState(
        contractEnabled = true,
        boundPipelineName = "Morning digest",
        callAction = "app.knotwork.android.action.RUN_PIPELINE",
        callKeys = CALL_KEYS,
    )

    /** The working state: switched on, bound, and a mixed timeline behind it. */
    fun populated(): ExternalAutomationJournalViewState = base().copy(
        journalState = ExternalJournalVisualState.Populated,
        dayGroups = listOf(TODAY, YESTERDAY),
    )

    /** Switched on and bound, but nothing has ever arrived — the common first state. */
    fun empty(): ExternalAutomationJournalViewState = base().copy(
        journalState = ExternalJournalVisualState.Empty,
    )

    /** Switched off: the journal explains that nothing will be accepted. */
    fun contractOff(): ExternalAutomationJournalViewState = base().copy(
        contractEnabled = false,
        journalState = ExternalJournalVisualState.Empty,
    )

    /** Switched on with nothing bound — reachable, inert, and easy to mistake for broken. */
    fun unbound(): ExternalAutomationJournalViewState = base().copy(
        boundPipelineName = null,
        journalState = ExternalJournalVisualState.Empty,
    )

    /**
     * A caller looping against a switched-off contract. One collapsed row with a
     * repeat count — the realistic shape of a misconfigured profile, which must
     * not read as forty-three separate incidents.
     */
    fun refusalHeavy(): ExternalAutomationJournalViewState = base().copy(
        contractEnabled = false,
        journalState = ExternalJournalVisualState.Populated,
        dayGroups = listOf(
            ExternalRequestDayGroupUi(
                headerLabel = "Today",
                entries = listOf(
                    ExternalRequestEntryUi(
                        id = "loop",
                        status = ExternalRequestStatusUi.Rejected,
                        reason = ExternalRequestReasonUi.ContractDisabled,
                        targetLabel = "Morning digest",
                        senderLabel = "net.dinglisch.android.taskerm",
                        senderKind = ExternalRequestSenderKindUi.Claimed,
                        requestIdLabel = "tsk-loop",
                        timestampLabel = "just now",
                        repeatCount = 43,
                    ),
                ),
            ),
        ),
    )

    /** First read from the encrypted store. */
    fun loading(): ExternalAutomationJournalViewState = base().copy(
        journalState = ExternalJournalVisualState.Loading,
    )

    /** The wire-contract block open, for the snapshot that has to prove it reads. */
    fun callBlockOpen(): ExternalAutomationJournalViewState = populated().copy(
        callBlockInitiallyExpanded = true,
    )
}
