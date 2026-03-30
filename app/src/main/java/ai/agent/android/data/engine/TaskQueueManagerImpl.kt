package ai.agent.android.data.engine

import ai.agent.android.domain.engine.GraphExecutionEngine
import ai.agent.android.domain.engine.TaskQueueManager
import ai.agent.android.domain.models.AgentOrchestratorState
import ai.agent.android.domain.models.AgentTask
import ai.agent.android.domain.models.ChatMessage
import ai.agent.android.domain.models.Role
import ai.agent.android.domain.repositories.ChatRepository
import ai.agent.android.domain.repositories.PipelineRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
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

@Singleton
class TaskQueueManagerImpl @Inject constructor(
    private val chatRepository: ChatRepository,
    private val pipelineRepository: PipelineRepository,
    private val graphExecutionEngine: GraphExecutionEngine
) : TaskQueueManager {

    private val scope = CoroutineScope(Dispatchers.IO + Job())
    
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

    // State flows per session
    private val sessionStates = mutableMapOf<String, MutableStateFlow<AgentOrchestratorState>>()

    init {
        startWorker()
    }

    private fun startWorker() {
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
            timestamp = System.currentTimeMillis()
        )
        chatRepository.saveMessage(userMessage)

        // 2. Load pipeline
        val pipelines = pipelineRepository.getAllPipelines().firstOrNull() ?: emptyList()
        val activePipeline = pipelines.firstOrNull()

        if (activePipeline == null) {
            val errState = AgentOrchestratorState.Error("No active pipeline found. Please create one in the Visual Orchestrator.")
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
            if (stateFlow.value !is AgentOrchestratorState.Completed && stateFlow.value !is AgentOrchestratorState.Error) {
                stateFlow.value = AgentOrchestratorState.Idle
            }
            _globalState.value = AgentOrchestratorState.Idle
        }
    }

    override fun enqueueTask(task: AgentTask) {
        scope.launch {
            val stateFlow = getOrCreateStateFlow(task.sessionId)
            if (stateFlow.value == AgentOrchestratorState.Idle || stateFlow.value is AgentOrchestratorState.Completed || stateFlow.value is AgentOrchestratorState.Error) {
                stateFlow.value = AgentOrchestratorState.Loading
            }
            queueMutex.withLock {
                taskQueue.offer(task)
            }
            taskSignal.trySend(Unit)
        }
    }

    override fun observeTaskState(sessionId: String): Flow<AgentOrchestratorState> {
        return getOrCreateStateFlow(sessionId).asStateFlow()
    }

    override fun resumeWithApproval(sessionId: String, isApproved: Boolean) {
        graphExecutionEngine.resumeWithApproval(sessionId, isApproved)
    }

    private fun getOrCreateStateFlow(sessionId: String): MutableStateFlow<AgentOrchestratorState> {
        synchronized(sessionStates) {
            return sessionStates.getOrPut(sessionId) {
                MutableStateFlow(AgentOrchestratorState.Idle)
            }
        }
    }
}
