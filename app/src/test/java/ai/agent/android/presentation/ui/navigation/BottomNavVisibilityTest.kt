package ai.agent.android.presentation.ui.navigation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

/**
 * Table-driven test for [shouldShowBottomNav].
 *
 * The visibility rule is the canonical source of truth for what counts as
 * "inside the bottom-nav structure" vs. "full-screen focus mode" — every
 * call site in the app code routes through this function. Locking the
 * table down with a unit test prevents accidental regressions when new
 * routes are added.
 */
class BottomNavVisibilityTest {

    @Test
    fun `null route hides the bar`() {
        assertFalse(shouldShowBottomNav(null))
    }

    @Test
    fun `splash and onboarding hide the bar`() {
        assertExpectations(
            NavRoutes.SPLASH to false,
            NavRoutes.ONBOARDING to false,
        )
    }

    @Test
    fun `top-level tab routes show the bar`() {
        assertExpectations(
            NavRoutes.CHAT_TAB to true,
            NavRoutes.PIPELINES_GRAPH to true,
            NavRoutes.TOOLS to true,
            NavRoutes.MORE to true,
        )
    }

    @Test
    fun `secondary screens under more show the bar`() {
        assertExpectations(
            NavRoutes.MEMORY to true,
            NavRoutes.MODELS to true,
            NavRoutes.PROMPTS to true,
            NavRoutes.SETTINGS to true,
            NavRoutes.TASK_MONITOR to true,
            NavRoutes.MONITORING to true,
            NavRoutes.ABOUT to true,
        )
    }

    @Test
    fun `pipeline editor hides the bar to free the canvas`() {
        assertExpectations(
            NavRoutes.PIPELINE_EDITOR to false,
            NavRoutes.PIPELINE_EDIT_WITH_ID to false,
        )
    }

    @Test
    fun `pipeline library inside the pipelines tab shows the bar`() {
        assertEquals(true, shouldShowBottomNav(NavRoutes.PIPELINE_LIBRARY))
    }

    @Test
    fun `tools deep screens show the bar`() {
        assertExpectations(
            NavRoutes.TOOL_DETAIL to true,
            NavRoutes.ADD_MCP_SERVER to true,
        )
    }

    @Test
    fun `modal sheet routes hide the bar`() {
        assertExpectations(
            NavRoutes.SHEET_NODE_CONFIG to false,
            NavRoutes.SHEET_CONSOLE to false,
            // Any other future `sheet/...` route is covered by the prefix check.
            "sheet/anything-else" to false,
        )
    }

    @Test
    fun `chat parameterised route shows the bar`() {
        // The deep-link route `chat/{threadId}` opens inside the Chat tab,
        // not as a focus-mode surface, so the bar must remain visible.
        assertEquals(true, shouldShowBottomNav(NavRoutes.CHAT_WITH_THREAD))
    }

    private fun assertExpectations(vararg expectations: Pair<String, Boolean>) {
        for ((route, expected) in expectations) {
            assertEquals(
                "route=$route expected=$expected",
                expected,
                shouldShowBottomNav(route),
            )
        }
    }
}
