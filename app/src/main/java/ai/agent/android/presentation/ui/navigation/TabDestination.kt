package ai.agent.android.presentation.ui.navigation

import ai.agent.android.R
import androidx.annotation.StringRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Build
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.MoreHoriz
import androidx.compose.ui.graphics.vector.ImageVector
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
 * Phase 21 / Task 4: filled-variant icons (per `animations.md §App shell`,
 * "icon fill morph 200ms") will arrive with Task 11's icon set; we currently
 * pass the same outlined glyph for both [iconUnselected] and [iconSelected]
 * so the API is wired and a future swap is a single-line change.
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
 * The four top-level destinations of the bottom-nav, in display order
 * (`decisions.md §12`). Chat is the start tab — the app opens here after
 * splash / onboarding, and the system Back gesture exits from this tab.
 */
val TAB_DESTINATIONS: List<TabDestination> = listOf(
    TabDestination(
        route = NavRoutes.CHAT_TAB,
        labelRes = R.string.nav_tab_chat,
        iconUnselected = Icons.Outlined.ChatBubbleOutline,
        iconSelected = Icons.Outlined.ChatBubbleOutline,
    ),
    TabDestination(
        route = NavRoutes.PIPELINES_GRAPH,
        labelRes = R.string.nav_tab_pipelines,
        iconUnselected = AppIcons.Flow,
        iconSelected = AppIcons.Flow,
    ),
    TabDestination(
        route = NavRoutes.TOOLS,
        labelRes = R.string.nav_tab_tools,
        iconUnselected = Icons.Outlined.Build,
        iconSelected = Icons.Outlined.Build,
    ),
    TabDestination(
        route = NavRoutes.MORE,
        labelRes = R.string.nav_tab_more,
        iconUnselected = Icons.Outlined.MoreHoriz,
        iconSelected = Icons.Outlined.MoreHoriz,
    ),
)
