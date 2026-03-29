package ai.agent.android.domain.usecases

import ai.agent.android.domain.engine.GraphExecutionEngine
import ai.agent.android.domain.models.AgentOrchestratorState
import ai.agent.android.domain.models.PipelineGraph
import ai.agent.android.domain.repositories.ChatRepository
import ai.agent.android.domain.repositories.PipelineRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class AgentOrchestratorUseCaseTest {

    private lateinit var chatRepository: ChatRepository
    private lateinit var pipelineRepository: PipelineRepository
    private lateinit var graphExecutionEngine: GraphExecutionEngine
    private lateinit var useCase: AgentOrchestratorUseCase

    private val sessionId = "test-session"

    @Before
    fun setup() {
        chatRepository = mockk(relaxed = true)
        pipelineRepository = mockk()
        graphExecutionEngine = mockk(relaxed = true)
        useCase = AgentOrchestratorUseCase(chatRepository, pipelineRepository, graphExecutionEngine)
    }

    @Test
    fun `emits error when no active pipeline exists`() = runTest {
        val userPrompt = "Hello"

        every { pipelineRepository.getAllPipelines() } returns flowOf(emptyList())

        val states = useCase(sessionId, userPrompt).toList()

        assertTrue(states[0] is AgentOrchestratorState.Loading)
        assertTrue(states[1] is AgentOrchestratorState.Error)
        assertEquals("No active pipeline found. Please create one in the Visual Orchestrator.", (states[1] as AgentOrchestratorState.Error).message)

        coVerify { chatRepository.saveMessage(match { it.content == userPrompt }) }
    }

    @Test
    fun `delegates execution to GraphExecutionEngine`() = runTest {
        val userPrompt = "Hello"
        val activePipeline = PipelineGraph(id = "1", name = "Test")

        every { pipelineRepository.getAllPipelines() } returns flowOf(listOf(activePipeline))
        every { graphExecutionEngine.invoke(sessionId, userPrompt, activePipeline) } returns flowOf(
            AgentOrchestratorState.Completed("Done")
        )

        val states = useCase(sessionId, userPrompt).toList()

        assertTrue(states[0] is AgentOrchestratorState.Loading)
        assertTrue(states[1] is AgentOrchestratorState.Completed)
        assertEquals("Done", (states[1] as AgentOrchestratorState.Completed).finalResponse)
    }

    @Test
    fun `resumeWithApproval delegates to GraphExecutionEngine`() {
        useCase.resumeWithApproval(sessionId, true)
        verify { graphExecutionEngine.resumeWithApproval(sessionId, true) }
    }
}
