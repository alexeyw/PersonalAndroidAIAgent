package app.knotwork.android.presentation.notifications

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import app.knotwork.android.R
import app.knotwork.android.domain.constants.NotificationChannels
import app.knotwork.android.domain.constants.TimeAndIdConstants
import app.knotwork.android.domain.models.ToolRisk
import app.knotwork.android.domain.services.ApprovalNotifier
import app.knotwork.android.presentation.receivers.AgentApprovalReceiver
import app.knotwork.android.presentation.receivers.ApprovalAction
import app.knotwork.android.presentation.state.ActiveSessionTracker
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

/**
 * Manager that sends a high-priority notification to the user requesting approval
 * to execute a tool, as part of the Human-in-the-loop mechanism.
 *
 * Maintains two `IMPORTANCE_HIGH` notification channels so the user can
 * distinguish [ToolRisk.DESTRUCTIVE] approvals (warning glyph, distinct title)
 * from reversible [ToolRisk.SENSITIVE] / opt-in [ToolRisk.READ_ONLY] approvals
 * even in the system shade. Both channels are eagerly registered on every send
 * — `NotificationManager.createNotificationChannel` is idempotent and the
 * channels survive process death.
 */
class ApprovalNotificationManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val activeSessionTracker: ActiveSessionTracker,
) : ApprovalNotifier {

    companion object {
        const val NOTIFICATION_ID = 201
    }

    /**
     * Sends a notification to request user approval for a tool execution.
     * It suppresses the notification if the user is currently viewing the active session.
     *
     * @param sessionId The ID of the session requesting approval.
     * @param toolName The name of the tool to be executed.
     * @param arguments The arguments to be passed to the tool.
     * @param risk Risk classification, drives the channel / icon / title selection.
     */
    override fun sendApprovalRequest(sessionId: String, toolName: String, arguments: String, risk: ToolRisk) {
        // If the user is currently on the chat screen for this session, they will see the inline prompt.
        if (activeSessionTracker.activeSessionId.value == sessionId) {
            return
        }

        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        ensureChannelsRegistered(notificationManager)

        val approvePendingIntent = approvalPendingIntent(sessionId, ApprovalAction.APPROVE, requestCodeOffset = 0)
        val denyPendingIntent = approvalPendingIntent(sessionId, ApprovalAction.DENY, requestCodeOffset = 1)

        val channelId = when (risk) {
            ToolRisk.DESTRUCTIVE -> NotificationChannels.AGENT_APPROVAL_DESTRUCTIVE
            ToolRisk.SENSITIVE, ToolRisk.READ_ONLY -> NotificationChannels.AGENT_APPROVAL
        }
        val smallIcon = when (risk) {
            ToolRisk.DESTRUCTIVE -> android.R.drawable.stat_sys_warning
            ToolRisk.SENSITIVE, ToolRisk.READ_ONLY -> android.R.drawable.ic_dialog_alert
        }
        val title = when (risk) {
            ToolRisk.DESTRUCTIVE -> context.getString(R.string.approval_notification_title_destructive)
            ToolRisk.SENSITIVE, ToolRisk.READ_ONLY -> context.getString(R.string.approval_notification_title_sensitive)
        }
        val contentText = context.getString(R.string.approval_notification_text, toolName)
        val bigText = context.getString(R.string.approval_notification_big_text, toolName, arguments)

        val notification = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(smallIcon)
            .setContentTitle(title)
            .setContentText(contentText)
            .setStyle(NotificationCompat.BigTextStyle().bigText(bigText))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .addAction(
                android.R.drawable.ic_media_play,
                context.getString(R.string.chat_thought_approve),
                approvePendingIntent,
            )
            .addAction(
                android.R.drawable.ic_delete,
                context.getString(R.string.chat_thought_deny),
                denyPendingIntent,
            )
            .setAutoCancel(true)
            .build()

        // Use sessionId hashcode as notification ID so multiple requests can be shown if needed
        notificationManager.notify(
            NOTIFICATION_ID + sessionId.hashCode() % TimeAndIdConstants.NOTIFICATION_ID_RANGE,
            notification,
        )
    }

    private fun ensureChannelsRegistered(notificationManager: NotificationManager) {
        val sensitiveChannel = NotificationChannel(
            NotificationChannels.AGENT_APPROVAL,
            context.getString(R.string.approval_channel_sensitive_name),
            NotificationManager.IMPORTANCE_HIGH,
        ).apply {
            description = context.getString(R.string.approval_channel_sensitive_description)
        }
        val destructiveChannel = NotificationChannel(
            NotificationChannels.AGENT_APPROVAL_DESTRUCTIVE,
            context.getString(R.string.approval_channel_destructive_name),
            NotificationManager.IMPORTANCE_HIGH,
        ).apply {
            description = context.getString(R.string.approval_channel_destructive_description)
        }
        notificationManager.createNotificationChannel(sensitiveChannel)
        notificationManager.createNotificationChannel(destructiveChannel)
    }

    private fun approvalPendingIntent(
        sessionId: String,
        action: ApprovalAction,
        requestCodeOffset: Int,
    ): PendingIntent {
        val intent = Intent(context, AgentApprovalReceiver::class.java).apply {
            this.action = action.action
            putExtra("sessionId", sessionId)
        }
        return PendingIntent.getBroadcast(
            context,
            sessionId.hashCode() + requestCodeOffset,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }
}
