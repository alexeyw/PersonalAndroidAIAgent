package app.knotwork.android.presentation.ui.navigation

/**
 * Pure decision function: should the bottom navigation bar be visible for [route]?
 *
 * Kept as a standalone function (not a `Modifier`/`Composable`) so it is
 * unit-testable on the JVM and the rule table lives in one grep-able place.
 *
 * Visibility policy:
 *  - Visible on the four top-level tabs and their secondary screens reachable
 *    via the More menu (Memory / Models / Settings / About / etc.) — staying
 *    inside the nav structure should never hide the chrome that lets you
 *    switch tabs.
 *  - Hidden on full-screen, focus-mode surfaces:
 *      - Splash and Onboarding — these live outside the bottom-nav lifecycle.
 *      - Pipeline editor and the parameterised `pipeline/{id}/edit` alias —
 *        the canvas needs the full screen height for pan/zoom.
 *  - Hidden on modal bottom-sheet routes (`sheet/...`) — the sheet provides
 *    its own dismiss affordances and overlays the host destination, which
 *    keeps the bar painted underneath.
 *  - `null` route (initial composition before [NavHost] resolves the
 *    start-destination) — hide; the bar would otherwise flash for one frame.
 *
 * @param route The `currentBackStackEntry?.destination?.route` value.
 * @return `true` if the [AppShellScaffold]'s `NavigationBar` should be shown
 *         for this destination.
 */
fun shouldShowBottomNav(route: String?): Boolean {
    if (route == null) return false
    return when (route) {
        NavRoutes.SPLASH,
        NavRoutes.ONBOARDING,
        NavRoutes.PIPELINE_EDITOR,
        NavRoutes.PIPELINE_EDIT_WITH_ID,
        -> false

        else -> !route.startsWith(MODAL_SHEET_ROUTE_PREFIX)
    }
}

/** All modal-sheet routes share this prefix; cheap structural check. */
private const val MODAL_SHEET_ROUTE_PREFIX: String = "sheet/"
