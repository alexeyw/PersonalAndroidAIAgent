package ai.agent.android.data.engine

import ai.agent.android.domain.engine.GraphExecutionEngine
import ai.agent.android.domain.engine.TaskQueueManager
import ai.agent.android.domain.models.AgentOrchestratorState
import ai.agent.android.domain.models.AgentTask
import ai.agent.android.domain.models.ChatMessage
import ai.agent.android.domain.models.Role
import ai.agent.android.domain.repositories.ChatRepository
import ai.agent.android.domain.repositories.PipelineRepository
import androidx.annotation.VisibleForTesting
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.PriorityQueue
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Implementation of [TaskQueueManager] that manages agent tasks in a priority queue.
 * Handles task execution via [GraphExecutionEngine] and state tracking.
 * Operations are thread-safe and atomized to prevent race conditions.
 */
@Singleton
class TaskQueueManagerImpl @Inject constructor(
    private val chatRepository: ChatRepository,
    private val pipelineRepository: PipelineRepository,
    private val graphExecutionEngine: GraphExecutionEngine,
) : TaskQueueManager {

    @VisibleForTesting
    internal var dispatcher: kotlinx.coroutines.CoroutineDispatcher = Dispatchers.IO
        set(value) {
            field = value
            scope.cancel()
            scope = CoroutineScope(value + SupervisorJob())
            startWorker()
        }

    internal var scope = CoroutineScope(dispatcher + SupervisorJob())

    private val _globalState = MutableStateFlow<AgentOrchestratorState>(AgentOrchestratorState.Idle)
    override val globalState: StateFlow<AgentOrchestratorState> = _globalState.asStateFlow()

    // Priority queue for tasks
    private val queueMutex = Mutex()
    private val taskQueue = PriorityQueue<AgentTask> { t1, t2 ->
        val p1 = t1.priority.ordinal
        val p2 = t2.priority.ordinal
        if (p1 != p2) p1.compareTo(p2) else t1.timestamp.compareTo(t2.timestamp)
    }

    // Channel to signal new tasks
    private val taskSignal = Channel<Unit>(Channel.CONFLATED)

    private val _activeSessionsState = MutableStateFlow<Map<String, AgentOrchestratorState>>(emptyMap())
    override val activeSessionsState: StateFlow<Map<String, AgentOrchestratorState>> =
        _activeSessionsState.asStateFlow()

    // State flows per session — capped by evicting only terminal (non-running) sessions
    @VisibleForTesting
    internal val sessionStates = LinkedHashMap<String, MutableStateFlow<AgentOrchestratorState>>()

    companion object {
        @VisibleForTesting
        internal const val MAX_SESSION_STATES = 20
    }

    private fun updateActiveSessionsState() {
        val currentState = sessionStates.mapValues { it.value.value }
        _activeSessionsState.value = currentState
    }

    init {
        startWorker()
    }

    internal fun startWorker() {
        scope.launch {
            for (signal in taskSignal) {
                while (true) {
                    val task = queueMutex.withLock {
                        taskQueue.poll()
                    } ?: break // Exit inner loop when queue is empty

                    processTask(task)
                }
            }
        }
    }

    private suspend fun processTask(task: AgentTask) {
        val stateFlow = getOrCreateStateFlow(task.sessionId)
        val loadingState = AgentOrchestratorState.Loading
        stateFlow.value = loadingState
        _globalState.value = loadingState

        // 1. Save user message
        val userMessage = ChatMessage(
            sessionId = task.sessionId,
            role = Role.USER,
            content = task.prompt,
            timestamp = System.currentTimeMillis(),
        )
        chatRepository.saveMessage(userMessage)

        // 2. Load pipeline. Phase 17.2: each chat session may bind its own
        // `pipelineId`; honour that binding first, then fall back to the
        // application-wide default (the first pipeline returned by the
        // repository) for legacy chats and for tasks that don't specify a
        // binding. If the bound pipeline was deleted while the task waited
        // in the queue we silently fall back to the default rather than
        // failing — the chat-level UI handles the deleted-pipeline rebind
        // separately (Phase 17.2 Snackbar fallback).
        val pipelines = pipelineRepository.getAllPipelines().firstOrNull() ?: emptyList()
        val boundPipeline = task.pipelineId?.let { id -> pipelines.firstOrNull { it.id == id } }
        val activePipeline = boundPipeline ?: pipelines.firstOrNull()

        if (activePipeline == null) {
            val errState = AgentOrchestratorState.Error(
                "No active pipeline found. Please create one in the Visual Orchestrator.",
            )
            stateFlow.value = errState
            _globalState.value = errState
            return
        }

        // 3. Delegate to engine
        try {
            graphExecutionEngine(task.sessionId, task.prompt, activePipeline).collect { state ->
                stateFlow.value = state
                _globalState.value = state
            }
        } catch (e: Exception) {
            val errState = AgentOrchestratorState.Error(e.message ?: "Execution failed")
            stateFlow.value = errState
            _globalState.value = errState
        } finally {
            if (stateFlow.value !is AgentOrchestratorState.Completed &&
                stateFlow.value !is AgentOrchestratorState.Error
            ) {
                stateFlow.value = AgentOrchestratorState.Idle
            }
            _globalState.value = AgentOrchestratorState.Idle
        }
    }

    /**
     * Enqueues a new [AgentTask] for execution.
     * Atomically checks the current session state and adds the task to the queue
     * to prevent race conditions during state updates.
     *
     * @param task The task to be executed.
     */
    override fun enqueueTask(task: AgentTask) {
        scope.launch {
            queueMutex.withLock {
                val stateFlow = getOrCreateStateFlow(task.sessionId)
                if (stateFlow.value == AgentOrchestratorState.Idle ||
                    stateFlow.value is AgentOrchestratorState.Completed ||
                    stateFlow.value is AgentOrchestratorState.Error
                ) {
                    stateFlow.value = AgentOrchestratorState.Loading
                    updateActiveSessionsState()
                }
                taskQueue.offer(task)
            }
            taskSignal.trySend(Unit)
        }
    }

    override fun observeTaskState(sessionId: String): Flow<AgentOrchestratorState> =
        getOrCreateStateFlow(sessionId).asStateFlow()

    override fun resumeWithApproval(sessionId: String, isApproved: Boolean) {
        graphExecutionEngine.resumeWithApproval(sessionId, isApproved)
    }

    private fun getOrCreateStateFlow(sessionId: String): MutableStateFlow<AgentOrchestratorState> {
        synchronized(sessionStates) {
            return sessionStates.getOrPut(sessionId) {
                if (sessionStates.size >= MAX_SESSION_STATES) {
                    evictOldestTerminalSession()
                }
                MutableStateFlow(AgentOrchestratorState.Idle)
            }
        }
    }

    // Evicts the oldest session whose state is terminal (Idle/Completed/Error).
    // If all sessions are still running, no eviction occurs — active flows are never dropped.
    private fun evictOldestTerminalSession() {
        val entry = sessionStates.entries.firstOrNull { (_, flow) ->
            val state = flow.value
            state is AgentOrchestratorState.Idle ||
                state is AgentOrchestratorState.Completed ||
                state is AgentOrchestratorState.Error
        }
        entry?.let { sessionStates.remove(it.key) }
    }
}
