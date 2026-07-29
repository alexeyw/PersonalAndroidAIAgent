package app.knotwork.android.domain.usecases

import app.knotwork.android.domain.models.PendingInteractionKind
import app.knotwork.android.domain.models.TriggerEvaluation
import app.knotwork.android.domain.models.TriggerEvaluationSource
import app.knotwork.android.domain.models.TriggerEvaluationVerdict
import app.knotwork.android.domain.models.TriggerHitlActivity
import app.knotwork.android.domain.models.TriggerHitlResolution
import app.knotwork.android.domain.models.TriggerRunOutcome
import app.knotwork.android.domain.models.TriggerSkipReason
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Verifies that [BuildTriggerJournalExportUseCase] renders the journal snapshot
 * into a correct JSON document: the header (schema, local-only, count), the
 * discriminator strings of every verdict and run outcome, the typed skip reason
 * and failure error, and that the caller's order is preserved verbatim.
 */
class BuildTriggerJournalExportUseCaseTest {

    private val useCase = BuildTriggerJournalExportUseCase()

    private val label = "2026-07-18 21:30:00"

    private fun evaluation(
        id: String,
        triggerId: String = "trig-1",
        evaluatedAt: Long = 0L,
        source: TriggerEvaluationSource = TriggerEvaluationSource.POLL,
        verdict: TriggerEvaluationVerdict = TriggerEvaluationVerdict.Fired,
        runId: String? = null,
        outcome: TriggerRunOutcome? = null,
        hitl: TriggerHitlActivity? = null,
    ) = TriggerEvaluation(id, triggerId, evaluatedAt, source, verdict, runId, outcome, hitl)

    private fun render(evaluations: List<TriggerEvaluation>) =
        Json.parseToJsonElement(useCase(evaluations, label)).jsonObject

    @Test
    fun `given an empty journal when rendered then the header is present and the list is empty`() {
        val document = render(emptyList())

        // Bumped to 2 by the hitl* fields: an analyst reading a dump must be
        // able to tell "this build never recorded gates" from "no gate here".
        assertEquals(2, document.getValue("schemaVersion").jsonPrimitive.int)
        assertEquals(label, document.getValue("generatedAt").jsonPrimitive.content)
        assertTrue(document.getValue("localOnly").jsonPrimitive.content.toBoolean())
        assertEquals(0, document.getValue("totalEvaluations").jsonPrimitive.int)
        assertTrue(document.getValue("evaluations").jsonArray.isEmpty())
    }

    @Test
    fun `given a fired evaluation with a success outcome when rendered then its fields round-trip`() {
        val document = render(
            listOf(
                evaluation(
                    id = "a",
                    triggerId = "evening-journal",
                    evaluatedAt = 1_700_000_000_000,
                    source = TriggerEvaluationSource.CHARGING_SWEEP,
                    verdict = TriggerEvaluationVerdict.Fired,
                    runId = "run-a",
                    outcome = TriggerRunOutcome.Success,
                ),
            ),
        )

        val row = document.getValue("evaluations").jsonArray.single().jsonObject
        assertEquals("a", row.getValue("id").jsonPrimitive.content)
        assertEquals("evening-journal", row.getValue("triggerId").jsonPrimitive.content)
        assertEquals(1_700_000_000_000, row.getValue("evaluatedAt").jsonPrimitive.long)
        assertEquals("CHARGING_SWEEP", row.getValue("source").jsonPrimitive.content)
        assertEquals("FIRED", row.getValue("verdict").jsonPrimitive.content)
        assertEquals(JsonNull, row.getValue("skipReason"))
        assertEquals("run-a", row.getValue("runId").jsonPrimitive.content)
        assertEquals("SUCCESS", row.getValue("outcome").jsonPrimitive.content)
        assertEquals(JsonNull, row.getValue("outcomeError"))
        // A run that never asked renders as zero gates, not as absent keys — the
        // dump is filtered offline, where a missing key reads as a parse bug.
        assertEquals(0, row.getValue("hitlGateCount").jsonPrimitive.int)
        assertEquals(JsonNull, row.getValue("hitlKind"))
        assertEquals(JsonNull, row.getValue("hitlResolution"))
        assertEquals(false, row.getValue("hitlParked").jsonPrimitive.content.toBoolean())
    }

    @Test
    fun `given a run that parked on an approval and got it when rendered then the dump says so`() {
        val document = render(
            listOf(
                evaluation(
                    id = "h",
                    verdict = TriggerEvaluationVerdict.Fired,
                    runId = "run-h",
                    outcome = TriggerRunOutcome.Success,
                    hitl = TriggerHitlActivity(
                        gateCount = 2,
                        lastKind = PendingInteractionKind.APPROVAL,
                        lastResolution = TriggerHitlResolution.APPROVED,
                        parked = true,
                    ),
                ),
            ),
        )

        // This row is what makes the background-approval criterion of the soak
        // protocol checkable from the dump instead of from operator memory.
        val row = document.getValue("evaluations").jsonArray.single().jsonObject
        assertEquals(2, row.getValue("hitlGateCount").jsonPrimitive.int)
        assertEquals("APPROVAL", row.getValue("hitlKind").jsonPrimitive.content)
        assertEquals("APPROVED", row.getValue("hitlResolution").jsonPrimitive.content)
        assertEquals(true, row.getValue("hitlParked").jsonPrimitive.content.toBoolean())
    }

