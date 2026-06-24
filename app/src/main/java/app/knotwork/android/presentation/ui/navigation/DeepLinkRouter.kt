package app.knotwork.android.presentation.ui.navigation

import android.content.Intent
import androidx.navigation.NavHostController

/**
 * Routes a `knotwork://` deep-link [Intent] into an already-running NavHost.
 *
 * The NavHost auto-handles the deep link that launched the activity (the
 * `onCreate` intent). But because `MainActivity` is `singleTask`, a *subsequent*
 * deep link — a launcher shortcut or share while the app is already open —
 * arrives via `onNewIntent` against the live NavController instead of a fresh
 * activity. This helper turns that intent into a single, deterministic
 * `navigate` so the target lands on top of the chat home (Back returns home),
 * with no second activity and no splash flash.
 *
 * Supported hosts mirror the registered `navDeepLink` patterns in
 * [AppNavGraph]:
 *  - `knotwork://chat/{threadId}` → that thread's chat ([NavRoutes.chatRoute]);
 *  - `knotwork://chat`            → the chat home ([NavRoutes.CHAT_TAB]);
 *  - `knotwork://pipelines`       → the pipeline library ([NavRoutes.PIPELINE_LIBRARY]).
 *
 * @param intent the new intent delivered to `MainActivity.onNewIntent`.
 * @return `true` when the intent carried a recognised `knotwork://` deep link
 *   and a navigation was dispatched; `false` otherwise (caller leaves the
 *   current destination untouched).
 */
internal fun NavHostController.navigateToDeepLink(intent: Intent): Boolean {
    val uri = intent.data ?: return false
    if (uri.scheme != NavRoutes.DEEP_LINK_SCHEME) return false
    val route = when (uri.host) {
        // `knotwork://chat` (no segment) and `knotwork://chat/<threadId>`.
        "chat" -> uri.pathSegments.firstOrNull()
            ?.takeIf { it.isNotBlank() }
            ?.let { NavRoutes.chatRoute(it) }
            ?: NavRoutes.CHAT_TAB
        // `knotwork://pipelines`.
        "pipelines" -> NavRoutes.PIPELINE_LIBRARY
        else -> return false
    }
    navigate(route) {
        // Anchor on the chat home (the permanent bottom of the post-splash
        // back stack, see AppShellScaffold.TAB_BACK_STACK_ANCHOR) and dedupe so
        // a repeated tap of the same target does not stack duplicates.
        popUpTo(NavRoutes.CHAT_TAB) { inclusive = false }
        launchSingleTop = true
    }
    return true
}
