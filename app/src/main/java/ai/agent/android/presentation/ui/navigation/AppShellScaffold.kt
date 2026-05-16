package ai.agent.android.presentation.ui.navigation

import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalActivity
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavHostController
import androidx.navigation.NavOptionsBuilder
import androidx.navigation.compose.currentBackStackEntryAsState

/**
 * Root scaffold for the post-splash, post-onboarding app surface.
 *
 * Owns three responsibilities:
 *
 *  1. **Bottom-nav chrome.** Renders the M3 [NavigationBar] with the four
 *     [TAB_DESTINATIONS] tabs (`decisions.md §12`). Visibility per route is
 *     decided by [shouldShowBottomNav] (a pure function so it is
 *     unit-testable). Show/hide uses an [AnimatedVisibility] slide so the
 *     editor / onboarding entry doesn't snap the body.
 *
 *  2. **Tab-switch state preservation.** Selecting a tab uses the canonical
 *     Compose Navigation pattern (`popUpTo(startDestination) {
 *     saveState = true } + restoreState = true`) — preserves each tab's
 *     inner back-stack and scroll position across switches and rotations.
 *
 *  3. **Root-tab Back behaviour.** While on a tab's start-destination,
 *     [BackHandler] short-circuits to `activity.finish()` so the app
 *     exits — matches the task brief "BackHandler on root tabs closes the
 *     app, not switches to the previous tab". On deeper screens the
 *     handler is disabled and default Back (pop the inner stack) takes
 *     over.
 *
 * The animated theme-flip crossfade specified in the design brief ships
 * with Task 10 alongside the manual theme toggle in `SettingsScreen`;
 * here the host activity's `AndroidAIAgentTheme` is the single source of
 * truth for the current scheme, and system-theme changes already cause a
 * natural Compose recomposition.
 *
 * @param navController The host activity's [NavHostController]; the
 *        composable observes the current back-stack entry through it.
 * @param content The nav-graph composable to host. Typically `AppNavGraph`.
 */
@Composable
fun AppShellScaffold(navController: NavHostController, content: @Composable (innerPadding: PaddingValues) -> Unit) {
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute: String? = navBackStackEntry?.destination?.route
    val showBottomNav = shouldShowBottomNav(currentRoute)
    val activity = LocalActivity.current

    val isOnTabRoot = TAB_DESTINATIONS.any { it.route == currentRoute }
    BackHandler(enabled = isOnTabRoot) {
        activity?.finish()
    }

    // Wrap the whole shell in `imePadding()` so the bottom-nav + body slide
    // up in lockstep with the keyboard. Without this the bottom-nav slot
    // keeps reserving layout space behind the IME and any IME-padded
    // composer above it has to compete with that reserved-but-hidden area,
    // which produces a visible gap + a jump when the IME animation
    // finishes (the keyboard tween and an `AnimatedVisibility` hide-anim
    // run on their own clocks). With the whole Scaffold tracking the IME
    // inset directly, the keyboard, bottom-nav, and composer all move on
    // the same frame.
    Scaffold(
        modifier = Modifier.fillMaxSize().imePadding(),
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        bottomBar = {
            AnimatedVisibility(
                visible = showBottomNav,
                enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
                exit = slideOutVertically(targetOffsetY = { it }) + fadeOut(),
            ) {
                AppBottomNavigationBar(
                    currentRoute = currentRoute,
                    onTabSelected = { tab ->
                        navController.navigate(tab.route) {
                            applyTabSwitchOptions()
                        }
                    },
                )
            }
        },
    ) { innerPadding ->
        Box(modifier = Modifier.padding(innerPadding)) {
            content(PaddingValues())
        }
    }
}

