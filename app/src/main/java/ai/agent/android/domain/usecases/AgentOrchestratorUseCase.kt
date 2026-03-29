package ai.agent.android.domain.usecases

import ai.agent.android.domain.engine.GraphExecutionEngine
import ai.agent.android.domain.models.AgentOrchestratorState
import ai.agent.android.domain.models.ChatMessage
import ai.agent.android.domain.models.Role
import ai.agent.android.domain.repositories.ChatRepository
import ai.agent.android.domain.repositories.PipelineRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.onEach
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Use case that orchestrates the AI Agent's execution flow.
 * It reads the user's input, loads the active pipeline from the repository,
 * and delegates the execution to the [GraphExecutionEngine].
 */
@Singleton
class AgentOrchestratorUseCase @Inject constructor(
    private val chatRepository: ChatRepository,
    private val pipelineRepository: PipelineRepository,
    private val graphExecutionEngine: GraphExecutionEngine
) {

    private val _globalState = MutableStateFlow<AgentOrchestratorState>(AgentOrchestratorState.Idle)
    val globalState: StateFlow<AgentOrchestratorState> = _globalState.asStateFlow()

    /**
     * Resumes the suspended execution cycle after the user has made a decision on tool usage.
     *
     * @param sessionId The session ID that was waiting for approval.
     * @param isApproved True if the user allowed the action, false otherwise.
     */
    fun resumeWithApproval(sessionId: String, isApproved: Boolean) {
        graphExecutionEngine.resumeWithApproval(sessionId, isApproved)
    }

    /**
     * Starts the orchestration cycle for a given user prompt.
     *
     * @param sessionId The current chat session ID.
     * @param userPrompt The new prompt from the user.
     * @return A [Flow] of [AgentOrchestratorState] emitting the progress of the agent.
     */
    operator fun invoke(sessionId: String, userPrompt: String): Flow<AgentOrchestratorState> = flow<AgentOrchestratorState> {
        emit(AgentOrchestratorState.Loading)

        // 1. Save the user's message
        val userMessage = ChatMessage(
            sessionId = sessionId,
            role = Role.USER,
            content = userPrompt,
            timestamp = System.currentTimeMillis()
        )
        chatRepository.saveMessage(userMessage)

        // 2. Load the active pipeline
        val pipelines = pipelineRepository.getAllPipelines().firstOrNull() ?: emptyList()
        val activePipeline = pipelines.firstOrNull()

        if (activePipeline == null) {
            emit(AgentOrchestratorState.Error("No active pipeline found. Please create one in the Visual Orchestrator."))
            return@flow
        }

        // 3. Delegate execution to GraphExecutionEngine
        graphExecutionEngine(sessionId, userPrompt, activePipeline).collect { state ->
            emit(state)
        }

    }.onEach { state ->
        _globalState.value = state
    }.onCompletion {
        _globalState.value = AgentOrchestratorState.Idle
    }
}
