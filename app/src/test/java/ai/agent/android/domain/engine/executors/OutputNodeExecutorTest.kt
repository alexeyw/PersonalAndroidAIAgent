package ai.agent.android.domain.engine.executors

import ai.agent.android.domain.models.AgentOrchestratorState
import ai.agent.android.domain.models.NodeExecutionResult
import ai.agent.android.domain.models.NodeModel
import ai.agent.android.domain.models.NodeType
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class OutputNodeExecutorTest {
    @Test
    fun `execute emits Completed state and result`() = runTest {
        val executor = OutputNodeExecutor()
        val node = NodeModel("1", NodeType.OUTPUT, 0f, 0f)
        
        val results = executor.execute(node, "final text", "session-1", "prompt").toList()
        
        assertEquals(2, results.size)
        assertTrue(results[0] is AgentOrchestratorState.Completed)
        assertEquals("final text", (results[0] as AgentOrchestratorState.Completed).finalResponse)
        
        assertTrue(results[1] is NodeExecutionResult)
        assertEquals("final text", (results[1] as NodeExecutionResult).outputText)
    }
}