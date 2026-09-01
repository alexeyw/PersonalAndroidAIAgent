package app.knotwork.android.presentation.ui.navigation

/**
 * Pure decision functions answering "which bottom-nav tab does the user's
 * *current back stack* belong to, and is that stack still at a tab root?".
 *
 * ### Why the back stack and not the current route
 *
 * The previous implementation answered both questions from the current route
 * alone, through a hand-maintained `route → tab` table. That table is a
 * maintenance trap of the same shape as a required-check list: a destination
 * added to [AppNavGraph] and forgotten in the table silently highlights **no**
 * tab at all, and the build stays green. It had already grown two documented
 * repairs of exactly that bug, and at the time of this rewrite **ten**
 * registered destinations were missing from it (`tools/allowed-domains`,
 * `files`, `skills`, `triggers`, `pipeline-presets`, `chat-archive`,
 * `discover`, `discover/detail`, `settings/provider/{id}`,
 * `settings/provider/add`).
 *
 * Deriving the answer from the stack removes the table, and with it the whole
 * class of omission: a destination pushed on top of the More tab belongs to
 * More because More is underneath it, not because someone remembered to say so.
 *
 * The change is also what makes the bottom-nav highlight stop contradicting the
 * Back button. Settings opened from the chat drawer now highlights **Chat**,
 * because Back from settings returns to the chat — the old table highlighted
 * More, a tab that is not on the stack at all and that Back never reaches.
 *
 * ### Nested tab graphs
 *
 * The Pipelines tab is a nested `navigation { }` graph, so its route
 * ([NavRoutes.PIPELINES_GRAPH]) is never a destination the user is *on* — its
 * start destination ([NavRoutes.PIPELINE_LIBRARY]) is. The stack these
 * functions read comes from `ComposeNavigator.backStack`, which carries only
 * `composable` destinations, so the graph route is not on it at all: the start
 * destination has to resolve to the Pipelines tab by itself, which is what
 * [TAB_GRAPH_START_DESTINATIONS] records.
 */

/**
 * Start destination of every tab route that is a nested navigation graph rather
 * than a single destination, keyed by the graph route.
 *
 * Only the Pipelines tab is a graph today. Adding a second one means adding a
 * row here; forgetting to is caught by [TabOwnershipTest]'s coverage assertion
 * over [TAB_DESTINATIONS], not by a reviewer's memory.
 */
private val TAB_GRAPH_START_DESTINATIONS: Map<String, String> = mapOf(
    NavRoutes.PIPELINES_GRAPH to NavRoutes.PIPELINE_LIBRARY,
)

/** Routes of the four bottom-nav tabs, as declared by [TAB_DESTINATIONS]. */
private val TAB_DESTINATION_ROUTES: Set<String> = TAB_DESTINATIONS.mapTo(LinkedHashSet()) { it.route }

/** Reverse index: a tab graph's start destination back to its tab route. */
private val GRAPH_START_TO_TAB: Map<String, String> =
    TAB_GRAPH_START_DESTINATIONS.entries.associate { (tabRoute, startRoute) -> startRoute to tabRoute }

/**
 * Every route that counts as "sitting on a tab's root": the four tab routes
 * plus the start destination of each tab that is a nested graph.
 *
 * Exposed for tests and for [isTabRootStack]; production code should prefer the
 * two functions below over membership checks.
 */
internal val TAB_ROOT_ROUTES: Set<String> = TAB_DESTINATION_ROUTES + GRAPH_START_TO_TAB.keys

/**
 * Canonical bottom-nav tab route that [route] belongs to, or `null` when the
 * route is not a tab root (a deeper screen, a nested graph that is not a tab,
 * splash, onboarding, a modal sheet).
 */
private fun tabRouteOf(route: String): String? =
    route.takeIf { it in TAB_DESTINATION_ROUTES } ?: GRAPH_START_TO_TAB[route]

/**
 * The tab that owns the back stack described by [stackRoutes], or `null` when
 * no tab root is on it (splash / onboarding, before the first tab is reached).
 *
 * The owner is the tab root **nearest the top** of the stack: with
 * `[chat-tab, more, settings/hub]` the answer is More, because More
 * is the last tab the user entered and Back walks down through it. Scanning
 * from the top rather than the bottom is what keeps a screen pushed from
 * inside one tab attributed to that tab instead of to the permanent Chat
 * anchor at the bottom of every post-splash stack.
 *
 * @param stackRoutes routes of the `composable` back-stack entries,
 *        bottom-first (`ComposeNavigator.backStack`; no `NavGraph` entries).
 * @return the owning tab's route (as declared in [TAB_DESTINATIONS]), or `null`.
 */
fun owningTabRoute(stackRoutes: List<String>): String? = stackRoutes.asReversed().firstNotNullOfOrNull(::tabRouteOf)

/**
 * Whether the back stack described by [stackRoutes] consists **only** of tab
 * roots — the condition on which the shell arms its finish-the-app Back
 * handler.
 *
 * Arming it is not the same as exiting — see below; that gap is the whole
 * argument for asking about the stack rather than about the current route.
 *
 * This is the predicate the shell's `BackHandler` runs on, and it replaces a
 * check on the *current route* alone. The older check answered `true` for any
 * stack whose **top** was a tab root — including
 * `[chat-tab, more, settings/hub, settings/tools, tools]`, the shape closed-test
 * finding `#14` produced, where the user had a whole settings subtree still
 * underneath and Back had somewhere to go.
 *
 * **Whether that actually closed the app is a race, and it was measured both
 * ways.** `NavHost` installs a `BackHandler` of its own, and
 * `OnBackPressedDispatcher` gives the press to the most recently added enabled
 * callback. On a device, an armed shell handler over a still-poppable stack
 * lost to the NavHost in four runs (Back popped, the activity survived) and won
 * in one (the activity finished). No claim is made here about which wins.
 *
 * The point is that the shell should not be in that race at all. Under this
 * predicate a stack with anything underneath the tab root is never armed, so
 * Back pops whichever handler answers — the outcome stops depending on
 * composition order. That is why this is **hardening** rather than a repair of
 * an observed defect, and why finding `#14` is described in terms of the
 * highlight and the buried subtree, which were reproducible every time.
 *
 * @param stackRoutes routes of the `composable` back-stack entries,
 *        bottom-first (`ComposeNavigator.backStack`; no `NavGraph` entries).
 * @return `true` when every entry is a tab root and the stack is non-empty.
 */
fun isTabRootStack(stackRoutes: List<String>): Boolean =
    stackRoutes.isNotEmpty() && stackRoutes.all { tabRouteOf(it) != null }
