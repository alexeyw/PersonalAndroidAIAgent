package ai.agent.android.domain.engine

import ai.agent.android.data.engine.KoogClientFactory
import ai.agent.android.domain.engine.executors.*
import ai.agent.android.domain.models.*
import ai.agent.android.domain.repositories.*
import ai.agent.android.domain.services.ApprovalNotifier
import ai.agent.android.domain.usecases.EvaluateIfConditionUseCase
import ai.agent.android.domain.usecases.GetContextWindowUseCase
import ai.agent.android.domain.usecases.RetrieveRelevantMemoryUseCase
import ai.agent.android.domain.usecases.LoadModelUseCase
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
    private lateinit var apiKeyRepository: ApiKeyRepository
    private lateinit var metricsRepository: MetricsRepository
    private lateinit var approvalNotifier: ApprovalNotifier
    private lateinit var koogClientFactory: KoogClientFactory
    private lateinit var evaluateIfConditionUseCase: EvaluateIfConditionUseCase
    private lateinit var loadModelUseCase: LoadModelUseCase
    
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
        apiKeyRepository = mockk(relaxed = true)
        metricsRepository = mockk(relaxed = true)
        approvalNotifier = mockk(relaxed = true)
        koogClientFactory = mockk()
        evaluateIfConditionUseCase = mockk()
        loadModelUseCase = mockk()

        val inputNodeExecutor = InputNodeExecutor()
        val outputNodeExecutor = OutputNodeExecutor(llmEngine, loadModelUseCase)
        val ifConditionNodeExecutor = IfConditionNodeExecutor(evaluateIfConditionUseCase)
        val queueProcessorNodeExecutor = QueueProcessorNodeExecutor()
        
        val toolNodeExecutor = ToolNodeExecutor(
            toolRepository, settingsRepository, approvalNotifier, chatRepository
        )
        
        val liteRtNodeExecutor = LiteRtNodeExecutor(
            llmEngine, toolRepository, chatRepository, getContextWindowUseCase,
            retrieveRelevantMemoryUseCase, settingsRepository, metricsRepository, loadModelUseCase
        )
        
        val cloudLlmNodeExecutor = CloudLlmNodeExecutor(
            toolRepository, chatRepository, getContextWindowUseCase,
            retrieveRelevantMemoryUseCase, settingsRepository, apiKeyRepository,
            metricsRepository, koogClientFactory
        )
        
        val systemNodeExecutor = SystemNodeExecutor(
            llmEngine, loadModelUseCase, chatRepository
        )

        val nodeExecutorFactory = NodeExecutorFactory(
            inputNodeExecutor, outputNodeExecutor, ifConditionNodeExecutor,
            toolNodeExecutor, liteRtNodeExecutor, cloudLlmNodeExecutor,
            systemNodeExecutor, queueProcessorNodeExecutor
        )

        engine = GraphExecutionEngine(nodeExecutorFactory, toolNodeExecutor)

        coEvery { getContextWindowUseCase(sessionId) } returns ""
        coEvery { retrieveRelevantMemoryUseCase(any()) } returns emptyList()
        every { settingsRepository.systemPromptPrefix } returns flowOf("")
        every { settingsRepository.toolUsageInstruction } returns flowOf("")
        every { settingsRepository.requiresUserConfirmation } returns flowOf(false)
        coEvery { toolRepository.getAvailableTools() } returns emptyList()
        
        coEvery { loadModelUseCase(any()) } returns ai.agent.android.domain.models.Result.Success(Unit)
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
        every { llmEngine.generateResponseStream(any()) } returns flowOf("Test prompt")

        val statesTrue = engine(sessionId, "Test prompt", graph).toList()
        assertTrue(statesTrue.last() is AgentOrchestratorState.Completed)
        
        // Output text is preserved as input text across nodes if not modified
        assertEquals("Test prompt", (statesTrue.last() as AgentOrchestratorState.Completed).finalResponse)
    }

    @Test
    fun `output node uses systemPrompt and llmEngine if prompt is provided`() = runTest {
        val inputNode = NodeModel("input_1", NodeType.INPUT, 0f, 0f)
        val outputNode = NodeModel("output_1", NodeType.OUTPUT, 0f, 0f, systemPrompt = "Format this text:")

        val graph = PipelineGraph(
            id = "g1", name = "Output Test",
            nodes = listOf(inputNode, outputNode),
            connections = listOf(
                ConnectionModel("c1", "input_1", "output_1")
            )
        )

        every { llmEngine.generateResponseStream(any()) } returns flowOf("Formatted Response")

        val states = engine(sessionId, "Raw User Input", graph).toList()
        
        val completedState = states.last() as AgentOrchestratorState.Completed
        assertEquals("Formatted Response", completedState.finalResponse)
        
        io.mockk.verify { llmEngine.generateResponseStream(match { it.contains("Format this text:") && it.contains("Raw User Input") }) }
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

    @Test
    fun `emits Error if pipeline ends without reaching OUTPUT`() = runTest {
        val inputNode = NodeModel("input_1", NodeType.INPUT, 0f, 0f)
        val llmNode = NodeModel("llm_1", NodeType.LITE_RT, 0f, 0f)

        val graph = PipelineGraph(
            id = "g1", name = "Incomplete",
            nodes = listOf(inputNode, llmNode),
            connections = listOf(
                ConnectionModel("c1", "input_1", "llm_1")
                // No connection to an output node
            )
        )
        
        every { llmEngine.generateResponseStream(any()) } returns flowOf("Response")

        val states = engine(sessionId, "Test", graph).toList()
        
        val lastState = states.last()
        assertTrue(lastState is AgentOrchestratorState.Error)
        assertTrue((lastState as AgentOrchestratorState.Error).message.contains("without reaching OUTPUT"))
    }

    @Test
    fun `resumeWithApproval delegates to toolNodeExecutor`() {
        val mockToolNodeExecutor = mockk<ToolNodeExecutor>(relaxed = true)
        val mockFactory = mockk<NodeExecutorFactory>()
        val engineWithMock = GraphExecutionEngine(mockFactory, mockToolNodeExecutor)
        
        engineWithMock.resumeWithApproval("session_id_123", true)
        
        io.mockk.verify { mockToolNodeExecutor.resumeWithApproval("session_id_123", true) }
    }
}