package app.knotwork.android.domain.usecases.automation

import app.knotwork.android.domain.models.ExternalAutomationJournalEntry
import app.knotwork.android.domain.models.ExternalAutomationRejectionReason
import app.knotwork.android.domain.models.ExternalAutomationStatus
import app.knotwork.android.domain.models.ExternalAutomationTarget
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import javax.inject.Inject

/**
 * Renders the whole external-automation request journal to a machine-readable
 * JSON document — the counterpart of
 * [app.knotwork.android.domain.usecases.BuildTriggerJournalExportUseCase] for the
 * entry point third-party apps drive.
 *
 * The journal answers a question its owner cannot answer from inside their own
 * automation app: *my profile fires and nothing happens — did the broadcast reach
 * Knotwork at all, and what did it decide?* Exporting it is how that answer
 * travels to a bug report or to an offline analysis, on a release build, without
 * the debug dump path that only exists in `debug`.
 *
 * Pure domain logic: it formats the entry list the caller already holds — no I/O
 * and, deliberately, **no network access** (the guarantee shared with
 * [app.knotwork.android.domain.usecases.BuildUsageTelemetryExportUseCase] and
 * structurally enforced by `JournalExportNoNetworkKonsistTest`). The caller owns
 * the read and the write, and pre-formats the human-readable [generatedAtLabel]
 * because the domain owns no date formatting.
 *
 * **The document carries the journal and nothing else.** Every field below is a
 * column of [ExternalAutomationJournalEntry]; nothing about the run a request
 * started — its prompt, its steps, its answer — is read here, because the journal
 * never held any of it. The one caveat worth stating: the caller-supplied
 * strings (`requestId`, `action`, `targetValue`, `declaredReturnPackage`) are
 * journalled verbatim, so they travel verbatim.
 *
 * The status and reason vocabularies are **not** re-invented here: they are the
 * discriminators already persisted on the row and already published as the
 * callback contract in `docs/external-automation.md`. One frozen vocabulary, so a
 * consumer that can read a callback can read a dump.
 */
class BuildExternalAutomationJournalExportUseCase @Inject constructor() {

    /**
     * Renders [entries] to the pretty-printed JSON export document.
     *
     * @param entries The journal snapshot to export, in the caller's order (the
     *   repository yields it newest-first). The order is preserved verbatim.
     * @param generatedAtLabel Device-local "generated at" label, pre-formatted by
     *   the caller.
     * @return The JSON document as a string, ready to be written to a `.json` file.
     */
    operator fun invoke(entries: List<ExternalAutomationJournalEntry>, generatedAtLabel: String): String {
        val document = buildJsonObject {
            put("schemaVersion", SCHEMA_VERSION)
            put("generatedAt", generatedAtLabel)
            put("localOnly", true)
            put("totalRequests", entries.size)
            put(
                "requests",
                buildJsonArray {
                    for (entry in entries) {
                        addJsonObject { putEntry(entry) }
                    }
                },
            )
        }
        return PRETTY_JSON.encodeToString(JsonObject.serializer(), document)
    }

    /** Writes one journal entry's fields into the current JSON object builder. */
    private fun kotlinx.serialization.json.JsonObjectBuilder.putEntry(entry: ExternalAutomationJournalEntry) {
        put("id", entry.id)
        put("requestId", entry.requestId)
        put("receivedAt", entry.receivedAt)
        put("action", entry.action)
        // Kind and value stay two keys rather than one rendered label, because the
        // difference between "named by id" and "named by name" is what a
        // TARGET_AMBIGUOUS or TARGET_NOT_ALLOWED refusal turns on.
        put("targetKind", entry.target?.let(::targetKind))
        put("targetValue", entry.target?.let(::targetValue))
        // The caller's own claim about where to answer, kept apart from the
        // system-attested name for the same reason the table keeps two columns:
        // collapsing them would promote a claim into a fact.
        put("declaredReturnPackage", entry.declaredReturnPackage)
        put("returnAction", entry.returnAction)
        put("attestedSenderPackage", entry.attestedSenderPackage)
        put("status", statusKind(entry.status))
        put("statusReason", statusReason(entry.status))
        put("runId", entry.runId)
        put("repeatCount", entry.repeatCount)
    }

    /** The stable discriminator string for a status — the published callback vocabulary. */
    private fun statusKind(status: ExternalAutomationStatus): String = when (status) {
        ExternalAutomationStatus.Accepted -> STATUS_ACCEPTED
        ExternalAutomationStatus.Completed -> STATUS_COMPLETED
        ExternalAutomationStatus.Failed -> STATUS_FAILED
        is ExternalAutomationStatus.Rejected -> STATUS_REJECTED
        is ExternalAutomationStatus.Blocked -> STATUS_BLOCKED
    }

    /** The typed refusal reason of a refusal status, else `null`. */
    private fun statusReason(status: ExternalAutomationStatus): String? = reasonOf(status)?.name

    /** The refusal reason a status carries, if any. */
    private fun reasonOf(status: ExternalAutomationStatus): ExternalAutomationRejectionReason? = when (status) {
        is ExternalAutomationStatus.Rejected -> status.reason
        is ExternalAutomationStatus.Blocked -> status.reason
        else -> null
    }

    /** How the request named its target — `ID` or `NAME`, the persisted discriminators. */
    private fun targetKind(target: ExternalAutomationTarget): String = when (target) {
        is ExternalAutomationTarget.ById -> TARGET_KIND_ID
        is ExternalAutomationTarget.ByName -> TARGET_KIND_NAME
    }

    /** The pipeline id or name the request carried. */
    private fun targetValue(target: ExternalAutomationTarget): String = when (target) {
        is ExternalAutomationTarget.ById -> target.pipelineId
        is ExternalAutomationTarget.ByName -> target.pipelineName
    }

    private companion object {
        /**
         * Export-format version.
         *
         * Bumped on any shape change a consumer must be able to detect —
         * including a purely additive one, because "the key is absent" and "the
         * key is present and empty" are different findings when a dump is the
         * evidence for a claim about what an entry point did.
         *
         * - `1` — the original request shape.
         */
        const val SCHEMA_VERSION = 1

        const val STATUS_ACCEPTED = "Accepted"
        const val STATUS_COMPLETED = "Completed"
        const val STATUS_FAILED = "Failed"
        const val STATUS_REJECTED = "Rejected"
        const val STATUS_BLOCKED = "Blocked"

        const val TARGET_KIND_ID = "ID"
        const val TARGET_KIND_NAME = "NAME"

        /** Pretty-printing config, encoding defaults so `null`s render explicitly. */
        val PRETTY_JSON = Json {
            prettyPrint = true
            encodeDefaults = true
            explicitNulls = true
        }
    }
}
