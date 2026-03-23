package ai.agent.android.presentation.receivers

import ai.agent.android.domain.usecases.AgentOrchestratorUseCase
import ai.agent.android.presentation.notifications.ApprovalNotificationManager
import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/**
 * BroadcastReceiver that handles the user's action from the approval notification.
 */
@AndroidEntryPoint
class AgentApprovalReceiver : BroadcastReceiver() {

    /**
     * Use case for managing the orchestrator's state and actions.
     */
    @Inject
    lateinit var orchestratorUseCase: AgentOrchestratorUseCase

    companion object {
        const val ACTION_APPROVE = "ai.agent.android.ACTION_APPROVE"
        const val ACTION_DENY = "ai.agent.android.ACTION_DENY"
    }

    /**
     * Called when the BroadcastReceiver is receiving an Intent broadcast.
     *
     * @param context The Context in which the receiver is running.
     * @param intent The Intent being received.
     */
    override fun onReceive(context: Context, intent: Intent) {
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val sessionId = intent.getStringExtra("sessionId") ?: return
        
        notificationManager.cancel(ApprovalNotificationManager.NOTIFICATION_ID + sessionId.hashCode() % 1000)

        when (intent.action) {
            ACTION_APPROVE -> orchestratorUseCase.resumeWithApproval(sessionId, true)
            ACTION_DENY -> orchestratorUseCase.resumeWithApproval(sessionId, false)
        }
    }
}
