package app.knotwork.android.domain.models

/**
 * One record of the external-automation request journal: what a third-party app
 * asked for, and what the app decided about it.
 *
 * **Every inbound request is journalled, admitted or refused.** The no-silent-skips
 * invariant of the trigger journal
 * ([app.knotwork.android.domain.models.TriggerEvaluation]) applies verbatim to the
 * external entry point, and for the same reason: an entry point that leaves no
 * trace is undiagnosable. A refusal because the contract is switched off is an
 * event too — without it, "my Tasker profile does nothing" and "no broadcast ever
 * reached the app" look identical from inside the app.
 *
 * **The sender is not attested.** A `BroadcastReceiver` learns the sending package
 * only when the *sender* opted in via `BroadcastOptions.setShareIdentityEnabled`,
 * which defaults to `false` and which no automation app or `adb` sets. So
 * [declaredReturnPackage] is the caller's own claim about itself and must never be
 * read as identity, while [attestedSenderPackage] carries the system-supplied name
 * on the rare occasion one is available. Keeping them in two columns is the point:
 * collapsing them would quietly promote a claim into a fact.
 *
 * @property id Stable unique id of this record (UUID), primary key.
 * @property requestId The caller-minted correlation id from the request, echoed
 *   back on the callback. Not unique here: a caller may reuse one, and the journal
 *   records what happened rather than enforcing caller hygiene.
 * @property receivedAt Epoch-millis at which the request was received.
 * @property action The action the request arrived with, verbatim — an unrecognised
 *   one is recorded as it was sent so a typo in a caller's profile is visible.
 * @property target The pipeline the caller named, or `null` when it named none
 *   (which is itself a refusal reason, never a fallback to the bound pipeline).
 * @property declaredReturnPackage The package the caller asked to be called back
 *   on. Caller-supplied and therefore unverified; `null` for a fire-and-forget call.
 * @property returnAction The action the callback is sent with.
 * @property attestedSenderPackage The sending package as reported by the system,
 *   or `null` — the ordinary case — when the sender did not share its identity.
 * @property status The decision, and later the settled outcome of the run it
 *   started. `Accepted` narrows to `Completed` or `Failed` when the run ends.
 * @property runId Id of the run enqueued for an accepted request; `null` for every
 *   refusal. Minted before the run exists so the correlation predates the work.
 * @property repeatCount How many identical consecutive refusals this row stands
 *   for. `1` is a single event. See
 *   [app.knotwork.android.domain.usecases.automation.HandleExternalAutomationRequestUseCase]
 *   for why refusals collapse and admissions never do.
 */
data class ExternalAutomationJournalEntry(
    val id: String,
    val requestId: String,
    val receivedAt: Long,
    val action: String,
    val target: ExternalAutomationTarget?,
    val declaredReturnPackage: String?,
    val returnAction: String,
    val attestedSenderPackage: String?,
    val status: ExternalAutomationStatus,
    val runId: String?,
    val repeatCount: Int = 1,
)