@Composable
private fun AppBottomNavigationBar(currentRoute: String?, onTabSelected: (TabDestination) -> Unit) {
    NavigationBar {
        TAB_DESTINATIONS.forEach { tab ->
            val selected = currentRoute?.belongsToTab(tab) == true
            NavigationBarItem(
                selected = selected,
                onClick = { onTabSelected(tab) },
                icon = {
                    Icon(
                        imageVector = if (selected) tab.iconSelected else tab.iconUnselected,
                        contentDescription = null,
                    )
                },
                label = { Text(stringResource(tab.labelRes)) },
            )
        }
    }
}

/**
 * Maps a destination route to the [TabDestination] that owns it, so the
 * bottom-nav can keep the correct tab highlighted while the user is on a
 * deeper screen (e.g. the pipeline editor highlights the Pipelines tab).
 *
 * The owning-tab mapping is explicit rather than derived from
 * `NavDestination.hierarchy`: nested-graph routes (`pipeline-library`)
 * would already match, but standalone routes like `pipeline/{id}/edit`
 * would not without a table — and a wrong "no tab selected" state during
 * editing would feel like a navigation bug.
 */
internal fun String.belongsToTab(tab: TabDestination): Boolean = when (tab.route) {
    NavRoutes.CHAT_TAB -> this == NavRoutes.CHAT_TAB || this == NavRoutes.CHAT_WITH_THREAD
    NavRoutes.PIPELINES_GRAPH ->
        this == NavRoutes.PIPELINES_GRAPH ||
            this == NavRoutes.PIPELINE_LIBRARY ||
            this == NavRoutes.PIPELINE_EDITOR ||
            this == NavRoutes.PIPELINE_EDIT_WITH_ID
    NavRoutes.TOOLS ->
        this == NavRoutes.TOOLS ||
            this == NavRoutes.TOOL_DETAIL ||
            this == NavRoutes.ADD_MCP_SERVER
    NavRoutes.MORE ->
        this == NavRoutes.MORE ||
            this == NavRoutes.MEMORY ||
            this == NavRoutes.MODELS ||
            this == NavRoutes.MONITORING ||
            this == NavRoutes.TASK_MONITOR ||
            this == NavRoutes.SETTINGS ||
            this == NavRoutes.PROMPTS ||
            this == NavRoutes.ABOUT
    else -> false
}

/**
 * Anchor route used as the [popUpTo] target during tab switches.
 *
 * We intentionally do **not** use `navController.graph.findStartDestination()`
 * here: this graph's start destination is [NavRoutes.SPLASH], and the
 * splash handler removes itself from the back-stack with
 * `popUpTo(SPLASH) { inclusive = true }` once initialization completes.
 * After that, the splash id is no longer on the stack, so passing it as
 * the `popUpTo` target makes the pop a silent no-op — root-tab entries
 * accumulate and the documented `saveState` / `restoreState` behaviour
 * stops working.
 *
 * Pinning the anchor to [NavRoutes.CHAT_TAB] (the first tab, navigated
 * to right after splash) gives us a route that is guaranteed to be on
 * the stack at the bottom whenever a tab-switch can happen. The Chat
 * tab itself stays permanently anchored at the bottom; every other tab
 * enters above it and is popped + saved on the next switch, which is
 * exactly the Material 3 NavigationSuite back-stack contract.
 */
internal const val TAB_BACK_STACK_ANCHOR: String = NavRoutes.CHAT_TAB

/**
 * Canonical nav-options for switching to a tab destination.
 *
 * - `popUpTo(TAB_BACK_STACK_ANCHOR) { saveState = true }` pops every
 *   entry above the anchor while preserving the popped tabs' state
 *   (back-stack, scroll position, ViewModel state) so re-selection
 *   restores the user where they left off.
 * - `launchSingleTop = true` avoids stacking duplicate copies of a tab
 *   when the user taps the same tab repeatedly.
 * - `restoreState = true` replays the saved state when revisiting a tab.
 */
internal fun NavOptionsBuilder.applyTabSwitchOptions() {
    popUpTo(TAB_BACK_STACK_ANCHOR) {
        saveState = true
    }
    launchSingleTop = true
    restoreState = true
}
