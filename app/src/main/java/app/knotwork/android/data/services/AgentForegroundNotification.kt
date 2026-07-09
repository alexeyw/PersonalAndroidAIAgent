package app.knotwork.android.data.services

import android.app.Notification
import android.app.PendingIntent
import android.content.Context
import androidx.core.app.NotificationCompat
import app.knotwork.android.R
import app.knotwork.android.domain.constants.NotificationChannels

/**
 * Single source of truth for the foreground status notification posted on the
 * [NotificationChannels.AGENT_FOREGROUND] channel. Both [AgentForegroundService]
 * (the interactive agent service) and [AgentWorker] (headless scheduled runs)
 * build their promotion notification here, so the icon, ongoing/alert-once flags
 * and tap-to-open behaviour cannot drift between the two.
 */
object AgentForegroundNotification {

    /**
     * Builds an ongoing status notification for the agent foreground channel.
     *
     * @param context Context used to resolve resources and the channel.
     * @param contentTitle The notification title.
     * @param contentText The status line shown as the notification body.
     * @param contentIntent Intent fired when the notification is tapped (opens
     *   the app); `null` leaves the notification non-tappable.
     * @return The built [Notification].
     */
    fun build(
        context: Context,
        contentTitle: String,
        contentText: String,
        contentIntent: PendingIntent? = null,
    ): Notification = NotificationCompat.Builder(context, NotificationChannels.AGENT_FOREGROUND)
        .setContentTitle(contentTitle)
        .setContentText(contentText)
        .setSmallIcon(R.drawable.ic_stat_agent)
        .setOngoing(true)
        .setOnlyAlertOnce(true)
        .setContentIntent(contentIntent)
        .build()

    /**
     * Resolves a [PendingIntent] that opens the app's launcher activity, or
     * `null` when the launch intent cannot be resolved. Built via the package
     * launch intent (not a direct Activity reference) so a data-layer caller
     * does not depend on the presentation layer.
     *
     * @param context Context used to resolve the launch intent.
     * @return An immutable activity [PendingIntent], or `null`.
     */
    fun launchContentIntent(context: Context): PendingIntent? =
        context.packageManager.getLaunchIntentForPackage(context.packageName)?.let { launchIntent ->
            PendingIntent.getActivity(context, 0, launchIntent, PendingIntent.FLAG_IMMUTABLE)
        }
}
