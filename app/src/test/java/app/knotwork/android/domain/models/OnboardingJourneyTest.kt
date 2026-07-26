package app.knotwork.android.domain.models

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Verifies the derived figures and first-value attribution rules of
 * [OnboardingJourney] — the pure half of the repeatable "< 10 minutes to first
 * value" measurement.
 */
class OnboardingJourneyTest {

    private val start = 1_700_000_000_000L

    private fun journey(
        vararg markers: Pair<OnboardingMilestone, Long>,
        scenarioPipelineId: String? = null,
    ): OnboardingJourney = OnboardingJourney(milestones = markers.toMap(), scenarioPipelineId = scenarioPipelineId)

    @Test
    fun `given a full journey when reading durations then the download is split out of the total`() {
        val subject = journey(
            OnboardingMilestone.ONBOARDING_STARTED to start,
            OnboardingMilestone.MODEL_DOWNLOAD_STARTED to start + 30_000L,
            OnboardingMilestone.MODEL_DOWNLOAD_FINISHED to start + 230_000L,
            OnboardingMilestone.FIRST_VALUE to start + 300_000L,
        )

        assertEquals(300_000L, subject.totalToValueMillis)
        assertEquals(200_000L, subject.modelDownloadMillis)
        // The product-owned figure: total minus the network-bound download.
        assertEquals(100_000L, subject.productToValueMillis)
    }

    @Test
    fun `given no download markers when reading durations then product time equals the total`() {
        // The picked model was already installed, so no download happened.
        val subject = journey(
            OnboardingMilestone.ONBOARDING_STARTED to start,
            OnboardingMilestone.FIRST_VALUE to start + 45_000L,
        )

        assertEquals(45_000L, subject.totalToValueMillis)
        assertNull(subject.modelDownloadMillis)
        assertEquals(45_000L, subject.productToValueMillis)
    }

    @Test
    fun `given an unfinished journey when reading durations then they are unmeasured not zero`() {
        val subject = journey(
            OnboardingMilestone.ONBOARDING_STARTED to start,
            OnboardingMilestone.MODEL_DOWNLOAD_STARTED to start + 30_000L,
        )

        assertNull(subject.totalToValueMillis)
        assertNull(subject.modelDownloadMillis)
        assertNull(subject.productToValueMillis)
        assertFalse(subject.isEmpty)
    }

    @Test
    fun `given a clock adjustment inverting two markers when reading durations then they clamp at zero`() {
        val subject = journey(
            OnboardingMilestone.ONBOARDING_STARTED to start,
            OnboardingMilestone.FIRST_VALUE to start - 10_000L,
        )

        assertEquals(0L, subject.totalToValueMillis)
        assertEquals(0L, subject.productToValueMillis)
    }

    @Test
    fun `given a download longer than the total when reading product time then it clamps at zero`() {
        // Defensive: a clock jump could make the download interval exceed the
        // whole journey; the remainder must never go negative.
        val subject = journey(
            OnboardingMilestone.ONBOARDING_STARTED to start,
            OnboardingMilestone.MODEL_DOWNLOAD_STARTED to start,
            OnboardingMilestone.MODEL_DOWNLOAD_FINISHED to start + 500_000L,
            OnboardingMilestone.FIRST_VALUE to start + 100_000L,
        )

        assertEquals(0L, subject.productToValueMillis)
    }

    @Test
    fun `given nothing recorded when reading the journey then it is empty`() {
        assertTrue(OnboardingJourney.EMPTY.isEmpty)
        assertNull(OnboardingJourney.EMPTY.totalToValueMillis)
        assertNull(OnboardingJourney.EMPTY.scenarioPipelineId)
    }

    @Test
    fun `given a scenario journey when its pipeline completes then first value is accepted`() {
        val subject = journey(
            OnboardingMilestone.ONBOARDING_STARTED to start,
            OnboardingMilestone.SCENARIO_CHOSEN to start + 1_000L,
            scenarioPipelineId = "pipe-1",
        )

        assertTrue(subject.acceptsFirstValueFrom("pipe-1"))
    }

    @Test
    fun `given a scenario journey when a different pipeline completes then first value is rejected`() {
        val subject = journey(
            OnboardingMilestone.ONBOARDING_STARTED to start,
            OnboardingMilestone.SCENARIO_CHOSEN to start + 1_000L,
            scenarioPipelineId = "pipe-1",
        )

        // A run of the seeded showcase default is not the value the scenario promised.
        assertFalse(subject.acceptsFirstValueFrom("pipe-2"))
    }

    @Test
    fun `given no scenario was set up when any pipeline completes then first value is accepted`() {
        // Skip / start-from-scratch path: the metric still needs an end point.
        val subject = journey(OnboardingMilestone.ONBOARDING_STARTED to start)

        assertTrue(subject.acceptsFirstValueFrom("pipe-anything"))
    }

    @Test
    fun `given first value already recorded when another run completes then it is rejected`() {
        val subject = journey(
            OnboardingMilestone.ONBOARDING_STARTED to start,
            OnboardingMilestone.FIRST_VALUE to start + 60_000L,
        )

        assertFalse(subject.acceptsFirstValueFrom("pipe-1"))
    }

    @Test
    fun `given onboarding never started when a run completes then there is no journey to close`() {
        // Upgrading installs (markers added after their onboarding) must not get
        // a phantom first value with no start to measure from.
        assertFalse(OnboardingJourney.EMPTY.acceptsFirstValueFrom("pipe-1"))
    }
}
