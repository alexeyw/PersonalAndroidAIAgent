package app.knotwork.android.domain.engine.executors

import app.knotwork.android.domain.engine.GraphExecutionEngine
import app.knotwork.android.domain.models.AgentOrchestratorState
import app.knotwork.android.domain.models.ConnectionModel
import app.knotwork.android.domain.models.NodeModel
import app.knotwork.android.domain.models.NodeOutput
import app.knotwork.android.domain.models.NodeType
import app.knotwork.android.domain.models.PipelineGraph
import app.knotwork.android.domain.repositories.PipelineRepository
import app.knotwork.android.domain.repositories.SettingsRepository
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import javax.inject.Provider

/**
 * Unit tests for [PipelineNodeExecutor] — the recursive sub-pipeline executor.
 *
 * The recursive [GraphExecutionEngine] is mocked so each test controls exactly
 * what the sub-run emits, isolating the executor's own logic (target resolution,
 * depth ceiling, output/error mapping, depth threading).
 */
class PipelineNodeExecutorTest {

    private val pipelineRepository: PipelineRepository = mockk()
    private val settingsRepository: SettingsRepository = mockk()
    private val engine: GraphExecutionEngine = mockk()

    private val executor = PipelineNodeExecutor(
        pipelineRepository = pipelineRepository,
        settingsRepository = settingsRepository,
        engineProvider = Provider { engine },
    )

    private val subGraph = PipelineGraph(
        id = "sub",
        name = "Sub Pipeline",
        nodes = listOf(
            NodeModel("i", NodeType.INPUT, 0f, 0f),
            NodeModel("o", NodeType.OUTPUT, 1f, 1f),
        ),
        connections = listOf(ConnectionModel("c", "i", "o")),
    )

    private fun pipelineNode(targetPipelineId: String? = "sub") =
        NodeModel(id = "p", type = NodeType.PIPELINE, x = 0f, y = 0f, targetPipelineId = targetPipelineId)

    private fun stubEngine(result: Flow<AgentOrchestratorState>) {
        every { engine.invoke(any(), any(), any(), any(), any(), any()) } returns result
    }

    private suspend fun runExecutor(node: NodeModel, input: String = "hello", depth: Int = 0) =
        executor.execute(node, input, "session", "original", runId = null, depth = depth).toList()

    @Before
    fun setUp() {
        every { settingsRepository.pipelineMaxNestingDepth } returns flowOf(3)
    }

    private fun List<NodeOutput>.singleResult() = filterIsInstance<NodeOutput.Result>().single().result

    @Test
    fun `given resolvable target when execute then forwards sub-pipeline output`() = runTest {
        coEvery { pipelineRepository.getPipelineById("sub") } returns subGraph
        stubEngine(flowOf(AgentOrchestratorState.Completed("sub answer")))

        val result = runExecutor(pipelineNode()).singleResult()

        assertEquals("sub answer", result.outputText)
        assertNull(result.error)
    }

    @Test
    fun `given top-level run when execute then sub-pipeline runs one level deeper with node input as prompt`() =
        runTest {
            coEvery { pipelineRepository.getPipelineById("sub") } returns subGraph
            stubEngine(flowOf(AgentOrchestratorState.Completed("ok")))

            runExecutor(pipelineNode(), input = "carried input", depth = 0)

            // userPrompt = the node's input; runId = null; depth = parent + 1.
            verify {
                engine.invoke("session", "carried input", subGraph, null, null, 1)
            }
        }

    @Test
    fun `given blank target when execute then fails without touching repository`() = runTest {
        val result = runExecutor(pipelineNode(targetPipelineId = null)).singleResult()

        assertNull(result.outputText)
        assertTrue(result.error!!.contains("no target pipeline"))
        verify(exactly = 0) { engine.invoke(any(), any(), any(), any(), any(), any()) }
    }

    @Test
    fun `given missing target when execute then fails with not-found error`() = runTest {
        coEvery { pipelineRepository.getPipelineById("sub") } returns null

        val result = runExecutor(pipelineNode()).singleResult()

        assertTrue(result.error!!.contains("not found"))
        verify(exactly = 0) { engine.invoke(any(), any(), any(), any(), any(), any()) }
    }

    @Test
    fun `given depth one below the limit when execute then the sub-pipeline still runs`() = runTest {
        // limit = 3, current depth = 2 -> child depth = 3 -> allowed.
        coEvery { pipelineRepository.getPipelineById("sub") } returns subGraph
        stubEngine(flowOf(AgentOrchestratorState.Completed("deep ok")))

        val result = runExecutor(pipelineNode(), depth = 2).singleResult()

        assertEquals("deep ok", result.outputText)
        verify { engine.invoke(any(), any(), any(), any(), any(), 3) }
    }

    @Test
    fun `given depth at the limit when execute then refuses and does not recurse`() = runTest {
        // limit = 3, current depth = 3 -> child depth = 4 -> refused.
        val result = runExecutor(pipelineNode(), depth = 3).singleResult()

        assertTrue(result.error!!.contains("nesting depth"))
        verify(exactly = 0) { engine.invoke(any(), any(), any(), any(), any(), any()) }
        coVerifyNeverLoadsTarget()
    }

    @Test
    fun `given sub-pipeline error when execute then propagates the error`() = runTest {
        coEvery { pipelineRepository.getPipelineById("sub") } returns subGraph
        stubEngine(flowOf(AgentOrchestratorState.Error("sub boom")))

        val result = runExecutor(pipelineNode()).singleResult()

        assertEquals("sub boom", result.error)
        assertNull(result.outputText)
    }

    @Test
    fun `given sub-pipeline produces no terminal state when execute then reports no output`() = runTest {
        coEvery { pipelineRepository.getPipelineById("sub") } returns subGraph
        stubEngine(flowOf(AgentOrchestratorState.Loading))

        val result = runExecutor(pipelineNode()).singleResult()

        assertTrue(result.error!!.contains("no output"))
    }

    private fun coVerifyNeverLoadsTarget() {
        io.mockk.coVerify(exactly = 0) { pipelineRepository.getPipelineById(any()) }
    }
}
