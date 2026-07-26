package app.knotwork.android.domain.usecases

import app.knotwork.android.domain.models.OnboardingJourney
import app.knotwork.android.domain.models.OnboardingMilestone
import app.knotwork.android.domain.models.PipelineRunStatus
import app.knotwork.android.domain.models.PipelineRunTally
import app.knotwork.android.domain.models.UsageTelemetrySummary
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
        onboarding = OnboardingJourney(
            milestones = mapOf(
                // 09:00:00 → 09:07:24 total; the download spans 09:01:00 → 09:06:00
                // (5 min), leaving 2 min 24 s of product time.
                OnboardingMilestone.ONBOARDING_STARTED to 1_750_000_000_000L,
                OnboardingMilestone.MODEL_DOWNLOAD_STARTED to 1_750_000_060_000L,
                OnboardingMilestone.MODEL_DOWNLOAD_FINISHED to 1_750_000_360_000L,
                OnboardingMilestone.FIRST_VALUE to 1_750_000_444_000L,
            ),
            scenarioPipelineId = "pipe-1",
        ),
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
            onboarding = OnboardingJourney.EMPTY,
        )

        val export = useCase(summary, emptyMap(), "25 Jun 2026 14:30")

        assertTrue(export.text.contains("COMPLETED: 1 (13%)"))
    }

    @Test
    fun `given a recorded onboarding journey when rendered to text then all three durations appear`() {
        val export = useCase(populated, emptyMap(), "25 Jun 2026 14:30")

        assertTrue(export.text.contains("Time to first value: 7m 24s"))
        assertTrue(export.text.contains("Model download: 5m 0s"))
        assertTrue(export.text.contains("Time to first value excluding download: 2m 24s"))
    }

    @Test
    fun `given a recorded onboarding journey when rendered to JSON then durations and raw markers are exported`() {
        val export = useCase(populated, emptyMap(), "25 Jun 2026 14:30")

        val onboarding = Json.parseToJsonElement(export.json).jsonObject["onboarding"]!!.jsonObject
        assertEquals(true, onboarding["recorded"]!!.jsonPrimitive.content.toBoolean())
        assertEquals(444_000L, onboarding["totalToValueMillis"]!!.jsonPrimitive.long)
        assertEquals(300_000L, onboarding["modelDownloadMillis"]!!.jsonPrimitive.long)
        assertEquals(144_000L, onboarding["productToValueMillis"]!!.jsonPrimitive.long)
        // Raw markers travel too, so an external analysis can re-derive the figures.
        val milestones = onboarding["milestones"]!!.jsonObject
        assertEquals(1_750_000_000_000L, milestones["ONBOARDING_STARTED"]!!.jsonPrimitive.long)
        // A marker that was never reached is exported as an explicit null.
        assertTrue(milestones["SCENARIO_CHOSEN"] is JsonNull)
    }

    @Test
    fun `given a journey without a download when rendered then the download reads as unmeasured`() {
        val summary = populated.copy(
            onboarding = OnboardingJourney(
                milestones = mapOf(
                    OnboardingMilestone.ONBOARDING_STARTED to 1_750_000_000_000L,
                    OnboardingMilestone.FIRST_VALUE to 1_750_000_045_000L,
                ),
                scenarioPipelineId = null,
            ),
        )

        val export = useCase(summary, emptyMap(), "25 Jun 2026 14:30")

        // Under a minute renders without a minutes part; the absent download is an
        // em-dash, not a zero, and the product figure equals the total.
        assertTrue(export.text.contains("Time to first value: 45s"))
        assertTrue(export.text.contains("Model download: —"))
        assertTrue(export.text.contains("Time to first value excluding download: 45s"))
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
