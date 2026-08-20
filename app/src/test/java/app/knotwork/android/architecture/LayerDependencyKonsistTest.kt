package app.knotwork.android.architecture

import com.lemonappdev.konsist.api.architecture.KoArchitectureCreator.assertArchitecture
import com.lemonappdev.konsist.api.architecture.Layer
import org.junit.Test

/**
 * Konsist architecture guard enforcing the project's Clean Architecture
 * dependency rule: dependencies flow strictly inward,
 * `data` -> `domain` <- `presentation`, and `domain` depends on neither sibling.
 *
 * This is the mechanical counterpart to item 1 ("Architecture") of the manual
 * review checklist. It exists specifically to catch the class of regression
 * that previously slipped through human review: a `domain` declaration reaching
 * into the `data` or `presentation` layer (the `ScheduleTaskUseCase`
 * Clean-Architecture leak fixed earlier in this phase). `domain.dependsOnNothing()`
 * fails the build the moment any `..domain..` declaration imports a
 * `..data..` or `..presentation..` symbol again.
 *
 * The scope is restricted to the production source set via
 * [ArchitectureScope]; test sources legitimately cross layer boundaries (a
 * `domain` use-case test may construct `data` fakes) and must not be
 * constrained by this rule. The dependency-injection wiring
 * (`app.knotwork.android.di`) and the application entry point (`App.kt`) are
 * deliberately not declared as layers: composition roots are allowed to
 * reference every layer.
 */
class LayerDependencyKonsistTest {
    @Test
    fun `clean architecture layers have correct inward dependencies`() {
        ArchitectureScope.production
            .assertArchitecture {
                val domain = Layer("Domain", "app.knotwork.android.domain..")
                val data = Layer("Data", "app.knotwork.android.data..")
                val presentation = Layer("Presentation", "app.knotwork.android.presentation..")

                domain.dependsOnNothing()
                data.dependsOn(domain)
                presentation.dependsOn(domain)
            }
    }

    /**
     * `data` must not reach up into `presentation`.
     *
     * The assertion above is **permissive**: `dependsOn` says a layer *may*
     * depend on another and says nothing about the ones it may not. Only
     * `dependsOnNothing()` is exclusive, and it applies to `domain` alone — so
     * this direction was unguarded, and a foreground service duly imported a
     * presentation mapper to render a status line. Nothing failed. This test is
     * what would have failed.
     *
     * The mirror assertion, `presentation.doesNotDependOn(data)`, is
     * **deliberately absent**: eight production files already cross that way
     * (`MainActivity`, `ModelsViewModel`, `ExternalAutomationReceiver` and
     * others reaching for schedulers and download managers). That is a real
     * debt, but it is long-standing rather than a regression, and asserting it
     * here would fail the build on code this change never touched. Raised for
     * the phase's bug-fix container instead — adding the assertion is a
     * one-line change once the eight are moved behind domain ports.
     */
    @Test
    fun `data does not depend on presentation`() {
        ArchitectureScope.production
            .assertArchitecture {
                val data = Layer("Data", "app.knotwork.android.data..")
                val presentation = Layer("Presentation", "app.knotwork.android.presentation..")

                data.doesNotDependOn(presentation)
            }
    }
}
