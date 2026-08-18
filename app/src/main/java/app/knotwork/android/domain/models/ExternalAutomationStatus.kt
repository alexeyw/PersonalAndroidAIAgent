package app.knotwork.android.domain.models

/**
 * The state an external-automation request is reported in — to the caller over
 * the return callback, to the request journal, and to the settings surface.
 *
 * Modelled as a sealed hierarchy rather than a flat enum for one reason: two of
 * the states are meaningless without a cause. A caller told only "rejected"
 * cannot tell a switched-off contract from a request it built wrong, and the
 * journal could not explain a refusal after the fact. The shape mirrors
 * [TriggerEvaluationVerdict], which carries [TriggerSkipReason] the same way and
 * for the same reason.
 *
 * The discriminator names are **persisted** on the journal row and are part of
 * the public automation contract surfaced in `docs/external-automation.md`, so
 * they must never be renamed.
 *
 * Lifecycle: a request that survives parsing and authorization is [Accepted]
 * immediately, and later settles as [Completed] or [Failed] once the run it
 * started reaches a terminal status. [Rejected] and [Blocked] are terminal on
 * write — no run was started.
 */
sealed interface ExternalAutomationStatus {

    /** The request was admitted and a background run was enqueued. */
    data object Accepted : ExternalAutomationStatus

    /** The run started by the request finished successfully. */
    data object Completed : ExternalAutomationStatus

    /** The run started by the request failed, was cancelled, or was interrupted. */
    data object Failed : ExternalAutomationStatus

    /**
     * The request was refused before anything was started, because of what the
     * request said or how the app is configured. Retrying the identical request
     * against the identical configuration yields the identical refusal.
     *
     * @property reason What the request ran into.
     */
    data class Rejected(val reason: ExternalAutomationRejectionReason) : ExternalAutomationStatus

    /**
     * The request was well-formed and permitted, but a safety ceiling refused it
     * at this moment. Unlike [Rejected] this is a statement about the moment,
     * not about the request: the same request may be admitted later. It is
     * deliberately not a silent enqueue — a caller whose request was dropped on
     * the floor cannot tell that from one that ran.
     *
     * @property reason Which ceiling refused the request.
     */
    data class Blocked(val reason: ExternalAutomationRejectionReason) : ExternalAutomationStatus
}
