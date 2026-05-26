package ai.agent.android.presentation.ui.navigation

import ai.agent.android.presentation.state.TransientMessageRelay
import android.net.Uri
import androidx.compose.material3.Text
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import androidx.navigation.navDeepLink
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

/**
 * Compose-level integration tests for [AppShellScaffold] + an in-memory
 * mini nav-graph.
 *
 * These tests stand up [AppShellScaffold] with a minimal `NavHost` whose
 * destinations render plain text — enough to assert navigation behaviour
 * (tab selection, deep-link routing, back-stack containment) without
 * standing up the full Hilt graph. The real screens (`ChatScreen`, etc.)
 * are exercised by their own per-screen tests; here we are only
 * verifying the shell's contract.
 */
class AppShellNavigationTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun bottomNavTapSwitchesToTargetTab() {
        composeTestRule.setContent {
            val navController = rememberNavController()
            AppShellScaffold(navController = navController, transientMessageRelay = TransientMessageRelay()) { _ ->
                NavHost(navController = navController, startDestination = NavRoutes.CHAT_TAB) {
                    composable(NavRoutes.CHAT_TAB) { Text(CHAT_TAB_BODY) }
                    composable(NavRoutes.PIPELINES_GRAPH) { Text(PIPELINES_BODY) }
                    composable(NavRoutes.TOOLS) { Text(TOOLS_BODY) }
                    composable(NavRoutes.MORE) { Text(MORE_BODY) }
                }
            }
        }

        composeTestRule.onNodeWithText(CHAT_TAB_BODY).assertIsDisplayed()

        composeTestRule.onNodeWithText("Tools").performClick()
        composeTestRule.onNodeWithText(TOOLS_BODY).assertIsDisplayed()

        composeTestRule.onNodeWithText("Pipelines").performClick()
        composeTestRule.onNodeWithText(PIPELINES_BODY).assertIsDisplayed()
    }

    /**
     * Regression: tab switches must pop prior tab entries instead of
     * stacking them.
     *
     * Earlier versions used `popUpTo(graph.findStartDestination().id)` —
     * which resolves to the splash route, the very route the post-init
     * handler removes from the back-stack with `inclusive = true`. With
     * that target gone, `popUpTo` silently no-oped and every tap added
     * a fresh entry on top of the stack. This test reconstructs the same
     * `splash → CHAT_TAB` start sequence the real graph uses, performs a
     * four-tab dance, and asserts that only the Chat anchor + the
     * current tab remain on the back-stack.
     */
    @Test
    fun tabSwitchesKeepBackStackBoundedToAnchorPlusCurrentTab() {
        lateinit var navController: NavHostController
        composeTestRule.setContent {
            val nav = rememberNavController()
            navController = nav
            AppShellScaffold(navController = nav, transientMessageRelay = TransientMessageRelay()) { _ ->
                NavHost(navController = nav, startDestination = NavRoutes.SPLASH) {
                    composable(NavRoutes.SPLASH) {
                        LaunchedEffect(Unit) {
                            nav.navigate(NavRoutes.CHAT_TAB) {
                                popUpTo(NavRoutes.SPLASH) { inclusive = true }
                                launchSingleTop = true
                            }
                        }
                    }
                    composable(NavRoutes.CHAT_TAB) { Text(CHAT_TAB_BODY) }
                    composable(NavRoutes.PIPELINES_GRAPH) { Text(PIPELINES_BODY) }
                    composable(NavRoutes.TOOLS) { Text(TOOLS_BODY) }
                    composable(NavRoutes.MORE) { Text(MORE_BODY) }
                }
            }
        }

        composeTestRule.onNodeWithText(CHAT_TAB_BODY).assertIsDisplayed()

        // Four-tab dance: any combination of tabs that revisits the start
        // tab last should collapse the back-stack down to the anchor.
        composeTestRule.onNodeWithText("Tools").performClick()
        composeTestRule.onNodeWithText("Pipelines").performClick()
        composeTestRule.onNodeWithText("More").performClick()
        composeTestRule.onNodeWithText(MORE_BODY).assertIsDisplayed()

        // After landing on a non-anchor tab, the only entry below it
        // must be the Chat anchor — never one of the previously-visited
        // intermediate tabs.
        composeTestRule.runOnIdle {
            val tabsOnStack = navController.tabRoutesOnStack()
            assertEquals(listOf(NavRoutes.CHAT_TAB, NavRoutes.MORE), tabsOnStack)
        }

        // Tapping the anchor tab pops everything above it; only the
        // anchor remains.
        composeTestRule.onNodeWithText("Chat").performClick()
        composeTestRule.onNodeWithText(CHAT_TAB_BODY).assertIsDisplayed()
        composeTestRule.runOnIdle {
            val tabsOnStack = navController.tabRoutesOnStack()
            assertEquals(listOf(NavRoutes.CHAT_TAB), tabsOnStack)
        }
    }

    @Test
    fun deepLinkRoutesToChatThreadDestination() {
        // We fire the deep-link from inside the composition through a
        // captured NavController. This bypasses the system intent
        // pipeline (that would require a `createAndroidComposeRule` host
        // with the real MainActivity) while still exercising the
        // navDeepLink matching logic, which is the unit under test.
        composeTestRule.setContent {
            val navController = rememberNavController()
            LaunchedEffect(navController) {
                navController.navigate(
                    Uri.parse("${NavRoutes.DEEP_LINK_SCHEME}://chat/thread-42"),
                )
            }
            AppShellScaffold(navController = navController, transientMessageRelay = TransientMessageRelay()) { _ ->
                NavHost(navController = navController, startDestination = NavRoutes.CHAT_TAB) {
                    composable(NavRoutes.CHAT_TAB) { Text(CHAT_TAB_BODY) }
                    composable(
                        route = NavRoutes.CHAT_WITH_THREAD,
                        arguments = listOf(
                            navArgument(NavRoutes.CHAT_THREAD_ARG) {
                                type = NavType.StringType
                                nullable = false
                            },
                        ),
                        deepLinks = listOf(
                            navDeepLink { uriPattern = NavRoutes.CHAT_DEEP_LINK_PATTERN },
                        ),
                    ) { entry ->
                        val threadId = entry.arguments?.getString(NavRoutes.CHAT_THREAD_ARG)
                        Text("chat-thread:$threadId")
                    }
                }
            }
        }

        composeTestRule.onNodeWithText("chat-thread:thread-42").assertIsDisplayed()
    }

    /** Returns the subset of the back-stack that corresponds to top-level tab routes. */
    private fun NavHostController.tabRoutesOnStack(): List<String> {
        val tabRoutes = TAB_DESTINATIONS.map { it.route }.toSet()
        return currentBackStack.value
            .mapNotNull { it.destination.route }
            .filter { it in tabRoutes }
    }

    private companion object {
        const val CHAT_TAB_BODY = "chat-tab-root"
        const val PIPELINES_BODY = "pipelines-root"
        const val TOOLS_BODY = "tools-root"
        const val MORE_BODY = "more-root"
    }
}
