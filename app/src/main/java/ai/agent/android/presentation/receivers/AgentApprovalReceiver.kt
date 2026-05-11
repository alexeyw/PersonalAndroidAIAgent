package ai.agent.android.presentation.receivers

import ai.agent.android.domain.constants.TimeAndIdConstants
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

    /**
     * Called when the BroadcastReceiver is receiving an Intent broadcast.
     *
     * Decodes [intent]'s action through [ApprovalAction.fromAction] so the dispatch is
     * exhaustive on the enum; unknown actions are silently ignored.
     *
     * @param context The Context in which the receiver is running.
     * @param intent The Intent being received.
     */
    override fun onReceive(context: Context, intent: Intent) {
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val sessionId = intent.getStringExtra("sessionId") ?: return

        notificationManager.cancel(
            ApprovalNotificationManager.NOTIFICATION_ID +
                sessionId.hashCode() % TimeAndIdConstants.NOTIFICATION_ID_RANGE,
        )

        when (ApprovalAction.fromAction(intent.action)) {
            ApprovalAction.APPROVE -> orchestratorUseCase.resumeWithApproval(sessionId, true)
            ApprovalAction.DENY -> orchestratorUseCase.resumeWithApproval(sessionId, false)
            null -> Unit
        }
    }
}
