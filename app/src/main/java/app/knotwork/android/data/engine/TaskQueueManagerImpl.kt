package app.knotwork.android.data.engine

import androidx.annotation.VisibleForTesting
import app.knotwork.android.domain.engine.GraphExecutionEngine
import app.knotwork.android.domain.engine.TaskQueueManager
import app.knotwork.android.domain.models.AgentOrchestratorState
import app.knotwork.android.domain.models.AgentTask
import app.knotwork.android.domain.models.ChatMessage
import app.knotwork.android.domain.models.Role
import app.knotwork.android.domain.repositories.ChatRepository
import app.knotwork.android.domain.repositories.PipelineRepository
import app.knotwork.android.domain.repositories.SettingsRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
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
    private val settingsRepository: SettingsRepository,
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

    /**
     * Per-session event streams — one [MutableSharedFlow] per active chat
     * session, capped by [MAX_SESSION_STATES] and pruned via
     * [evictOldestTerminalSession]. Modelled as a `SharedFlow` (not the
     * earlier `StateFlow`) so the engine's tight-sequence emits never get
     * conflated: emits like `PipelineTrace` immediately followed by
     * `NodeIO` would previously overwrite each other inside the state
     * flow and only one would reach the chat-home collector — depriving
     * the console pane of every event that wasn't the latest. The
     * `replay = 1` keeps the legacy "latest state on subscription"
     * semantics; `extraBufferCapacity` ([CONSOLE_EVENT_BUFFER_CAPACITY])
     * absorbs the longest engine burst observed in practice (memory
     * retrieval + per-node start/end console events for a 12-node graph
     * fit comfortably).
     */
    @VisibleForTesting
    internal val sessionStates = LinkedHashMap<String, MutableSharedFlow<AgentOrchestratorState>>()

    companion object {
        @VisibleForTesting
        internal const val MAX_SESSION_STATES = 20

        /**
         * Buffer capacity for [sessionStates] entries — large enough to
         * hold every intermediate emit of the longest pipeline run
         * observed in practice without back-pressuring the engine.
         */
        @VisibleForTesting
        internal const val CONSOLE_EVENT_BUFFER_CAPACITY = 256
    }

    private fun updateActiveSessionsState() {
        // Per-session flow is a SharedFlow now; the latest value lives in
        // the replay cache (size 1) rather than under `.value`.
        val currentState = sessionStates.mapValues { entry ->
            entry.value.replayCache.lastOrNull() ?: AgentOrchestratorState.Idle
        }
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
        stateFlow.emit(loadingState)
        _globalState.value = loadingState

        // 1. Save user message
        val userMessage = ChatMessage(
            sessionId = task.sessionId,
            role = Role.USER,
            content = task.prompt,
            timestamp = System.currentTimeMillis(),
        )
        chatRepository.saveMessage(userMessage)

        // 2. Load pipeline. Resolution is a deterministic chain that never
        // depends on the order pipelines come back from the repository:
        //   1. `task.pipelineId` — the session binding captured at enqueue
        //      time, when it still resolves to an existing pipeline;
        //   2. `SettingsRepository.defaultPipelineId` — the user-marked
        //      default, when set and still existing;
        //   3. explicit `Error` — no silent "whatever the DAO returned
        //      first" substitution.
        // A bound pipeline deleted while the task waited in the queue falls
        // through to the default; the chat-level UI handles the rebind +
        // Snackbar notification separately, so the fall-through is never
        // silent from the user's perspective.
        val pipelines = pipelineRepository.getAllPipelines().firstOrNull() ?: emptyList()
        if (pipelines.isEmpty()) {
            val errState = AgentOrchestratorState.Error(
                "No active pipeline found. Please create one in the Visual Orchestrator.",
            )
            stateFlow.emit(errState)
            _globalState.value = errState
            return
        }
        val boundPipeline = task.pipelineId?.let { id -> pipelines.firstOrNull { it.id == id } }
        // The default is resolved lazily — reading the settings flow is a
        // suspend call that a successfully resolved binding never needs.
        val activePipeline = boundPipeline
            ?: settingsRepository.defaultPipelineId.firstOrNull()
                ?.let { id -> pipelines.firstOrNull { it.id == id } }

        if (activePipeline == null) {
            val errState = AgentOrchestratorState.Error(
                "No default pipeline configured. Set one in Settings or bind a pipeline to this chat.",
            )
            stateFlow.emit(errState)
            _globalState.value = errState
            return
        }

        // 3. Delegate to engine
        try {
            graphExecutionEngine(task.sessionId, task.prompt, activePipeline).collect { state ->
                // `emit` (vs. `tryEmit`) back-pressures the engine if the
                // buffer ever fills, so we never silently drop an event.
                stateFlow.emit(state)
                _globalState.value = state
            }
        } catch (e: CancellationException) {
            // Cancellation (user Stop, scope teardown) is not a failure:
            // mapping it to `Error` would both surface a false error banner
            // and break cooperative cancellation. Re-throw so the worker
            // coroutine dies with its scope; the `finally` below still
            // resets the session state.
            throw e
        } catch (e: Exception) {
            val errState = AgentOrchestratorState.Error(e.message ?: "Execution failed")
            stateFlow.emit(errState)
            _globalState.value = errState
        } finally {
            // Runs on the cancellation path too, where the coroutine's job
            // is already cancelled — `NonCancellable` lets the suspending
            // `emit` reset the session to `Idle` instead of immediately
            // re-throwing and leaving the UI stuck on a stale state.
            withContext(NonCancellable) {
                val last = stateFlow.replayCache.lastOrNull()
                if (last !is AgentOrchestratorState.Completed &&
                    last !is AgentOrchestratorState.Error
                ) {
                    stateFlow.emit(AgentOrchestratorState.Idle)
                }
                _globalState.value = AgentOrchestratorState.Idle
            }
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
                val last = stateFlow.replayCache.lastOrNull()
                if (last == AgentOrchestratorState.Idle ||
                    last is AgentOrchestratorState.Completed ||
                    last is AgentOrchestratorState.Error
                ) {
                    stateFlow.emit(AgentOrchestratorState.Loading)
                    updateActiveSessionsState()
                }
                taskQueue.offer(task)
            }
            taskSignal.trySend(Unit)
        }
    }

    override fun observeTaskState(sessionId: String): Flow<AgentOrchestratorState> =
        getOrCreateStateFlow(sessionId).asSharedFlow()

    override fun resumeWithApproval(sessionId: String, isApproved: Boolean) {
        graphExecutionEngine.resumeWithApproval(sessionId, isApproved)
    }

    private fun getOrCreateStateFlow(sessionId: String): MutableSharedFlow<AgentOrchestratorState> {
        synchronized(sessionStates) {
            return sessionStates.getOrPut(sessionId) {
                if (sessionStates.size >= MAX_SESSION_STATES) {
                    evictOldestTerminalSession()
                }
                MutableSharedFlow<AgentOrchestratorState>(
                    replay = 1,
                    extraBufferCapacity = CONSOLE_EVENT_BUFFER_CAPACITY,
                ).apply {
                    // Seed the replay cache with `Idle` so subscribers that
                    // attach before the engine emits its first state see
                    // the same initial value the legacy StateFlow used.
                    tryEmit(AgentOrchestratorState.Idle)
                }
            }
        }
    }

    // Evicts the oldest session whose state is terminal (Idle/Completed/Error).
    // If all sessions are still running, no eviction occurs — active flows are never dropped.
    private fun evictOldestTerminalSession() {
        val entry = sessionStates.entries.firstOrNull { (_, flow) ->
            val state = flow.replayCache.lastOrNull()
            state is AgentOrchestratorState.Idle ||
                state is AgentOrchestratorState.Completed ||
                state is AgentOrchestratorState.Error
        }
        entry?.let { sessionStates.remove(it.key) }
    }
}
