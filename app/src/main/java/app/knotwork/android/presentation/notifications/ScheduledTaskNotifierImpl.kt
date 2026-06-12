package app.knotwork.android.presentation.notifications

import android.Manifest
import android.annotation.SuppressLint
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.TaskStackBuilder
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.app.NotificationChannelCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import app.knotwork.android.R
import app.knotwork.android.domain.constants.NotificationChannels
import app.knotwork.android.domain.repositories.SettingsRepository
import app.knotwork.android.domain.services.ScheduledTaskNotifier
import app.knotwork.android.presentation.ui.MainActivity
import app.knotwork.android.presentation.ui.navigation.NavRoutes
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.firstOrNull
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Default [ScheduledTaskNotifier] backed by [NotificationManagerCompat].
 *
 * Lives in the presentation layer (next to [ApprovalNotificationManager])
 * because the tap action deep-links into [MainActivity] via the existing
 * `knotwork://chat/{threadId}` navigation pattern — a dependency the data
 * layer must not carry. Background components consume the domain-level
 * [ScheduledTaskNotifier] interface and stay presentation-agnostic.
 *
 * Posting is gated twice: by the user's "Scheduled task results" Settings
 * toggle and by the POST_NOTIFICATIONS runtime permission. When either gate
 * is closed the post is silently skipped — the run's result still lands in
 * the chat session, so nothing is lost beyond the announcement.
 */
@Singleton
class ScheduledTaskNotifierImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    private val settingsRepository: SettingsRepository,
) : ScheduledTaskNotifier {

    override fun registerChannel() {
        val channel = NotificationChannelCompat.Builder(
            NotificationChannels.TASK_RESULTS,
            NotificationManager.IMPORTANCE_DEFAULT,
        )
            .setName(context.getString(R.string.notifications_task_results_channel_name))
            .setDescription(context.getString(R.string.notifications_task_results_channel_description))
            .build()
        NotificationManagerCompat.from(context).createNotificationChannel(channel)
    }

    override suspend fun notifyCompleted(sessionId: String, resultPreview: String) {
        post(
            sessionId = sessionId,
            title = context.getString(R.string.notifications_task_completed_title),
            body = resultPreview,
            icon = android.R.drawable.stat_sys_download_done,
        )
    }

    override suspend fun notifyFailed(sessionId: String, reason: String) {
        post(
            sessionId = sessionId,
            title = context.getString(R.string.notifications_task_failed_title),
            body = reason,
            icon = android.R.drawable.stat_notify_error,
        )
    }

    @SuppressLint("MissingPermission") // hasPostNotificationsPermission() gates the call below.
    private suspend fun post(sessionId: String, title: String, body: String, icon: Int) {
        val enabled = settingsRepository.scheduledTaskNotificationsEnabled.firstOrNull() ?: false
        if (!enabled) return
        if (!hasPostNotificationsPermission()) return
        val notification = NotificationCompat.Builder(context, NotificationChannels.TASK_RESULTS)
            .setSmallIcon(icon)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(chatDeepLinkIntent(sessionId))
            .setAutoCancel(true)
            .build()
        NotificationManagerCompat.from(context).notify(notificationId(sessionId), notification)
    }

    /**
     * Builds the tap action: an activity [PendingIntent] whose `ACTION_VIEW`
     * uri matches the `knotwork://chat/{threadId}` pattern registered on the
     * chat destination, so Navigation routes straight into the session.
     * [TaskStackBuilder] synthesises the back stack — pressing Back from the
     * deep-linked chat lands on the app's start destination instead of the
     * launcher.
     */
    private fun chatDeepLinkIntent(sessionId: String): PendingIntent? {
        val intent = Intent(
            Intent.ACTION_VIEW,
            "${NavRoutes.DEEP_LINK_SCHEME}://${NavRoutes.chatRoute(sessionId)}".toUri(),
            context,
            MainActivity::class.java,
        )
        return TaskStackBuilder.create(context).run {
            addNextIntentWithParentStack(intent)
            getPendingIntent(
                notificationId(sessionId),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
        }
    }

    private fun hasPostNotificationsPermission(): Boolean = ContextCompat.checkSelfPermission(
        context,
        Manifest.permission.POST_NOTIFICATIONS,
    ) == PackageManager.PERMISSION_GRANTED

    private fun notificationId(sessionId: String): Int = BASE_ID + (sessionId.hashCode() and ID_MASK)

    private companion object {
        /** Base notification id; keeps task-result ids clear of other notification families. */
        const val BASE_ID = 23_900

        /** Mask folding the session hash into a bounded id range. */
        const val ID_MASK = 0x0FFF
    }
}
