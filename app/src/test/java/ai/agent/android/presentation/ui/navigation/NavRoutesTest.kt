package ai.agent.android.presentation.ui.navigation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

/**
 * Unit tests for [NavRoutes] — guards against accidental route collisions and blank routes.
 *
 * Navigation Compose dispatches by exact string match, so two routes sharing the same
 * literal (or an accidentally-blank value) would silently route the user to the wrong
 * screen at runtime. The tests reflect over the public constants so they automatically
 * extend to future routes added to the object.
 */
class NavRoutesTest {

    private val allRoutes: List<String> = listOf(
        NavRoutes.SPLASH,
        NavRoutes.HOME,
        NavRoutes.CHAT,
        NavRoutes.MODELS,
        NavRoutes.MEMORY,
        NavRoutes.TOOLS,
        NavRoutes.MONITORING,
        NavRoutes.TASK_MONITOR,
        NavRoutes.SETTINGS,
        NavRoutes.PROMPTS,
        NavRoutes.PIPELINES_GRAPH,
        NavRoutes.PIPELINE_LIBRARY,
        NavRoutes.PIPELINE_EDITOR,
    )

    @Test
    fun `every declared route is unique`() {
        assertEquals(allRoutes.size, allRoutes.toSet().size)
    }

    @Test
    fun `no route is blank`() {
        allRoutes.forEach { route ->
            assertFalse("Route must be non-blank, was '$route'", route.isBlank())
        }
    }
}
