package app.knotwork.android.presentation.ui.navigation

import android.app.TaskStackBuilder
import android.content.Context
import android.content.Intent
import androidx.core.net.toUri
import app.knotwork.android.presentation.ui.MainActivity

/**
 * Single source of truth for building a deep link into a chat session.
 *
 * Both the share target ([app.knotwork.android.presentation.share.ShareReceiverActivity])
 * and the scheduled-task notifier need to route into a session via the
 * `knotwork://chat/{threadId}` pattern with a synthesised back stack (so Back
 * lands on the app's start destination, not the launcher). Centralising the
 * `Intent` + [TaskStackBuilder] construction here keeps the scheme and back-stack
 * behaviour from drifting between those call sites.
 */
object ChatDeepLink {

    /** The `ACTION_VIEW` intent targeting [MainActivity] for [sessionId]'s chat route. */
    fun intent(context: Context, sessionId: String): Intent = Intent(
        Intent.ACTION_VIEW,
        "${NavRoutes.DEEP_LINK_SCHEME}://${NavRoutes.chatRoute(sessionId)}".toUri(),
        context,
        MainActivity::class.java,
    )

    /** A [TaskStackBuilder] carrying [intent] with the parent back stack synthesised. */
    fun backStack(context: Context, sessionId: String): TaskStackBuilder =
        TaskStackBuilder.create(context).apply { addNextIntentWithParentStack(intent(context, sessionId)) }
}