    @Test
    fun `given every hitl resolution when rendered then each maps to its discriminator`() {
        val evaluations = TriggerHitlResolution.entries.mapIndexed { index, resolution ->
            evaluation(
                id = "hitl-$index",
                runId = "run-hitl-$index",
                hitl = TriggerHitlActivity(
                    gateCount = 1,
                    lastKind = PendingInteractionKind.CLARIFICATION,
                    lastResolution = resolution,
                    parked = false,
                ),
            )
        }

        val rendered = render(evaluations).getValue("evaluations").jsonArray
            .map { it.jsonObject.getValue("hitlResolution").jsonPrimitive.content }

        assertEquals(TriggerHitlResolution.entries.map { it.name }, rendered)
    }

    @Test
    fun `given a skipped evaluation when rendered then the typed reason is carried and no run fields`() {
        val document = render(
            listOf(
                evaluation(
                    id = "s",
                    verdict = TriggerEvaluationVerdict.Skipped(TriggerSkipReason.CONDITION_NOT_MET),
                ),
            ),
        )

        val row = document.getValue("evaluations").jsonArray.single().jsonObject
        assertEquals("SKIPPED", row.getValue("verdict").jsonPrimitive.content)
        assertEquals("CONDITION_NOT_MET", row.getValue("skipReason").jsonPrimitive.content)
        assertEquals(JsonNull, row.getValue("runId"))
        assertEquals(JsonNull, row.getValue("outcome"))
        assertEquals(JsonNull, row.getValue("outcomeError"))
    }

    @Test
    fun `given a re-armed evaluation when rendered then the verdict discriminator is RE_ARMED`() {
        val document = render(listOf(evaluation(id = "r", verdict = TriggerEvaluationVerdict.ReArmed)))

        val row = document.getValue("evaluations").jsonArray.single().jsonObject
        assertEquals("RE_ARMED", row.getValue("verdict").jsonPrimitive.content)
        assertEquals(JsonNull, row.getValue("skipReason"))
    }

    @Test
    fun `given a failure outcome when rendered then the error message is carried`() {
        val document = render(
            listOf(
                evaluation(
                    id = "f",
                    verdict = TriggerEvaluationVerdict.Fired,
                    runId = "run-f",
                    outcome = TriggerRunOutcome.Failure("network down"),
                ),
            ),
        )

        val row = document.getValue("evaluations").jsonArray.single().jsonObject
        assertEquals("FAILURE", row.getValue("outcome").jsonPrimitive.content)
        assertEquals("network down", row.getValue("outcomeError").jsonPrimitive.content)
    }

    @Test
    fun `given every run outcome kind when rendered then each maps to its discriminator`() {
        val cases = mapOf(
            TriggerRunOutcome.Success to "SUCCESS",
            TriggerRunOutcome.Failure("x") to "FAILURE",
            TriggerRunOutcome.CancelledBySystem to "CANCELLED_BY_SYSTEM",
            TriggerRunOutcome.Cancelled to "CANCELLED",
            TriggerRunOutcome.HitlTimeout to "HITL_TIMEOUT",
        )
        val evaluations = cases.keys.mapIndexed { index, outcome ->
            evaluation(
                id = "run-$index",
                evaluatedAt = index.toLong(),
                verdict = TriggerEvaluationVerdict.Fired,
                runId = "run-$index",
                outcome = outcome,
            )
        }

        val rows = render(evaluations).getValue("evaluations").jsonArray
        val rendered = rows.map { it.jsonObject.getValue("outcome").jsonPrimitive.content }
        assertEquals(cases.values.toList(), rendered)
    }

    @Test
    fun `given a fired run still pending when rendered then the outcome is null`() {
        val document = render(
            listOf(evaluation(id = "p", verdict = TriggerEvaluationVerdict.Fired, runId = "run-p", outcome = null)),
        )

        val row = document.getValue("evaluations").jsonArray.single().jsonObject
        assertEquals(JsonNull, row.getValue("outcome"))
        assertEquals(JsonNull, row.getValue("outcomeError"))
    }

    @Test
    fun `given several evaluations when rendered then the caller order is preserved and counted`() {
        val evaluations = listOf(
            evaluation(id = "third", evaluatedAt = 3),
            evaluation(id = "second", evaluatedAt = 2),
            evaluation(id = "first", evaluatedAt = 1),
        )

        val document = render(evaluations)

        assertEquals(3, document.getValue("totalEvaluations").jsonPrimitive.int)
        val ids = document.getValue("evaluations").jsonArray.map { it.jsonObject.getValue("id").jsonPrimitive.content }
        assertEquals(listOf("third", "second", "first"), ids)
    }
}
