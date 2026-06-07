package app.knotwork.android.presentation.ui.navigation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

/**
 * Unit tests for [NavRoutes] — guards against accidental route collisions,
 * blank routes, and silently broken deep-link / argument contracts.
 *
 * Navigation Compose dispatches by exact string match, so two routes
 * sharing the same literal (or an accidentally-blank value) would silently
 * route the user to the wrong screen at runtime.
 */
class NavRoutesTest {

    private val allRoutes: List<String> = listOf(
        NavRoutes.SPLASH,
        NavRoutes.ONBOARDING,
        NavRoutes.CHAT_TAB,
        NavRoutes.CHAT_WITH_THREAD,
        NavRoutes.MODELS,
        NavRoutes.MEMORY,
        NavRoutes.TOOLS,
        NavRoutes.TOOL_DETAIL,
        NavRoutes.MCP_SERVER_CONFIG,
        NavRoutes.MCP_SERVER_CONFIG_ADD,
        NavRoutes.MONITORING,
        NavRoutes.TASK_MONITOR,
        NavRoutes.SETTINGS,
        NavRoutes.PROMPTS,
        NavRoutes.ABOUT,
        NavRoutes.MORE,
        NavRoutes.PIPELINES_GRAPH,
        NavRoutes.PIPELINE_LIBRARY,
        NavRoutes.PIPELINE_EDITOR,
        NavRoutes.PIPELINE_EDIT_WITH_ID,
        NavRoutes.SHEET_NODE_CONFIG,
        NavRoutes.SHEET_CONSOLE,
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

    @Test
    fun `chat deep link uri pattern matches the scheme contract`() {
        // External consumers (other apps, Adb intents, future docs) depend
        // on the exact `knotwork://chat/{threadId}` pattern — bumping the
        // scheme or path is a breaking change.
        assertEquals("knotwork", NavRoutes.DEEP_LINK_SCHEME)
        assertEquals("knotwork://chat/{threadId}", NavRoutes.CHAT_DEEP_LINK_PATTERN)
    }

    @Test
    fun `chatRoute concatenates path argument`() {
        assertEquals("chat/thread-42", NavRoutes.chatRoute("thread-42"))
    }

    @Test
    fun `parameterised chat and pipeline routes expose stable argument keys`() {
        assertEquals("threadId", NavRoutes.CHAT_THREAD_ARG)
        assertEquals("chat/{threadId}", NavRoutes.CHAT_WITH_THREAD)
        assertEquals("id", NavRoutes.PIPELINE_EDIT_ID_ARG)
        assertEquals("pipeline/{id}/edit", NavRoutes.PIPELINE_EDIT_WITH_ID)
    }
}
