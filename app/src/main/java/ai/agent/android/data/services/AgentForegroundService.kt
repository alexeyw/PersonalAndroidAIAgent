package ai.agent.android.data.services

import ai.agent.android.domain.engine.LlmInferenceEngine
import ai.agent.android.domain.models.AgentOrchestratorState
import ai.agent.android.domain.usecases.AgentOrchestratorUseCase
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Foreground service that keeps the Agent (and LiteRT-ML engine) alive in memory 
 * while tasks are executing or while waiting for short periods.
 * Includes idle timeout logic to safely deallocate model resources if inactive.
 */
@AndroidEntryPoint
class AgentForegroundService : Service() {

    @Inject
    lateinit var agentOrchestratorUseCase: AgentOrchestratorUseCase

    @Inject
    lateinit var llmEngine: LlmInferenceEngine

    private val serviceScope = CoroutineScope(Dispatchers.Main + Job())
    private lateinit var idleManager: AgentIdleManager

    companion object {
        private const val NOTIFICATION_CHANNEL_ID = "AgentForegroundServiceChannel"
        private const val NOTIFICATION_ID = 101
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        startForegroundServiceWithNotification("Initializing...")

        idleManager = AgentIdleManager(
            scope = serviceScope,
            engine = llmEngine,
            agentState = agentOrchestratorUseCase.globalState
        )
        idleManager.startObserving()

        serviceScope.launch {
            agentOrchestratorUseCase.globalState.collectLatest { state ->
                updateNotification(getStatusTextForState(state))
            }
        }
    }

    private fun getStatusTextForState(state: AgentOrchestratorState): String {
        return when (state) {
            is AgentOrchestratorState.Idle -> "Agent is waiting"
            is AgentOrchestratorState.Loading -> "Loading context..."
            is AgentOrchestratorState.Thinking -> "Agent is thinking..."
            is AgentOrchestratorState.ExecutingTool -> "Using tool: ${state.toolName}..."
            is AgentOrchestratorState.WaitingForApproval -> "Awaiting user confirmation..."
            is AgentOrchestratorState.ObservationResult -> "Processing tool result..."
            is AgentOrchestratorState.Answering -> "Answering..."
            is AgentOrchestratorState.Completed -> "Task completed"
            is AgentOrchestratorState.Error -> "Error: ${state.message}"
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                NOTIFICATION_CHANNEL_ID,
                "Agent Service",
                NotificationManager.IMPORTANCE_LOW
            )
            channel.description = "Keeps the AI Agent running in the background."
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }

    private fun startForegroundServiceWithNotification(status: String) {
        val notification = buildNotification(status)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(
                NOTIFICATION_ID, 
                notification, 
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
            )
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIFICATION_ID, notification)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun updateNotification(status: String) {
        val notification = buildNotification(status)
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(NOTIFICATION_ID, notification)
    }

    private fun buildNotification(status: String): Notification {
        return NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
            .setContentTitle("Android AI Agent")
            .setContentText(status)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setOngoing(true)
            .build()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
        if (llmEngine.isInitialized) {
            llmEngine.close()
        }
    }
}
