package ai.agent.android.domain.engine.executors

import ai.agent.android.domain.engine.LlmInferenceEngine
import ai.agent.android.domain.models.AgentOrchestratorState
import ai.agent.android.domain.models.NodeExecutionResult
import ai.agent.android.domain.models.NodeModel
import ai.agent.android.domain.models.NodeType
import ai.agent.android.domain.models.Result
import ai.agent.android.domain.usecases.LoadModelUseCase
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class OutputNodeExecutorTest {
    @Test
    fun `execute emits Completed state and result without system prompt`() = runTest {
        val llmEngine = mockk<LlmInferenceEngine>()
        val loadModelUseCase = mockk<LoadModelUseCase>()
        val executor = OutputNodeExecutor(llmEngine, loadModelUseCase)
        val node = NodeModel("1", NodeType.OUTPUT, 0f, 0f, systemPrompt = null)
        
        val results = executor.execute(node, "final text", "session-1", "prompt").toList()
        
        assertEquals(2, results.size)
        assertTrue(results[0] is AgentOrchestratorState.Completed)
        assertEquals("final text", (results[0] as AgentOrchestratorState.Completed).finalResponse)
        
        assertTrue(results[1] is NodeExecutionResult)
        assertEquals("final text", (results[1] as NodeExecutionResult).outputText)
    }
    
    @Test
    fun `execute uses llm if system prompt is present`() = runTest {
        val llmEngine = mockk<LlmInferenceEngine>()
        val loadModelUseCase = mockk<LoadModelUseCase>()
        coEvery { loadModelUseCase(any()) } returns Result.Success(Unit)
        every { llmEngine.generateResponseStream(any()) } returns flowOf("Formatted Response")
        
        val executor = OutputNodeExecutor(llmEngine, loadModelUseCase)
        val node = NodeModel("1", NodeType.OUTPUT, 0f, 0f, systemPrompt = "Format please:")
        
        val results = executor.execute(node, "final text", "session-1", "prompt").toList()
        
        assertEquals(3, results.size) // Thinking, Completed, NodeExecutionResult
        assertTrue(results[0] is AgentOrchestratorState.Thinking)
        assertTrue(results[1] is AgentOrchestratorState.Completed)
        assertEquals("Formatted Response", (results[1] as AgentOrchestratorState.Completed).finalResponse)
    }
}