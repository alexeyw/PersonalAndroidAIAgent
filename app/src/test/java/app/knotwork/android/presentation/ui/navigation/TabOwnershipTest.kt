package app.knotwork.android.presentation.ui.navigation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for [owningTabRoute] / [isTabRootStack] — the two pure functions
 * that replaced the hand-maintained `route → tab` table.
 *
 * The interesting cases are the ones the old table got wrong: a destination it
 * never listed (no tab highlighted at all), and a tab root pushed on top of
 * another subtree (the shell believing it sat on a tab root while five entries
 * were still underneath).
 */
class TabOwnershipTest {

    // ─── owningTabRoute ────────────────────────────────────────────────────

    @Test
    fun `given an empty stack when resolving the owner then none is reported`() {
        assertNull(owningTabRoute(emptyList()))
    }

    @Test
    fun `given a pre-tab stack when resolving the owner then none is reported`() {
        // Splash and onboarding live outside the tab structure entirely; the
        // bottom nav is hidden for them and no tab may light up.
        assertNull(owningTabRoute(listOf(NavRoutes.SPLASH)))
        assertNull(owningTabRoute(listOf(NavRoutes.ONBOARDING)))
    }

    @Test
    fun `given each tab root alone when resolving the owner then it owns itself`() {
        TAB_DESTINATIONS.forEach { tab ->
            assertEquals(
                "tab ${tab.route} must own a stack consisting of itself",
                tab.route,
                owningTabRoute(listOf(tab.route)),
            )
        }
    }

    @Test
    fun `given a nested tab graph when resolving the owner then the tab route is reported`() {
        // The Pipelines tab is a `navigation { }` graph. Its route is not on the
        // `ComposeNavigator` stack at all — only its start destination is — so
        // that start destination has to resolve to the Pipelines *tab* alone.
        val library = listOf(NavRoutes.CHAT_TAB, NavRoutes.PIPELINE_LIBRARY)
        val editor = library + NavRoutes.PIPELINE_EDITOR

        assertEquals(NavRoutes.PIPELINES_GRAPH, owningTabRoute(library))
        assertEquals(NavRoutes.PIPELINES_GRAPH, owningTabRoute(editor))
        // Robust either way: a stack that does carry the graph route resolves
        // to the same tab, so the functions do not depend on which back-stack
        // source a future caller reads.
        assertEquals(
            NavRoutes.PIPELINES_GRAPH,
            owningTabRoute(listOf(NavRoutes.CHAT_TAB, NavRoutes.PIPELINES_GRAPH, NavRoutes.PIPELINE_LIBRARY)),
        )
    }

    @Test
    fun `given a deep screen the old table never listed when resolving the owner then its tab still wins`() {
        // Each of these routes was missing from the removed `belongsToTab`
        // table, so the bottom nav highlighted nothing at all on them.
        val orphanedUnderMore = listOf(
            NavRoutes.FILES,
            NavRoutes.SKILLS,
            NavRoutes.TRIGGERS,
            NavRoutes.PIPELINE_PRESETS,
            NavRoutes.CHAT_ARCHIVE,
            NavRoutes.DISCOVER,
            NavRoutes.DISCOVER_DETAIL,
            NavRoutes.PROVIDER_DETAIL,
            NavRoutes.ADD_PROVIDER,
        )
        orphanedUnderMore.forEach { route ->
            assertEquals(
                "$route reached from More must keep the More tab highlighted",
                NavRoutes.MORE,
                owningTabRoute(listOf(NavRoutes.CHAT_TAB, NavRoutes.MORE, route)),
            )
        }
        assertEquals(
            NavRoutes.TOOLS,
            owningTabRoute(listOf(NavRoutes.CHAT_TAB, NavRoutes.TOOLS, NavRoutes.ALLOWED_DOMAINS)),
        )
    }

    @Test
    fun `given settings reached from the More tab when resolving the owner then More is reported`() {
        val stack = listOf(
            NavRoutes.CHAT_TAB,
            NavRoutes.MORE,
            NavRoutes.SETTINGS_HUB,
            NavRoutes.SETTINGS_TOOLS,
        )
        assertEquals(NavRoutes.MORE, owningTabRoute(stack))
    }

    @Test
    fun `given settings reached from the chat drawer when resolving the owner then Chat is reported`() {
        // Deliberate change of behaviour: the removed table pinned every
        // settings route to More even when More was not on the stack. Back from
        // here returns to the chat, so the highlight now says so too — the
        // highlight and the Back button stopped contradicting each other.
        val stack = listOf(NavRoutes.CHAT_TAB, NavRoutes.SETTINGS_HUB)
        assertEquals(NavRoutes.CHAT_TAB, owningTabRoute(stack))
    }

