package ai.agent.android.data.services

import ai.agent.android.domain.engine.LlmInferenceEngine
import ai.agent.android.domain.models.AgentOrchestratorState
import ai.agent.android.domain.repositories.PowerStateRepository
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
import androidx.work.WorkManager
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

    /**
     * Use case for managing the global state and execution flow of the agent.
     */
    @Inject
    lateinit var agentOrchestratorUseCase: AgentOrchestratorUseCase

    /**
     * The engine responsible for local LLM inference.
     */
    @Inject
    lateinit var llmEngine: LlmInferenceEngine

    /**
     * Repository for managing device power state and keeping the CPU awake.
     */
    @Inject
    lateinit var powerStateRepository: PowerStateRepository

    /**
     * Manager for scheduling background work and tasks.
     */
    @Inject
    lateinit var workManager: WorkManager

    private val serviceScope = CoroutineScope(Dispatchers.Main + Job())
    private lateinit var idleManager: AgentIdleManager
    private lateinit var powerManager: AgentPowerManager

    companion object {
        private const val NOTIFICATION_CHANNEL_ID = "AgentForegroundServiceChannel"
        private const val NOTIFICATION_ID = 101
    }

    /**
     * Called by the system when the service is first created.
     * Initializes the notification channel, idle manager, power manager, and starts observing state.
     */
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

        powerManager = AgentPowerManager(
            scope = serviceScope,
            powerStateRepository = powerStateRepository,
            engine = llmEngine,
            workManager = workManager
        )
        powerManager.startObserving()

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
            is AgentOrchestratorState.PipelineStage -> "Pipeline stage: ${state.nodeName}"
            is AgentOrchestratorState.PipelineTrace -> "Pipeline trace updated"
        }
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            NOTIFICATION_CHANNEL_ID,
            "Agent Service",
            NotificationManager.IMPORTANCE_LOW
        )
        channel.description = "Keeps the AI Agent running in the background."
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.createNotificationChannel(channel)
    }

    private fun startForegroundServiceWithNotification(status: String) {
        val notification = buildNotification(status)
        startForeground(
            NOTIFICATION_ID, 
            notification, 
            ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
        )
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

    /**
     * Called by the system every time a client explicitly starts the service.
     * 
     * @param intent The Intent supplied to [android.content.Context.startService].
     * @param flags Additional data about this start request.
     * @param startId A unique integer representing this specific request to start.
     * @return The return value indicates what semantics the system should use for the service's current started state.
     */
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return START_STICKY
    }

    /**
     * Return the communication channel to the service. This service does not support binding.
     * 
     * @param intent The Intent that was used to bind to this service.
     * @return null as binding is not supported.
     */
    override fun onBind(intent: Intent?): IBinder? {
        return null
    }

    /**
     * Called by the system to notify a Service that it is no longer used and is being removed.
     * Cleans up coroutines and the LLM engine.
     */
    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
        if (llmEngine.isInitialized) {
            llmEngine.close()
        }
    }
}
