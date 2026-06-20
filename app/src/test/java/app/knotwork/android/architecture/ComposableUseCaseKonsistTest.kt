package app.knotwork.android.architecture

import com.lemonappdev.konsist.api.verify.assertFalse
import org.junit.Test

/**
 * Konsist guard for the presentation rule "Composables observe a ViewModel /
 * UiState, never the use-case layer directly".
 *
 * **Deliberately narrow scope.** Konsist analyses declarations, not the data
 * flow inside a function body, so it cannot reliably prove that a `@Composable`
 * never *invokes* a use-case transitively. What it can express precisely is the
 * structural smell that always precedes such a call: a use-case handed to a
 * Composable as a parameter. This rule therefore asserts that **no `@Composable`
 * function takes a `*UseCase` parameter**; deeper "no direct invocation" intent
 * stays in item 1 of the manual review checklist, where this boundary is noted.
 */
class ComposableUseCaseKonsistTest {
    @Test
    fun `composables do not receive use-cases as parameters`() {
        ArchitectureScope.production
            .functions()
            .filter { function -> function.annotations.any { it.name == "Composable" } }
            .assertFalse(additionalMessage = COMPOSABLE_USECASE_FAILURE) { function ->
                function.parameters.any { parameter ->
                    parameter.type.name.endsWith("UseCase")
                }
            }
    }

    private companion object {
        const val COMPOSABLE_USECASE_FAILURE =
            "a @Composable must not receive a *UseCase parameter; expose state/lambdas via a ViewModel instead"
    }
}
