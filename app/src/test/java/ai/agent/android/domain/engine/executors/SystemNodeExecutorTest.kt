package ai.agent.android.domain.engine.executors

import ai.agent.android.domain.engine.LlmInferenceEngine
import ai.agent.android.domain.models.AgentOrchestratorState
import ai.agent.android.domain.models.AppError
import ai.agent.android.domain.models.NodeExecutionResult
import ai.agent.android.domain.models.NodeModel
import ai.agent.android.domain.models.NodeType
import ai.agent.android.domain.models.Result
import ai.agent.android.domain.repositories.ChatRepository
import ai.agent.android.domain.usecases.LoadModelUseCase
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.coVerify
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class SystemNodeExecutorTest {

    private lateinit var llmEngine: LlmInferenceEngine
    private lateinit var loadModelUseCase: LoadModelUseCase
    private lateinit var chatRepository: ChatRepository
    private lateinit var executor: SystemNodeExecutor

    @Before
    fun setup() {
        llmEngine = mockk()
        loadModelUseCase = mockk()
        chatRepository = mockk(relaxed = true)
        executor = SystemNodeExecutor(llmEngine, loadModelUseCase, chatRepository)
    }

    @Test
    fun `execute emits Thinking and text on success`() = runTest {
        val node = NodeModel("1", NodeType.INTENT_ROUTER, 0f, 0f)
        
        coEvery { loadModelUseCase(any()) } returns ai.agent.android.domain.models.Result.Success(Unit)
        
        every { llmEngine.generateResponseStream(any()) } returns flowOf("Result")
        
        val states = executor.execute(node, "input", "session-1", "prompt").toList()
        
        assertTrue(states[0] is AgentOrchestratorState.Thinking)
        
        val result = states[1] as NodeExecutionResult
        assertEquals("Result", result.outputText)
        assertEquals("Result", result.routingKey)
        
        coVerify { chatRepository.saveMessage(any()) }
    }
}