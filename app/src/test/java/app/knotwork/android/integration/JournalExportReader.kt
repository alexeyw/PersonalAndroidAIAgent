package app.knotwork.android.integration

import app.knotwork.android.domain.models.ExternalAutomationJournalEntry
import app.knotwork.android.domain.models.ExternalAutomationRejectionReason
import app.knotwork.android.domain.models.ExternalAutomationStatus
import app.knotwork.android.domain.models.ExternalAutomationTarget
import app.knotwork.android.domain.models.PendingInteractionKind
import app.knotwork.android.domain.models.TriggerEvaluation
import app.knotwork.android.domain.models.TriggerEvaluationSource
import app.knotwork.android.domain.models.TriggerEvaluationVerdict
import app.knotwork.android.domain.models.TriggerHitlActivity
import app.knotwork.android.domain.models.TriggerHitlResolution
import app.knotwork.android.domain.models.TriggerRunOutcome
import app.knotwork.android.domain.models.TriggerSkipReason
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long

/**
 * **The** reader of the exported journal documents — the offline consumer the
 * export formats exist for, written once and pointed at every producer.
 *
 * This is the artefact the "one document, one parse" claim is measured against.
 * The app itself never reads these files back: they are written to be analysed
 * elsewhere, which is exactly the situation in which two writers quietly drift
 * apart and nobody notices until a soak window's evidence turns out to be
 * unreadable. Keeping the only reader here, and making every export test go
 * through it, means a producer that changes shape breaks a test rather than a
 * future analysis.
 *
 * Deliberately strict: a missing key or an unknown discriminator throws instead
 * of degrading to a default. A lenient reader would let a producer drop a field
 * and still pass, which is the failure this exists to catch.
 */
object JournalExportReader {

    /**
     * Parses a trigger-evaluation export back into the journal it was built from.
     *
     * @param json A document produced by `BuildTriggerJournalExportUseCase`.
     * @return The evaluations, in document order.
     */
    fun readTriggerJournal(json: String): List<TriggerEvaluation> {
        val document = Json.parseToJsonElement(json).jsonObject
        val evaluations = document.getValue("evaluations").jsonArray.map { it.jsonObject.toEvaluation() }
        check(document.getValue("totalEvaluations").jsonPrimitive.int == evaluations.size) {
            "totalEvaluations disagrees with the evaluations array"
        }
        return evaluations
    }

    /**
     * Parses an external-request export back into the journal it was built from.
     *
     * @param json A document produced by `BuildExternalAutomationJournalExportUseCase`.
     * @return The requests, in document order.
     */
    fun readRequestJournal(json: String): List<ExternalAutomationJournalEntry> {
        val document = Json.parseToJsonElement(json).jsonObject
        val requests = document.getValue("requests").jsonArray.map { it.jsonObject.toRequest() }
        check(document.getValue("totalRequests").jsonPrimitive.int == requests.size) {
            "totalRequests disagrees with the requests array"
        }
        return requests
    }

    private fun JsonObject.toEvaluation(): TriggerEvaluation = TriggerEvaluation(
        id = string("id"),
        triggerId = string("triggerId"),
        evaluatedAt = getValue("evaluatedAt").jsonPrimitive.long,
        source = TriggerEvaluationSource.valueOf(string("source")),
        verdict = readVerdict(),
        runId = stringOrNull("runId"),
        outcome = readOutcome(),
        hitl = readHitl(),
    )

    private fun JsonObject.readVerdict(): TriggerEvaluationVerdict = when (val kind = string("verdict")) {
        "FIRED" -> TriggerEvaluationVerdict.Fired
        "RE_ARMED" -> TriggerEvaluationVerdict.ReArmed
        "SKIPPED" -> TriggerEvaluationVerdict.Skipped(
            TriggerSkipReason.valueOf(
                stringOrNull("skipReason") ?: error("a SKIPPED verdict carries no skipReason"),
            ),
        )
        else -> error("unknown verdict discriminator: $kind")
    }

