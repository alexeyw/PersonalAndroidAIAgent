package app.knotwork.android.data.services

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.IBinder
import android.os.PowerManager
import androidx.annotation.VisibleForTesting
import androidx.core.app.NotificationCompat
import androidx.work.WorkManager
import app.knotwork.android.domain.constants.NotificationChannels
import app.knotwork.android.domain.engine.LlmInferenceEngine
import app.knotwork.android.domain.models.AgentOrchestratorState
import app.knotwork.android.domain.repositories.PowerStateRepository
import app.knotwork.android.domain.services.MemoryReembedScheduler
import app.knotwork.android.domain.usecases.AgentOrchestratorUseCase
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

    /**
     * Re-arms the background memory re-embed pass on startup when chunks remain
     * flagged — covers the case where the OS restarts this service without
     * `MainActivity` ever being created.
     */
    @Inject
    lateinit var memoryReembedScheduler: MemoryReembedScheduler

    private val serviceScope = CoroutineScope(Dispatchers.Main + Job())
    private lateinit var idleManager: AgentIdleManager
    private lateinit var powerManager: AgentPowerManager
    private var wakeLock: PowerManager.WakeLock? = null

    companion object {
        private const val NOTIFICATION_ID = 101
        private const val WAKE_LOCK_TAG = "AndroidAIAgent:InferenceLock"
        private const val WAKE_LOCK_TIMEOUT_MS = 10 * 60 * 1000L

        /**
         * `true` while an instance of this service is alive (between [onCreate]
         * and [onDestroy]).
         *
         * Lets headless background components — `AgentWorker` running in a
         * process the user never opened — decide who owns post-run engine
         * cleanup: when the service is alive its [AgentIdleManager] unloads the
         * model after the idle timeout, so the worker must leave the engine
         * alone; when it is not, nothing else would ever release the model
         * memory and the worker unloads eagerly after its run finishes.
         * `@Volatile` because the flag is written on the main thread and read
         * from worker threads. The setter is internal (not private) solely so
         * tests can reset the process-wide flag between Robolectric classes,
         * which share a classloader.
         */
        @Volatile
        var isRunning: Boolean = false
            @VisibleForTesting
            internal set
    }

    /**
     * Called by the system when the service is first created.
     * Initializes the notification channel, WakeLock, idle manager, power manager,
     * and starts observing agent state.
     */
    override fun onCreate() {
        super.onCreate()
        isRunning = true
        createNotificationChannel()
        startForegroundServiceWithNotification("Initializing...")

        wakeLock = (getSystemService(Context.POWER_SERVICE) as PowerManager)
            .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, WAKE_LOCK_TAG)

        idleManager = AgentIdleManager(
            scope = serviceScope,
            engine = llmEngine,
            agentState = agentOrchestratorUseCase.globalState,
        )
        idleManager.startObserving()

        powerManager = AgentPowerManager(
            scope = serviceScope,
            powerStateRepository = powerStateRepository,
            engine = llmEngine,
            workManager = workManager,
        )
        powerManager.startObserving()

        // Self-heal the import re-embed pass even when the process is brought up
        // via the service rather than MainActivity (e.g. an OS service restart).
        serviceScope.launch(Dispatchers.IO) { memoryReembedScheduler.rearmIfPending() }

        serviceScope.launch {
            agentOrchestratorUseCase.globalState.collectLatest { state ->
                updateNotification(getStatusTextForState(state))
                when (state) {
                    is AgentOrchestratorState.Loading,
                    is AgentOrchestratorState.Thinking,
                    is AgentOrchestratorState.ExecutingTool,
                    -> acquireWakeLock()
                    is AgentOrchestratorState.Idle,
                    is AgentOrchestratorState.Completed,
                    is AgentOrchestratorState.Error,
                    -> releaseWakeLock()
                    else -> Unit
                }
            }
        }
    }

    /**
     * Acquires [wakeLock] if it is not already held, preventing the CPU from sleeping
     * during active inference or tool execution.
     */
    private fun acquireWakeLock() {
        if (wakeLock?.isHeld == false) {
            wakeLock?.acquire(WAKE_LOCK_TIMEOUT_MS)
        }
    }

    /**
     * Releases [wakeLock] if it is currently held, allowing the CPU to sleep
     * once inference completes or an error occurs.
     */
    private fun releaseWakeLock() {
        if (wakeLock?.isHeld == true) {
            wakeLock?.release()
        }
    }

    private fun getStatusTextForState(state: AgentOrchestratorState): String = when (state) {
        is AgentOrchestratorState.Idle -> "Agent is waiting"
        is AgentOrchestratorState.Loading -> "Loading context..."
        is AgentOrchestratorState.Thinking -> "Agent is thinking..."
        is AgentOrchestratorState.ExecutingTool -> "Using tool: ${state.toolName}..."
        is AgentOrchestratorState.WaitingForApproval -> "Awaiting user confirmation..."
        is AgentOrchestratorState.AwaitingClarification -> "Awaiting user clarification..."
        is AgentOrchestratorState.SuspendedInBackground -> "Waiting for user response in background"
        is AgentOrchestratorState.ObservationResult -> "Processing tool result..."
        is AgentOrchestratorState.Answering -> "Answering..."
        is AgentOrchestratorState.Completed -> "Task completed"
        is AgentOrchestratorState.Error -> "Error: ${state.message}"
        is AgentOrchestratorState.PipelineStage -> "Pipeline stage: ${state.stepInfo.nodeName}"
        is AgentOrchestratorState.PipelineTrace -> "Pipeline trace updated"
        is AgentOrchestratorState.ConsoleLog -> "Pipeline running..."
        is AgentOrchestratorState.NodeIO -> "Pipeline running..."
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            NotificationChannels.AGENT_FOREGROUND,
            "Agent Service",
            NotificationManager.IMPORTANCE_LOW,
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
            ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE,
        )
    }

    private fun updateNotification(status: String) {
        val notification = buildNotification(status)
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(NOTIFICATION_ID, notification)
    }

    private fun buildNotification(status: String): Notification =
        NotificationCompat.Builder(this, NotificationChannels.AGENT_FOREGROUND)
            .setContentTitle("Android AI Agent")
            .setContentText(status)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setOngoing(true)
            .build()

    /**
     * Called by the system every time a client explicitly starts the service.
     *
     * @param intent The Intent supplied to [android.content.Context.startService].
     * @param flags Additional data about this start request.
     * @param startId A unique integer representing this specific request to start.
     * @return The return value indicates what semantics the system should use for the service's current started state.
     */
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int = START_STICKY

    /**
     * Return the communication channel to the service. This service does not support binding.
     *
     * @param intent The Intent that was used to bind to this service.
     * @return null as binding is not supported.
     */
    override fun onBind(intent: Intent?): IBinder? = null

    /**
     * Called by the system to notify a Service that it is no longer used and is being removed.
     * Cleans up coroutines and the LLM engine.
     */
    override fun onDestroy() {
        super.onDestroy()
        isRunning = false
        releaseWakeLock()
        serviceScope.cancel()
        if (llmEngine.isInitialized) {
            llmEngine.close()
        }
    }
}
