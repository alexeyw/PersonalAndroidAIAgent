package ai.agent.android.data.services

import ai.agent.android.R
import ai.agent.android.domain.constants.NotificationChannels
import ai.agent.android.domain.repositories.SettingsRepository
import ai.agent.android.domain.services.LongRunningTaskNotifier
import android.Manifest
import android.annotation.SuppressLint
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.app.NotificationChannelCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.firstOrNull
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Default [LongRunningTaskNotifier] backed by [NotificationManagerCompat].
 * Registers the low-importance "Long-running tasks" channel and posts
 * one-shot informational notifications keyed by the elapsed-time bucket
 * so repeated checks for the same pipeline don't spam the shade.
 */
@Singleton
class LongRunningTaskNotifierImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    private val settingsRepository: SettingsRepository,
) : LongRunningTaskNotifier {

    override fun registerChannel() {
        val channel = NotificationChannelCompat.Builder(
            NotificationChannels.LONG_RUNNING_TASKS,
            NotificationManager.IMPORTANCE_LOW,
        )
            .setName(context.getString(R.string.notifications_long_running_channel_name))
            .setDescription(context.getString(R.string.notifications_long_running_channel_description))
            .setShowBadge(false)
            .build()
        NotificationManagerCompat.from(context).createNotificationChannel(channel)
    }

    @SuppressLint("MissingPermission") // hasPostNotificationsPermission() gates the call below.
    override suspend fun notify(pipelineName: String, elapsedMs: Long) {
        val enabled = settingsRepository.longRunningTaskNotificationsEnabled.firstOrNull() ?: false
        if (!enabled) return
        if (!hasPostNotificationsPermission()) return
        val elapsedSeconds = elapsedMs / MS_PER_SECOND
        val notification = NotificationCompat.Builder(context, NotificationChannels.LONG_RUNNING_TASKS)
            .setSmallIcon(android.R.drawable.stat_notify_sync)
            .setContentTitle(pipelineName)
            .setContentText(
                context.getString(R.string.notifications_long_running_body, elapsedSeconds),
            )
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setAutoCancel(true)
            .build()
        NotificationManagerCompat.from(context).notify(notificationId(pipelineName), notification)
    }

    private fun hasPostNotificationsPermission(): Boolean = ContextCompat.checkSelfPermission(
        context,
        Manifest.permission.POST_NOTIFICATIONS,
    ) == PackageManager.PERMISSION_GRANTED

    private fun notificationId(pipelineName: String): Int = BASE_ID + (pipelineName.hashCode() and ID_MASK)

    private companion object {
        const val MS_PER_SECOND = 1_000L
        const val BASE_ID = 22_900
        const val ID_MASK = 0x0FFF
    }
}