    private fun JsonObject.readOutcome(): TriggerRunOutcome? = when (val kind = stringOrNull("outcome")) {
        null -> null
        "SUCCESS" -> TriggerRunOutcome.Success
        "FAILURE" -> TriggerRunOutcome.Failure(
            stringOrNull("outcomeError") ?: error("a FAILURE outcome carries no outcomeError"),
        )
        "CANCELLED_BY_SYSTEM" -> TriggerRunOutcome.CancelledBySystem
        "CANCELLED" -> TriggerRunOutcome.Cancelled
        "HITL_TIMEOUT" -> TriggerRunOutcome.HitlTimeout
        "STOPPED_BY_CEILING" -> TriggerRunOutcome.StoppedByCeiling
        else -> error("unknown outcome discriminator: $kind")
    }

    /**
     * Rebuilds the HITL dimension.
     *
     * `hitlKind` is the presence marker: the model's `lastKind` is non-nullable, so
     * a row that recorded a gate always carries it, and a row that did not always
     * omits it. The flat `hitlGateCount` / `hitlParked` defaults (`0` / `false`)
     * are deliberately not used as the marker — they are also the legitimate values
     * of a row written before the gate columns existed.
     */
    private fun JsonObject.readHitl(): TriggerHitlActivity? {
        val kind = stringOrNull("hitlKind") ?: return null
        return TriggerHitlActivity(
            gateCount = getValue("hitlGateCount").jsonPrimitive.int,
            lastKind = PendingInteractionKind.valueOf(kind),
            lastResolution = TriggerHitlResolution.valueOf(
                stringOrNull("hitlResolution") ?: error("a recorded gate carries no hitlResolution"),
            ),
            parked = getValue("hitlParked").jsonPrimitive.boolean,
        )
    }

    private fun JsonObject.toRequest(): ExternalAutomationJournalEntry = ExternalAutomationJournalEntry(
        id = string("id"),
        requestId = string("requestId"),
        receivedAt = getValue("receivedAt").jsonPrimitive.long,
        action = string("action"),
        target = readTarget(),
        declaredReturnPackage = stringOrNull("declaredReturnPackage"),
        returnAction = string("returnAction"),
        attestedSenderPackage = stringOrNull("attestedSenderPackage"),
        status = readStatus(),
        runId = stringOrNull("runId"),
        repeatCount = getValue("repeatCount").jsonPrimitive.int,
    )

    private fun JsonObject.readTarget(): ExternalAutomationTarget? {
        val value = stringOrNull("targetValue") ?: return null
        return when (val kind = stringOrNull("targetKind")) {
            "ID" -> ExternalAutomationTarget.ById(value)
            "NAME" -> ExternalAutomationTarget.ByName(value)
            else -> error("unknown target kind: $kind")
        }
    }

    private fun JsonObject.readStatus(): ExternalAutomationStatus = when (val kind = string("status")) {
        "Accepted" -> ExternalAutomationStatus.Accepted
        "Completed" -> ExternalAutomationStatus.Completed
        "Failed" -> ExternalAutomationStatus.Failed
        "Rejected" -> ExternalAutomationStatus.Rejected(readReason())
        "Blocked" -> ExternalAutomationStatus.Blocked(readReason())
        else -> error("unknown status discriminator: $kind")
    }

    private fun JsonObject.readReason(): ExternalAutomationRejectionReason = ExternalAutomationRejectionReason.valueOf(
        stringOrNull("statusReason") ?: error("a refusal carries no statusReason"),
    )

    private fun JsonObject.string(key: String): String = getValue(key).jsonPrimitive.content

    private fun JsonObject.stringOrNull(key: String): String? = getValue(key).jsonPrimitive.let {
        if (it is kotlinx.serialization.json.JsonNull) null else it.content
    }
}
