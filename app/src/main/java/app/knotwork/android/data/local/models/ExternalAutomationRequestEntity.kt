package app.knotwork.android.data.local.models

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Room row for one external-automation request journal record
 * (`external_automation_requests` table, introduced in v58). See
 * [app.knotwork.android.domain.models.ExternalAutomationJournalEntry] for the
 * domain contract and the no-silent-skips invariant it inherits.
 *
 * The sealed status and the request's target are flattened into string
 * discriminators plus a payload column rather than a JSON blob, so both hot
 * queries — "count admissions inside the rate window" and "settle the outcome of
 * this run" — stay plain indexed SQL. The discriminator strings mirror the domain
 * names and, being persisted **and published in the third-party contract**, must
 * never be renamed.
 *
 * **No foreign key on [runId].** The journal is a diagnostic observer that must
 * outlive the run it describes: retention bounds its growth
 * ([app.knotwork.android.domain.usecases.automation.CleanupExternalAutomationJournalUseCase]),
 * not a cascade — the same reasoning as `trigger_evaluations`, and doubly so here,
 * because most rows describe requests that never produced a run at all.
 *
 * @property id Stable unique id of this record (UUID), primary key.
 * @property requestId The caller-minted correlation id. Deliberately not unique
 *   and not indexed: nothing looks a row up by it, and a caller reusing one is a
 *   fact to record rather than an error to reject.
 * @property receivedAt Epoch-millis the request was received. Indexed — it orders
 *   the journal, bounds the rate window and drives the retention pass. On a
 *   collapsed refusal this advances to the most recent occurrence, so retention
 *   ages a repeating refusal from when it last happened.
 * @property action The action the request arrived with, verbatim.
 * @property targetKind `ID` or `NAME` for how the caller named its target; `null`
 *   when it named none.
 * @property targetValue The pipeline id or name the caller sent; `null` with
 *   [targetKind].
 * @property declaredReturnPackage Caller-supplied callback package. Unverified —
 *   see the domain model's KDoc on why this is never treated as identity.
 * @property returnAction Action the callback is sent with.
 * @property attestedSenderPackage System-supplied sender package, `null` in the
 *   ordinary case where the sender did not opt in to sharing its identity.
 * @property statusKind Status discriminator: `Accepted` / `Completed` / `Failed` /
 *   `Rejected` / `Blocked`.
 * @property statusReason
 *   [app.knotwork.android.domain.models.ExternalAutomationRejectionReason] name for
 *   a `Rejected` or `Blocked` status; `null` otherwise.
 * @property runId Id of the enqueued run for an admitted request, `null` for every
 *   refusal. Indexed because the terminal-outcome write matches on it, and because
 *   `runId IS NOT NULL` is exactly the predicate the rate ceiling counts over.
 * @property repeatCount How many identical consecutive refusals this row stands
 *   for; `1` for a single event and always `1` for an admission.
 */
@Entity(
    tableName = "external_automation_requests",
    indices = [
        Index(value = ["receivedAt"]),
        Index(value = ["runId"]),
    ],
)
data class ExternalAutomationRequestEntity(
    @PrimaryKey val id: String,
    val requestId: String,
    val receivedAt: Long,
    val action: String,
    val targetKind: String?,
    val targetValue: String?,
    val declaredReturnPackage: String?,
    val returnAction: String,
    val attestedSenderPackage: String?,
    val statusKind: String,
    val statusReason: String?,
    val runId: String?,
    val repeatCount: Int,
)
