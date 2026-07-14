package app.knotwork.android.domain.constants

import app.knotwork.android.domain.models.EntrySurface
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Pins the onboarding scenario wiring: the set of scenarios, their preset /
 * model / surface mapping, gallery order, and the [OnboardingScenarioCatalog.byId]
 * lookup. Keeps the domain recipe honest against the design-system enum and the
 * bundled preset ids.
 */
class OnboardingScenarioCatalogTest {

    @Test
    fun `catalogue exposes exactly the three curated scenarios in gallery order`() {
        assertEquals(
            listOf(
                OnboardingScenarioCatalog.ID_STYLED_TRANSLATION,
                OnboardingScenarioCatalog.ID_SHARE_HANDLER,
                OnboardingScenarioCatalog.ID_VIRTUAL_COMPANION,
            ),
            OnboardingScenarioCatalog.SCENARIOS.map { it.id },
        )
    }

    @Test
    fun `preset id equals scenario id for every scenario`() {
        OnboardingScenarioCatalog.SCENARIOS.forEach { spec ->
            assertEquals(spec.id, spec.presetId)
        }
    }

    @Test
    fun `styled translation needs E2B and binds no surface`() {
        val spec = OnboardingScenarioCatalog.byId(OnboardingScenarioCatalog.ID_STYLED_TRANSLATION)!!
        assertEquals(OnboardingModelCatalog.ID_GEMMA_4_E2B, spec.modelId)
        assertNull(spec.entrySurface)
    }

    @Test
    fun `share handler needs E2B and binds the share surface`() {
        val spec = OnboardingScenarioCatalog.byId(OnboardingScenarioCatalog.ID_SHARE_HANDLER)!!
        assertEquals(OnboardingModelCatalog.ID_GEMMA_4_E2B, spec.modelId)
        assertEquals(EntrySurface.SHARE, spec.entrySurface)
    }

    @Test
    fun `virtual companion needs E4B and binds no surface`() {
        val spec = OnboardingScenarioCatalog.byId(OnboardingScenarioCatalog.ID_VIRTUAL_COMPANION)!!
        assertEquals(OnboardingModelCatalog.ID_GEMMA_4_E4B, spec.modelId)
        assertNull(spec.entrySurface)
    }

    @Test
    fun `byId returns null for an unknown scenario`() {
        assertNull(OnboardingScenarioCatalog.byId("nope"))
    }
}
