package ai.agent.android.presentation.notifications

import ai.agent.android.domain.services.ApprovalNotifier
import ai.agent.android.presentation.receivers.AgentApprovalReceiver
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

/**
 * Manager that sends a high-priority notification to the user requesting approval
 * to execute a tool, as part of the Human-in-the-loop mechanism.
 */
class ApprovalNotificationManager @Inject constructor(
    @ApplicationContext private val context: Context
) : ApprovalNotifier {

    companion object {
        const val CHANNEL_ID = "AgentApprovalChannel"
        const val NOTIFICATION_ID = 201
    }

    /**
     * Sends a notification to request user approval for a tool execution.
     *
     * @param sessionId The ID of the session requesting approval.
     * @param toolName The name of the tool to be executed.
     * @param arguments The arguments to be passed to the tool.
     */
    override fun sendApprovalRequest(sessionId: String, toolName: String, arguments: String) {
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        val channel = NotificationChannel(
            CHANNEL_ID,
            "Agent Approvals",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "Notifications for tool execution approvals"
        }
        notificationManager.createNotificationChannel(channel)

        val approveIntent = Intent(context, AgentApprovalReceiver::class.java).apply {
            action = AgentApprovalReceiver.ACTION_APPROVE
            putExtra("sessionId", sessionId)
        }
        val approvePendingIntent = PendingIntent.getBroadcast(
            context, sessionId.hashCode(), approveIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val denyIntent = Intent(context, AgentApprovalReceiver::class.java).apply {
            action = AgentApprovalReceiver.ACTION_DENY
            putExtra("sessionId", sessionId)
        }
        val denyPendingIntent = PendingIntent.getBroadcast(
            context, sessionId.hashCode() + 1, denyIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setContentTitle("Tool Execution Approval")
            .setContentText("Agent wants to use $toolName. Allow?")
            .setStyle(NotificationCompat.BigTextStyle().bigText("Agent wants to use $toolName with args:\n$arguments"))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .addAction(android.R.drawable.ic_media_play, "Approve", approvePendingIntent)
            .addAction(android.R.drawable.ic_delete, "Deny", denyPendingIntent)
            .setAutoCancel(true)
            .build()

        // Use sessionId hashcode as notification ID so multiple requests can be shown if needed
        notificationManager.notify(NOTIFICATION_ID + sessionId.hashCode() % 1000, notification)
    }
}