    @Test
    fun `given the settings-owned tools surface when resolving the owner then the settings tab wins`() {
        // Closed-test finding `#14`: this path used to land on the Tools *tab*
        // root, flipping the highlight to a tab the user never chose. It is now
        // a settings-owned route, so the owning tab is whichever tab the
        // settings subtree was entered from.
        val stack = listOf(
            NavRoutes.CHAT_TAB,
            NavRoutes.MORE,
            NavRoutes.SETTINGS_HUB,
            NavRoutes.SETTINGS_TOOLS,
            NavRoutes.SETTINGS_TOOLS_MANAGE,
            NavRoutes.MCP_SERVER_CONFIG,
        )
        assertEquals(NavRoutes.MORE, owningTabRoute(stack))
    }

    @Test
    fun `given a tab entered above another tab when resolving the owner then the topmost tab wins`() {
        // Tab switches collapse the stack to the anchor, so this shape should
        // not occur in production — but if it ever does, the tab the user is
        // actually looking at is the one that must be highlighted.
        val stack = listOf(NavRoutes.CHAT_TAB, NavRoutes.MORE)
        assertEquals(NavRoutes.MORE, owningTabRoute(stack))
    }

    // ─── isTabRootStack ────────────────────────────────────────────────────

    @Test
    fun `given an empty stack when asking about tab roots then it is not a tab-root stack`() {
        // An empty stack must not enable the finish-the-app Back handler; there
        // is nothing to exit from yet.
        assertFalse(isTabRootStack(emptyList()))
    }

    @Test
    fun `given each tab root alone when asking about tab roots then Back exits the app`() {
        TAB_DESTINATIONS.forEach { tab ->
            assertTrue("Back on the ${tab.route} root must exit the app", isTabRootStack(listOf(tab.route)))
        }
        // The Pipelines tab reached through its graph: only the start
        // destination reaches the stack, and it counts as that tab's root.
        assertTrue(isTabRootStack(listOf(NavRoutes.CHAT_TAB, NavRoutes.PIPELINE_LIBRARY)))
    }

    @Test
    fun `given the anchor plus one tab when asking about tab roots then Back exits the app`() {
        assertTrue(isTabRootStack(listOf(NavRoutes.CHAT_TAB, NavRoutes.MORE)))
        assertTrue(isTabRootStack(listOf(NavRoutes.CHAT_TAB, NavRoutes.TOOLS)))
    }

    @Test
    fun `given a deeper screen on the stack when asking about tab roots then Back pops instead`() {
        assertFalse(isTabRootStack(listOf(NavRoutes.CHAT_TAB, NavRoutes.MORE, NavRoutes.MEMORY)))
        assertFalse(isTabRootStack(listOf(NavRoutes.CHAT_TAB, NavRoutes.TOOLS, NavRoutes.MCP_SERVER_CONFIG)))
        assertFalse(
            isTabRootStack(listOf(NavRoutes.CHAT_TAB, NavRoutes.PIPELINE_LIBRARY, NavRoutes.PIPELINE_EDITOR)),
        )
    }

    @Test
    fun `given the finding 14 stack when asking about tab roots then Back must not exit the app`() {
        // The exact stack the closed-test tester was stranded on: a tab root
        // (Tools) pushed on top of the settings subtree. The removed check
        // looked only at the top entry, saw a tab root, and answered `true` —
        // arming the finish-the-app handler over a stack with five entries
        // still under it. In practice NavHost's own Back handler is registered
        // later and consumes the press first (measured, see `TabOwnership`), so
        // the app did not actually close; the point of asserting it here is
        // that the shell must not be *wrong* about where it is.
        val strandedStack = listOf(
            NavRoutes.CHAT_TAB,
            NavRoutes.MORE,
            NavRoutes.SETTINGS_HUB,
            NavRoutes.SETTINGS_TOOLS,
            NavRoutes.TOOLS,
        )
        assertFalse(isTabRootStack(strandedStack))
    }

    // ─── Set consistency ───────────────────────────────────────────────────

    @Test
    fun `given the declared tabs when reading the tab-root set then every tab route is a member`() {
        TAB_DESTINATIONS.forEach { tab ->
            assertTrue("${tab.route} must be a tab root", tab.route in TAB_ROOT_ROUTES)
        }
    }

    @Test
    fun `given the tab-root set when resolving each member then it maps to a declared tab`() {
        val declared = TAB_DESTINATIONS.map { it.route }.toSet()
        TAB_ROOT_ROUTES.forEach { route ->
            assertTrue(
                "$route resolves to ${owningTabRoute(listOf(route))}, which is not a declared tab",
                owningTabRoute(listOf(route)) in declared,
            )
        }
    }
}
