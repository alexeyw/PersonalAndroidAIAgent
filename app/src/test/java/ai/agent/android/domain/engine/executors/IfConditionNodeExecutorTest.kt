package ai.agent.android.domain.engine.executors

import ai.agent.android.domain.models.NodeExecutionResult
import ai.agent.android.domain.models.NodeModel
import ai.agent.android.domain.models.NodeType
import ai.agent.android.domain.usecases.EvaluateIfConditionUseCase
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class IfConditionNodeExecutorTest {
    @Test
    fun `execute evaluates condition and returns result`() = runTest {
        val evaluateIfConditionUseCase: EvaluateIfConditionUseCase = mockk()
        val executor = IfConditionNodeExecutor(evaluateIfConditionUseCase)
        val node = NodeModel("1", NodeType.IF_CONDITION, 0f, 0f)

        coEvery { evaluateIfConditionUseCase(node, "test input") } returns true

        val results = executor.execute(node, "test input", "session-1", "prompt").toList().unwrap()

        assertEquals(1, results.size)
        val executionResult = results[0] as NodeExecutionResult
        assertEquals(true, executionResult.conditionResult)
        assertEquals("test input", executionResult.outputText)
    }
}
