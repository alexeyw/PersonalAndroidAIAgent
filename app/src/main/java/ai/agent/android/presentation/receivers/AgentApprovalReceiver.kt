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

    @Inject
    lateinit var orchestratorUseCase: AgentOrchestratorUseCase

    companion object {
        const val ACTION_APPROVE = "ai.agent.android.ACTION_APPROVE"
        const val ACTION_DENY = "ai.agent.android.ACTION_DENY"
    }

    override fun onReceive(context: Context, intent: Intent) {
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.cancel(ApprovalNotificationManager.NOTIFICATION_ID)

        when (intent.action) {
            ACTION_APPROVE -> orchestratorUseCase.resumeWithApproval(true)
            ACTION_DENY -> orchestratorUseCase.resumeWithApproval(false)
        }
    }
}
