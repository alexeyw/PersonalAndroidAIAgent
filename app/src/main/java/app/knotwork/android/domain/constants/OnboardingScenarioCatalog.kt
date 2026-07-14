package app.knotwork.android.domain.constants

import app.knotwork.android.domain.models.EntrySurface

/**
 * Canonical wiring for the onboarding value-gallery scenarios.
 *
 * Each scenario the user can pick on the "Choose a scenario" step maps to a
 * concrete set-up recipe:
 *  - the bundled preset that is materialised as the user's default pipeline
 *    (`assets/presets/pipelines/<presetId>.json`, keyed by its stable slug id);
 *  - the OS [EntrySurface] the scenario binds, or `null` when it runs purely
 *    from the chat surface;
 *  - the LiteRT model id the scenario needs, so the motivated-download step
 *    can pre-select and frame the fetch ("Styled Translation needs Gemma 4 E2B").
 *
 * This object is the single source of truth on the domain side, mirroring how
 * [OnboardingModelCatalog] keys download metadata by [OnboardingModelCatalog.Entry.id].
 * The design-system layer keeps its own `OnboardingScenario` enum for rendering
 * (illustration tile, value copy, featured treatment); the two stay decoupled
 * and are joined by the stable [Spec.id] string — exactly the split used for
 * the model picker (`OnboardingLiteRtModel` ↔ `OnboardingModelCatalog`).
 *
 * Used by [app.knotwork.android.domain.usecases.SetUpScenarioUseCase] to resolve
 * a picked scenario id into the preset / surface / model it materialises.
 */
object OnboardingScenarioCatalog {

    /** Stable id of the on-device styled-translation scenario (first / default card). */
    const val ID_STYLED_TRANSLATION: String = "styled_translation"

    /** Stable id of the share-capture scenario (binds the system Share sheet). */
    const val ID_SHARE_HANDLER: String = "share_handler"

    /** Stable id of the mood-routing virtual-companion scenario (featured card). */
    const val ID_VIRTUAL_COMPANION: String = "virtual_companion_mood_router"

    /**
     * The materialisation recipe for a single onboarding scenario.
     *
     * @property id stable scenario id (matches the design-system
     *   `OnboardingScenario.id` and the bundled preset filename stem).
     * @property presetId id of the bundled preset materialised as the default
     *   pipeline. Identical to [id] today — kept as a distinct property so a
     *   scenario could later point at a differently-named preset without
     *   changing the UI-facing scenario key.
     * @property entrySurface OS surface the scenario binds to the materialised
     *   pipeline, or `null` when the scenario is driven only from chat.
     * @property modelId catalog id of the LiteRT model the scenario needs
     *   (matches [OnboardingModelCatalog] / `OnboardingLiteRtModel.id`). Drives
     *   the pre-selection on the motivated-download step.
     */
    data class Spec(val id: String, val presetId: String, val entrySurface: EntrySurface?, val modelId: String)

    /**
     * Every onboarding scenario recipe, in gallery order. Virtual Companion is
     * last (featured but never the first impression — privacy/identity
     * guardrail, VISION §2).
     */
    val SCENARIOS: List<Spec> = listOf(
        Spec(
            id = ID_STYLED_TRANSLATION,
            presetId = ID_STYLED_TRANSLATION,
            entrySurface = null,
            modelId = OnboardingModelCatalog.ID_GEMMA_4_E2B,
        ),
        Spec(
            id = ID_SHARE_HANDLER,
            presetId = ID_SHARE_HANDLER,
            entrySurface = EntrySurface.SHARE,
            modelId = OnboardingModelCatalog.ID_GEMMA_4_E2B,
        ),
        Spec(
            id = ID_VIRTUAL_COMPANION,
            presetId = ID_VIRTUAL_COMPANION,
            entrySurface = null,
            modelId = OnboardingModelCatalog.ID_GEMMA_4_E4B,
        ),
    )

    /** Returns the recipe for [scenarioId], or `null` for an unknown id. */
    fun byId(scenarioId: String): Spec? = SCENARIOS.firstOrNull { it.id == scenarioId }
}
