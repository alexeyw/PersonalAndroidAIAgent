package app.knotwork.android.presentation.ui.navigation

import androidx.annotation.StringRes
import androidx.compose.ui.graphics.vector.ImageVector
import app.knotwork.android.R
import app.knotwork.design.icons.AppIcons

/**
 * One bottom-nav tab destination.
 *
 * Tabs are rendered by [AppShellScaffold] inside a Material3 `NavigationBar`.
 * The [route] is the start-destination of the tab's mini back-stack; deeper
 * screens reachable from inside a tab (e.g. the pipeline editor, a tool
 * detail screen) live as additional `composable(...)` entries in the same
 * `NavHost` but are not directly addressable through the nav bar.
 *
 * Selecting a tab uses the canonical Compose Navigation pattern
 * (`popUpTo(graph.findStartDestination()) { saveState = true }` +
 * `restoreState = true`), which automatically preserves the inner back-stack
 * and scroll position of every tab across switches and configuration changes.
 *
 * The selected tab renders the custom `AppIcons.*Active` glyph at the spec's
 * active 2.0 stroke (§0.7), while the unselected tab uses the 1.6 default —
 * the stroke weight is the selection emphasis on top of the M3 indicator pill.
 *
 * @property route Start-destination route string for the tab.
 * @property labelRes String resource for the visible label below the icon.
 * @property iconUnselected Glyph rendered when the tab is not the current destination.
 * @property iconSelected Glyph rendered when the tab is the current destination.
 */
data class TabDestination(
    val route: String,
    @param:StringRes val labelRes: Int,
    val iconUnselected: ImageVector,
    val iconSelected: ImageVector,
)

/**
 * The four top-level destinations of the bottom-nav, in display order.
 * Chat is the start tab — the app opens here after
 * splash / onboarding, and the system Back gesture exits from this tab.
 */
val TAB_DESTINATIONS: List<TabDestination> = listOf(
    TabDestination(
        route = NavRoutes.CHAT_TAB,
        labelRes = R.string.nav_tab_chat,
        iconUnselected = AppIcons.Chat,
        iconSelected = AppIcons.ChatActive,
    ),
    TabDestination(
        route = NavRoutes.PIPELINES_GRAPH,
        labelRes = R.string.nav_tab_pipelines,
        iconUnselected = AppIcons.Flow,
        iconSelected = AppIcons.FlowActive,
    ),
    TabDestination(
        route = NavRoutes.TOOLS,
        labelRes = R.string.nav_tab_tools,
        iconUnselected = AppIcons.Tool,
        iconSelected = AppIcons.ToolActive,
    ),
    TabDestination(
        route = NavRoutes.MORE,
        labelRes = R.string.nav_tab_more,
        iconUnselected = AppIcons.More2,
        iconSelected = AppIcons.More2Active,
    ),
)
