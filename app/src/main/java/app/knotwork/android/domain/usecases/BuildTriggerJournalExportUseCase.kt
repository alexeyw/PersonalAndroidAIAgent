package app.knotwork.android.domain.usecases

import app.knotwork.android.domain.models.TriggerEvaluation
import app.knotwork.android.domain.models.TriggerEvaluationVerdict
import app.knotwork.android.domain.models.TriggerRunOutcome
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import javax.inject.Inject

/**
 * Renders the whole trigger-evaluation journal to a machine-readable JSON
 * document — the offline-analysis artefact of the background-reliability soak
 * protocol (`project_docs/metrics.md`).
 *
 * During a soak run the app is deliberately never opened (so the in-app journal
 * UI cannot be read), and the journal itself lives in the SQLCipher-encrypted
 * database (so `adb pull` + `sqlite3` cannot read it either). A debug-only
 * receiver dumps the output of this use case to an app-private file that a plain
 * `adb pull` can retrieve, keeping the closed-app discipline intact while still
 * making every evaluation auditable after the fact.
 *
 * Pure domain logic: it only formats the [TriggerEvaluation] list the caller
 * already holds — no I/O and, crucially, **no network access** (the same
 * on-device guarantee as [BuildUsageTelemetryExportUseCase]). The caller owns
 * the read (repository snapshot) and the write (file), and pre-formats the
 * human-readable [generatedAtLabel] because the domain owns no date formatting.
 *
 * The verdict and run-outcome sealed hierarchies are flattened to the stable
 * discriminator strings below; they mirror the model's variant names so a dump
 * reads the same vocabulary as the journal contract, and — being an exported
 * format — must not be renamed once a soak run has captured them.
 */
class BuildTriggerJournalExportUseCase @Inject constructor() {

    /**
     * Renders [evaluations] to the pretty-printed JSON export document.
     *
     * @param evaluations The journal snapshot to export, in the caller's order
     *   (the repository yields it newest-first). The order is preserved verbatim.
     * @param generatedAtLabel Device-local "generated at" label, pre-formatted by
     *   the caller.
     * @return The JSON document as a string, ready to be written to a `.json` file.
     */
    operator fun invoke(evaluations: List<TriggerEvaluation>, generatedAtLabel: String): String {
        val document = buildJsonObject {
            put("schemaVersion", SCHEMA_VERSION)
            put("generatedAt", generatedAtLabel)
            put("localOnly", true)
            put("totalEvaluations", evaluations.size)
            put(
                "evaluations",
                buildJsonArray {
                    for (evaluation in evaluations) {
                        addJsonObject { putEvaluation(evaluation) }
                    }
                },
            )
        }
        return PRETTY_JSON.encodeToString(JsonObject.serializer(), document)
    }

    /** Writes one evaluation's fields into the current JSON object builder. */
    private fun kotlinx.serialization.json.JsonObjectBuilder.putEvaluation(evaluation: TriggerEvaluation) {
        put("id", evaluation.id)
        put("triggerId", evaluation.triggerId)
        put("evaluatedAt", evaluation.evaluatedAt)
        put("source", evaluation.source.name)
        put("verdict", verdictKind(evaluation.verdict))
        put("skipReason", skipReason(evaluation.verdict))
        put("runId", evaluation.runId)
        put("outcome", evaluation.outcome?.let(::outcomeKind))
        put("outcomeError", outcomeError(evaluation.outcome))
    }

    /** The stable discriminator string for a verdict. */
    private fun verdictKind(verdict: TriggerEvaluationVerdict): String = when (verdict) {
        TriggerEvaluationVerdict.Fired -> VERDICT_FIRED
        TriggerEvaluationVerdict.ReArmed -> VERDICT_RE_ARMED
        is TriggerEvaluationVerdict.Skipped -> VERDICT_SKIPPED
    }

    /** The typed skip reason name for a [TriggerEvaluationVerdict.Skipped], else `null`. */
    private fun skipReason(verdict: TriggerEvaluationVerdict): String? =
        (verdict as? TriggerEvaluationVerdict.Skipped)?.reason?.name

    /** The stable discriminator string for a settled run outcome. */
    private fun outcomeKind(outcome: TriggerRunOutcome): String = when (outcome) {
        TriggerRunOutcome.Success -> OUTCOME_SUCCESS
        is TriggerRunOutcome.Failure -> OUTCOME_FAILURE
        TriggerRunOutcome.CancelledBySystem -> OUTCOME_CANCELLED_BY_SYSTEM
        TriggerRunOutcome.Cancelled -> OUTCOME_CANCELLED
        TriggerRunOutcome.HitlTimeout -> OUTCOME_HITL_TIMEOUT
    }

    /** The human-readable error of a [TriggerRunOutcome.Failure], else `null`. */
    private fun outcomeError(outcome: TriggerRunOutcome?): String? = (outcome as? TriggerRunOutcome.Failure)?.error

    private companion object {
        /** Export-format version; bumped only on a breaking shape change. */
        const val SCHEMA_VERSION = 1

        const val VERDICT_FIRED = "FIRED"
        const val VERDICT_RE_ARMED = "RE_ARMED"
        const val VERDICT_SKIPPED = "SKIPPED"

        const val OUTCOME_SUCCESS = "SUCCESS"
        const val OUTCOME_FAILURE = "FAILURE"
        const val OUTCOME_CANCELLED_BY_SYSTEM = "CANCELLED_BY_SYSTEM"
        const val OUTCOME_CANCELLED = "CANCELLED"
        const val OUTCOME_HITL_TIMEOUT = "HITL_TIMEOUT"

        /** Pretty-printing config, encoding defaults so `null`s render explicitly. */
        val PRETTY_JSON = Json {
            prettyPrint = true
            encodeDefaults = true
            explicitNulls = true
        }
    }
}
