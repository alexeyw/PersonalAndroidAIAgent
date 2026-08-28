package app.knotwork.android.presentation.ui.navigation

import android.content.Intent
import androidx.activity.ComponentActivity
import androidx.compose.material3.Text
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.core.net.toUri
import androidx.navigation.NavHostController
import androidx.navigation.compose.ComposeNavigator
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.navigation
import androidx.navigation.compose.rememberNavController
import androidx.navigation.get
import app.knotwork.android.presentation.state.ChatEntryRequest
import app.knotwork.android.presentation.state.ChatEntryRequestRelay
import app.knotwork.android.presentation.state.TransientMessageRelay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/**
 * Compose-level tests for the two navigation contracts this task established.
 *
 *  1. **A tab root is entered only as a tab switch.** Reaching one by pushing
 *     it on top of another subtree stranded the closed-test tester: the
 *     bottom-nav highlight jumped to a tab he had not chosen and the subtree he
 *     came from was left buried (`metrics.md §E #14`).
 *  2. **`back` is predictable in every affected branch.** Whatever the entry
 *     path, Back returns to the surface the user actually came from.
 *  3. **The notification deep link still lands on the single chat home.** The
 *     background HITL notification taps `knotwork://chat/{sessionId}`; the task
 *     requires that path to be held by a test rather than by inspection.
 *
 * The graph below is a stand-in for [AppNavGraph] — the real one needs the full
 * Hilt object graph — but it is deliberately shaped like it: the same route
 * constants, the same nested `navigation { }` graphs for Settings and
 * Pipelines, and the same [navigateToTab] / [navigateToDeepLink] entry points
 * the production graph calls. What is asserted is back-stack shape and chrome
 * state, both of which are properties of those entry points, not of the screens.
 */
class NavigationContractTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    private lateinit var navController: NavHostController
    private val chatEntryRelay = ChatEntryRequestRelay()

    // ─── Finding #14: the settings-owned tools surface ─────────────────────

    @Test
    fun settingsToolsManageKeepsTheSettingsBackStackAndTheMoreHighlight() {
        startShell()
        composeTestRule.onNodeWithText(MORE_TAB_LABEL).performClick()
        navigateTo(NavRoutes.SETTINGS)
        navigateTo(NavRoutes.SETTINGS_TOOLS)
        // The production wiring of `SettingsNavActions.onOpenManageTools`.
        navigateTo(NavRoutes.SETTINGS_TOOLS_MANAGE)

        composeTestRule.onNodeWithText(SETTINGS_TOOLS_MANAGE_BODY).assertIsDisplayed()

        // The settings subtree is intact underneath, and the surface on top is
        // the settings-owned twin — not the Tools tab root. Observed failing
        // against the pre-fix wiring: the stack ended in `tools`.
        composeTestRule.runOnIdle {
            assertEquals(
                listOf(
                    NavRoutes.CHAT_TAB,
                    NavRoutes.MORE,
                    NavRoutes.SETTINGS_HUB,
                    NavRoutes.SETTINGS_TOOLS,
                    NavRoutes.SETTINGS_TOOLS_MANAGE,
                ),
                navController.stackRoutes(),
            )
            // The tab the user chose is still the tab that is lit.
            assertEquals(NavRoutes.MORE, owningTabRoute(navController.stackRoutes()))
            // …and the shell must not think it is sitting on a tab root.
            assertFalse(isTabRootStack(navController.stackRoutes()))
        }
        composeTestRule.onNodeWithText(MORE_TAB_LABEL).assertIsSelected()
    }

    @Test
    fun backFromTheSettingsOwnedToolsSurfaceReturnsToItsCategory() {
        startShell()
        composeTestRule.onNodeWithText(MORE_TAB_LABEL).performClick()
        navigateTo(NavRoutes.SETTINGS)
        navigateTo(NavRoutes.SETTINGS_TOOLS)
        navigateTo(NavRoutes.SETTINGS_TOOLS_MANAGE)

        pressBack()

        // Pins the return contract the task asks for rather than a repaired
        // defect: re-running this against the pre-fix wiring *and* the pre-fix
        // Back predicate still passed, because NavHost's own Back handler is
        // registered after the shell's and consumes the press. Recorded here so
        // nobody re-derives that the hard way — and so the contract stops being
        // a property of registration order.
        composeTestRule.onNodeWithText(SETTINGS_TOOLS_BODY).assertIsDisplayed()
        composeTestRule.runOnIdle {
            assertEquals(NavRoutes.SETTINGS_TOOLS, navController.currentRoute())
            assertFalse("the activity must not have been finished", composeTestRule.activity.isFinishing)
        }
    }

    @Test
    fun tabSwitchFromADeepSubtreeCollapsesToTheAnchor() {
        startShell()
        composeTestRule.onNodeWithText(MORE_TAB_LABEL).performClick()
        navigateTo(NavRoutes.SETTINGS)
        navigateTo(NavRoutes.SETTINGS_TOOLS)
        navigateTo(NavRoutes.SETTINGS_TOOLS_MANAGE)

        // Closed testing reported three of the four bottom-bar buttons
        // responding and the Chat one not: every tab, the anchor one included,
        // must answer from a deep subtree.
        composeTestRule.onNodeWithText(CHAT_TAB_LABEL).performClick()

        composeTestRule.onNodeWithText(CHAT_BODY).assertIsDisplayed()
        composeTestRule.runOnIdle {
            assertEquals(listOf(NavRoutes.CHAT_TAB), navController.stackRoutes())
            assertTrue(isTabRootStack(navController.stackRoutes()))
        }
    }

    // ─── Tab ownership derived from the stack ──────────────────────────────

    @Test
    fun aScreenTheOldRouteTableNeverListedStillHighlightsItsTab() {
        // `tools/allowed-domains` was one of ten registered destinations missing
        // from the removed `belongsToTab` table: no tab lit up at all on them.
        startShell()
        composeTestRule.onNodeWithText(TOOLS_TAB_LABEL).performClick()
        navigateTo(NavRoutes.ALLOWED_DOMAINS)

        composeTestRule.onNodeWithText(ALLOWED_DOMAINS_BODY).assertIsDisplayed()
        composeTestRule.onNodeWithText(TOOLS_TAB_LABEL).assertIsSelected()
    }

    @Test
    fun theNestedPipelinesGraphHighlightsItsOwnTab() {
        startShell()
        composeTestRule.onNodeWithText(PIPELINES_TAB_LABEL).performClick()

        composeTestRule.onNodeWithText(PIPELINES_BODY).assertIsDisplayed()
        composeTestRule.onNodeWithText(PIPELINES_TAB_LABEL).assertIsSelected()
    }

    // ─── The notification deep link ────────────────────────────────────────

    @Test
    fun chatThreadDeepLinkLandsOnTheSingleChatHomeAndPostsTheThread() {
        startShell()
        // Get the user somewhere else first, exactly as a backgrounded app is
        // when a background HITL notification arrives.
        composeTestRule.onNodeWithText(MORE_TAB_LABEL).performClick()
        navigateTo(NavRoutes.SETTINGS)

        val handled = dispatchDeepLink("${NavRoutes.DEEP_LINK_SCHEME}://chat/$SESSION_ID")

        assertTrue("the knotwork://chat/{id} intent must be recognised", handled)
        composeTestRule.onNodeWithText(CHAT_BODY).assertIsDisplayed()
        composeTestRule.runOnIdle {
            // One chat entry, at the bottom: Back from a notification-opened
            // chat closes the app exactly like a normal launch.
            assertEquals(listOf(NavRoutes.CHAT_TAB), navController.stackRoutes())
            assertTrue(isTabRootStack(navController.stackRoutes()))
        }
        val request = runBlocking { chatEntryRelay.requests.first() }
        assertEquals(ChatEntryRequest.OpenThread(SESSION_ID), request)
    }

    @Test
    fun bareChatDeepLinkLandsOnTheChatHomeWithoutSwitchingSession() {
        startShell()
        composeTestRule.onNodeWithText(TOOLS_TAB_LABEL).performClick()

        val handled = dispatchDeepLink("${NavRoutes.DEEP_LINK_SCHEME}://chat")

        assertTrue(handled)
        composeTestRule.onNodeWithText(CHAT_BODY).assertIsDisplayed()
        composeTestRule.runOnIdle { assertEquals(listOf(NavRoutes.CHAT_TAB), navController.stackRoutes()) }
    }

    @Test
    fun pipelinesDeepLinkEntersThePipelinesTabAsATabSwitch() {
        startShell()
        composeTestRule.onNodeWithText(MORE_TAB_LABEL).performClick()

        val handled = dispatchDeepLink(NavRoutes.PIPELINES_DEEP_LINK_PATTERN)

        assertTrue(handled)
        composeTestRule.onNodeWithText(PIPELINES_BODY).assertIsDisplayed()
        composeTestRule.onNodeWithText(PIPELINES_TAB_LABEL).assertIsSelected()
        composeTestRule.runOnIdle {
            // Anchor + the tab, never the More tab left buried underneath.
            assertEquals(listOf(NavRoutes.CHAT_TAB, NavRoutes.PIPELINE_LIBRARY), navController.stackRoutes())
        }
    }

    @Test
    fun anUnknownDeepLinkLeavesTheCurrentDestinationUntouched() {
        startShell()
        composeTestRule.onNodeWithText(TOOLS_TAB_LABEL).performClick()

        val handled = dispatchDeepLink("${NavRoutes.DEEP_LINK_SCHEME}://not-a-destination")

        assertFalse(handled)
        composeTestRule.onNodeWithText(TOOLS_BODY).assertIsDisplayed()
    }

    // ─── Harness ───────────────────────────────────────────────────────────

    /**
     * Stands up the shell over a graph shaped like [AppNavGraph] for the routes
     * under test. Splash is omitted: every test starts from the post-splash
     * state, where the Chat tab is already the bottom of the stack.
     */
    private fun startShell() {
        composeTestRule.setContent {
            val nav = rememberNavController()
            navController = nav
            AppShellScaffold(navController = nav, transientMessageRelay = TransientMessageRelay()) { _ ->
                NavHost(navController = nav, startDestination = NavRoutes.CHAT_TAB) {
                    composable(NavRoutes.CHAT_TAB) { Text(CHAT_BODY) }
                    navigation(startDestination = NavRoutes.PIPELINE_LIBRARY, route = NavRoutes.PIPELINES_GRAPH) {
                        composable(NavRoutes.PIPELINE_LIBRARY) { Text(PIPELINES_BODY) }
                    }
                    composable(NavRoutes.TOOLS) { Text(TOOLS_BODY) }
                    composable(NavRoutes.ALLOWED_DOMAINS) { Text(ALLOWED_DOMAINS_BODY) }
                    composable(NavRoutes.MORE) { Text(MORE_BODY) }
                    navigation(startDestination = NavRoutes.SETTINGS_HUB, route = NavRoutes.SETTINGS) {
                        composable(NavRoutes.SETTINGS_HUB) { Text(SETTINGS_HUB_BODY) }
                        composable(NavRoutes.SETTINGS_TOOLS) { Text(SETTINGS_TOOLS_BODY) }
                        composable(NavRoutes.SETTINGS_TOOLS_MANAGE) { Text(SETTINGS_TOOLS_MANAGE_BODY) }
                    }
                }
            }
        }
    }

    /** Pushes [route] the way a screen's own nav callback does. */
    private fun navigateTo(route: String) {
        composeTestRule.runOnUiThread { navController.navigate(route) }
        composeTestRule.waitForIdle()
    }

    /** Fires the system Back gesture through the activity's own dispatcher. */
    private fun pressBack() {
        composeTestRule.runOnUiThread { composeTestRule.activity.onBackPressedDispatcher.onBackPressed() }
        composeTestRule.waitForIdle()
    }

    /** Runs [uri] through the production deep-link router, as `MainActivity` does. */
    private fun dispatchDeepLink(uri: String): Boolean {
        var handled = false
        composeTestRule.runOnUiThread {
            handled = navController.navigateToDeepLink(
                intent = Intent(Intent.ACTION_VIEW, uri.toUri()),
                chatEntryRelay = chatEntryRelay,
            )
        }
        composeTestRule.waitForIdle()
        return handled
    }

    /**
     * The routes the shell itself reads: `ComposeNavigator.backStack`, which
     * carries only `composable` destinations. Asserting against the same source
     * production uses keeps the expectations below honest about stack shape —
     * `NavController.currentBackStack` would additionally list `NavGraph`
     * entries the user is never "on", and it is `@RestrictTo` besides.
     */
    private fun NavHostController.stackRoutes(): List<String> =
        navigatorProvider[ComposeNavigator::class].backStack.value.mapNotNull { it.destination.route }

    private fun NavHostController.currentRoute(): String? = currentBackStackEntry?.destination?.route

    private companion object {
        const val SESSION_ID = "session-42"

        const val CHAT_BODY = "chat-home-body"
        const val PIPELINES_BODY = "pipeline-library-body"
        const val TOOLS_BODY = "tools-body"
        const val ALLOWED_DOMAINS_BODY = "allowed-domains-body"
        const val MORE_BODY = "more-body"
        const val SETTINGS_HUB_BODY = "settings-hub-body"
        const val SETTINGS_TOOLS_BODY = "settings-tools-body"
        const val SETTINGS_TOOLS_MANAGE_BODY = "settings-tools-manage-body"

        const val CHAT_TAB_LABEL = "Chat"
        const val PIPELINES_TAB_LABEL = "Pipelines"
        const val TOOLS_TAB_LABEL = "Tools"
        const val MORE_TAB_LABEL = "More"
    }
}
