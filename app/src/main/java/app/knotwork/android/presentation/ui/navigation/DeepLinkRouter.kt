package app.knotwork.android.presentation.ui.navigation

import android.content.Intent
import androidx.navigation.NavHostController
import app.knotwork.android.presentation.state.ChatEntryRequestRelay

/**
 * Routes a `knotwork://` deep-link [Intent] into the app's single chat home.
 *
 * The chat *is* the home — there is one chat destination ([NavRoutes.CHAT_TAB]),
 * not a per-thread screen — so every chat deep link lands on that one home and
 * merely changes which session it shows (posted through [chatEntryRelay]). The
 * back stack stays a single chat entry, so Back from a shortcut-opened chat
 * closes the app, exactly like a normal launch.
 *
 * Supported hosts (the app's `knotwork://` entry-surface URIs):
 *  - `knotwork://chat/{threadId}` → chat home switched to that session;
 *  - `knotwork://chat`            → chat home on the current session;
 *  - `knotwork://new-chat`        → chat home with a fresh empty session;
 *  - `knotwork://pipelines`       → the pipeline library ([NavRoutes.PIPELINE_LIBRARY]).
 *
 * Used for both the cold launch intent (applied once the splash reaches home)
 * and warm intents delivered to the single-task instance via `onNewIntent`.
 *
 * @param intent the deep-link intent.
 * @param chatEntryRelay bus the chat-session side effect (open-thread / new-chat)
 *   is posted to; the `CHAT_TAB` composable drains it.
 * @return `true` when the intent carried a recognised `knotwork://` deep link and
 *   a navigation was dispatched; `false` otherwise (caller leaves the current
 *   destination untouched).
 */
internal fun NavHostController.navigateToDeepLink(intent: Intent, chatEntryRelay: ChatEntryRequestRelay): Boolean {
    val uri = intent.data ?: return false
    if (uri.scheme != NavRoutes.DEEP_LINK_SCHEME) return false
    when (uri.host) {
        "chat" -> {
            navigateToChatHome()
            // `knotwork://chat/<threadId>` switches sessions; bare `knotwork://chat`
            // just shows whatever is current.
            uri.pathSegments.firstOrNull()
                ?.takeIf { it.isNotBlank() }
                ?.let { chatEntryRelay.openThread(it) }
        }
        "new-chat" -> {
            navigateToChatHome()
            chatEntryRelay.newChat()
        }
        "pipelines" -> navigate(NavRoutes.PIPELINE_LIBRARY) {
            popUpTo(NavRoutes.CHAT_TAB) { inclusive = false }
            launchSingleTop = true
        }
        else -> return false
    }
    return true
}

/**
 * Lands on the single chat home, deduped and anchored at the bottom of the
 * post-splash back stack (see [AppShellScaffold]'s `TAB_BACK_STACK_ANCHOR`), so
 * a chat deep link never stacks a second chat entry.
 */
private fun NavHostController.navigateToChatHome() {
    navigate(NavRoutes.CHAT_TAB) {
        popUpTo(NavRoutes.CHAT_TAB) { inclusive = false }
        launchSingleTop = true
    }
}
