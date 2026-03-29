package ai.agent.android.domain.engine

import ai.agent.android.data.engine.KoogClientFactory
import ai.agent.android.domain.models.*
import ai.agent.android.domain.repositories.*
import ai.agent.android.domain.services.ApprovalNotifier
import ai.agent.android.domain.usecases.EvaluateIfConditionUseCase
import ai.agent.android.domain.usecases.GetContextWindowUseCase
import ai.agent.android.domain.usecases.RetrieveRelevantMemoryUseCase
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class GraphExecutionEngineTest {

    private lateinit var llmEngine: LlmInferenceEngine
    private lateinit var toolRepository: ToolRepository
    private lateinit var chatRepository: ChatRepository
    private lateinit var getContextWindowUseCase: GetContextWindowUseCase
    private lateinit var retrieveRelevantMemoryUseCase: RetrieveRelevantMemoryUseCase
    private lateinit var settingsRepository: SettingsRepository
    private lateinit var metricsRepository: MetricsRepository
    private lateinit var approvalNotifier: ApprovalNotifier
    private lateinit var koogClientFactory: KoogClientFactory
    private lateinit var evaluateIfConditionUseCase: EvaluateIfConditionUseCase
    
    private lateinit var engine: GraphExecutionEngine

    private val sessionId = "test-session"

    @Before
    fun setup() {
        llmEngine = mockk()
        toolRepository = mockk(relaxed = true)
        chatRepository = mockk(relaxed = true)
        getContextWindowUseCase = mockk()
        retrieveRelevantMemoryUseCase = mockk()
        settingsRepository = mockk()
        metricsRepository = mockk(relaxed = true)
        approvalNotifier = mockk(relaxed = true)
        koogClientFactory = mockk()
        evaluateIfConditionUseCase = mockk()

        engine = GraphExecutionEngine(
            llmEngine, toolRepository, chatRepository, getContextWindowUseCase,
            retrieveRelevantMemoryUseCase, settingsRepository, metricsRepository,
            approvalNotifier, koogClientFactory, evaluateIfConditionUseCase
        )

        coEvery { getContextWindowUseCase(sessionId) } returns ""
        coEvery { retrieveRelevantMemoryUseCase(any()) } returns emptyList()
        every { settingsRepository.systemPromptPrefix } returns flowOf("")
        every { settingsRepository.toolUsageInstruction } returns flowOf("")
        every { settingsRepository.requiresUserConfirmation } returns flowOf(false)
        coEvery { toolRepository.getAvailableTools() } returns emptyList()
    }

    @Test
    fun `successful traversal from INPUT to OUTPUT`() = runTest {
        val inputNode = NodeModel("input_1", NodeType.INPUT, 0f, 0f)
        val llmNode = NodeModel("llm_1", NodeType.LITE_RT, 0f, 0f)
        val outputNode = NodeModel("output_1", NodeType.OUTPUT, 0f, 0f)

        val graph = PipelineGraph(
            id = "g1", name = "Test Graph",
            nodes = listOf(inputNode, llmNode, outputNode),
            connections = listOf(
                ConnectionModel("c1", "input_1", "llm_1"),
                ConnectionModel("c2", "llm_1", "output_1")
            )
        )

        every { llmEngine.generateResponseStream(any()) } returns flowOf("LLM ", "Response")

        val states = engine(sessionId, "User prompt", graph).toList()

        val completedState = states.last() as AgentOrchestratorState.Completed
        assertEquals("LLM Response", completedState.finalResponse)
    }

    @Test
    fun `evaluates IF_CONDITION and branches correctly`() = runTest {
        val inputNode = NodeModel("input_1", NodeType.INPUT, 0f, 0f)
        val ifNode = NodeModel("if_1", NodeType.IF_CONDITION, 0f, 0f)
        val outputTrue = NodeModel("out_true", NodeType.OUTPUT, 0f, 0f)
        val outputFalse = NodeModel("out_false", NodeType.OUTPUT, 0f, 0f)

        val graph = PipelineGraph(
            id = "g1", name = "Test Graph",
            nodes = listOf(inputNode, ifNode, outputTrue, outputFalse),
            connections = listOf(
                ConnectionModel("c1", "input_1", "if_1"),
                ConnectionModel("c2", "if_1", "out_true", label = "True"),
                ConnectionModel("c3", "if_1", "out_false", label = "False")
            )
        )

        // Evaluate to true
        coEvery { evaluateIfConditionUseCase(ifNode, "Test prompt") } returns true

        val statesTrue = engine(sessionId, "Test prompt", graph).toList()
        assertTrue(statesTrue.last() is AgentOrchestratorState.Completed)
        
        // Output text is preserved as input text across nodes if not modified
        assertEquals("Test prompt", (statesTrue.last() as AgentOrchestratorState.Completed).finalResponse)
    }

    @Test
    fun `prevents infinite cycles via DAG validation`() = runTest {
        val n1 = NodeModel("n1", NodeType.INPUT, 0f, 0f)
        val n2 = NodeModel("n2", NodeType.LITE_RT, 0f, 0f)
        
        val cyclicGraph = PipelineGraph(
            id = "g1", name = "Cyclic",
            nodes = listOf(n1, n2),
            connections = listOf(
                ConnectionModel("c1", "n1", "n2"),
                ConnectionModel("c2", "n2", "n1") // cycle back
            )
        )

        val states = engine(sessionId, "Test", cyclicGraph).toList()
        
        assertTrue(states.first() is AgentOrchestratorState.Error)
        assertTrue((states.first() as AgentOrchestratorState.Error).message.contains("cycles"))
    }
}
