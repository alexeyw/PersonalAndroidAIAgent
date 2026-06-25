package app.knotwork.android.domain.usecases

import app.knotwork.android.domain.models.PipelineRunStatus
import app.knotwork.android.domain.models.PipelineRunTally
import app.knotwork.android.domain.models.UsageTelemetrySummary
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Verifies that [BuildUsageTelemetryExportUseCase] renders the on-device
 * statistics into a correct text + JSON document, resolves pipeline names, and
 * carries the local-only marker.
 */
class BuildUsageTelemetryExportUseCaseTest {

    private val useCase = BuildUsageTelemetryExportUseCase()

    private val populated = UsageTelemetrySummary(
        runsByPipeline = listOf(
            PipelineRunTally("pipe-1", 9),
            PipelineRunTally(null, 1),
        ),
        runsByOutcome = mapOf(
            PipelineRunStatus.COMPLETED to 8,
            PipelineRunStatus.FAILED to 2,
        ),
        triggerFiresByKind = mapOf("CHARGING" to 3, "INTERVAL" to 1),
        activeDays = 4,
        firstActiveDay = "2026-06-20",
        lastActiveDay = "2026-06-25",
    )

    @Test
    fun `given populated summary when rendered to text then it carries the on-device guarantee and figures`() {
        val export = useCase(populated, mapOf("pipe-1" to "Daily digest"), "25 Jun 2026 14:30")

        assertTrue(export.text.contains("stored only on this device"))
        assertTrue(export.text.contains("Runs: 10 total"))
        assertTrue(export.text.contains("COMPLETED: 8 (80%)"))
        // Resolved + unknown pipeline labels.
        assertTrue(export.text.contains("Daily digest: 9"))
        assertTrue(export.text.contains("Unknown pipeline: 1"))
        assertTrue(export.text.contains("CHARGING: 3"))
        assertTrue(export.text.contains("Active days: 4"))
    }

    @Test
    fun `given populated summary when rendered to JSON then the document is well-formed`() {
        val export = useCase(populated, mapOf("pipe-1" to "Daily digest"), "25 Jun 2026 14:30")

        val root = Json.parseToJsonElement(export.json).jsonObject
        assertEquals(true, root["localOnly"]!!.jsonPrimitive.content.toBoolean())
        assertEquals(10, root["totalRuns"]!!.jsonPrimitive.int)
        // All four terminal outcomes present, missing ones zero-filled.
        val outcomes = root["runsByOutcome"]!!.jsonObject
        assertEquals(8, outcomes["COMPLETED"]!!.jsonPrimitive.int)
        assertEquals(0, outcomes["CANCELLED"]!!.jsonPrimitive.int)
        // Pipeline rows preserve order + resolved names.
        val pipelines = root["runsByPipeline"]!!.jsonArray
        assertEquals("Daily digest", pipelines[0].jsonObject["name"]!!.jsonPrimitive.content)
        assertEquals("Unknown pipeline", pipelines[1].jsonObject["name"]!!.jsonPrimitive.content)
        assertEquals(4, root["activeDays"]!!.jsonPrimitive.int)
    }

    @Test
    fun `given a fractional outcome share when rendered then the percentage is rounded not truncated`() {
        // 1 of 8 = 12.5% → rounds to 13% (truncation would have shown 12%).
        val summary = UsageTelemetrySummary(
            runsByPipeline = emptyList(),
            runsByOutcome = mapOf(PipelineRunStatus.COMPLETED to 1, PipelineRunStatus.FAILED to 7),
            triggerFiresByKind = emptyMap(),
            activeDays = 1,
            firstActiveDay = "2026-06-25",
            lastActiveDay = "2026-06-25",
        )

        val export = useCase(summary, emptyMap(), "25 Jun 2026 14:30")

        assertTrue(export.text.contains("COMPLETED: 1 (13%)"))
    }

    @Test
    fun `given an empty summary when rendered then text and JSON both reflect zero usage`() {
        val export = useCase(UsageTelemetrySummary.EMPTY, emptyMap(), "25 Jun 2026 14:30")

        assertTrue(export.text.contains("Runs: 0 total"))
        assertTrue(export.text.contains("(none)"))

        val root = Json.parseToJsonElement(export.json).jsonObject
        assertEquals(0, root["totalRuns"]!!.jsonPrimitive.int)
        assertTrue(root["runsByPipeline"]!!.jsonArray.isEmpty())
        assertTrue(root["triggerFiresByKind"]!!.jsonArray.isEmpty())
    }
}
