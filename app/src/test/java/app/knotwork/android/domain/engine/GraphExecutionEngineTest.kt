package app.knotwork.android.domain.engine

import ai.koog.prompt.Prompt
import ai.koog.prompt.executor.clients.LLMClient
import ai.koog.prompt.executor.clients.anthropic.AnthropicModels
import ai.koog.prompt.llm.LLModel
import ai.koog.prompt.streaming.StreamFrame
import app.knotwork.android.data.engine.KoogClientFactory
import app.knotwork.android.data.engine.KoogCloudLlmModelResolver
import app.knotwork.android.data.repositories.ClarificationRepositoryImpl
import app.knotwork.android.domain.engine.executors.ClarificationNodeExecutor
import app.knotwork.android.domain.engine.executors.CloudLlmNodeExecutor
import app.knotwork.android.domain.engine.executors.IfConditionNodeExecutor
import app.knotwork.android.domain.engine.executors.InputNodeExecutor
import app.knotwork.android.domain.engine.executors.LiteRtNodeExecutor
import app.knotwork.android.domain.engine.executors.NodeExecutorFactory
import app.knotwork.android.domain.engine.executors.OutputNodeExecutor
import app.knotwork.android.domain.engine.executors.QueueProcessorNodeExecutor
import app.knotwork.android.domain.engine.executors.SummaryNodeExecutor
import app.knotwork.android.domain.engine.executors.SystemNodeExecutor
import app.knotwork.android.domain.engine.executors.ToolNodeExecutor
import app.knotwork.android.domain.models.AgentOrchestratorState
import app.knotwork.android.domain.models.AgentTool
import app.knotwork.android.domain.models.ChatMessage
import app.knotwork.android.domain.models.CloudProvider
import app.knotwork.android.domain.models.ConnectionModel
import app.knotwork.android.domain.models.ConsoleEventType
import app.knotwork.android.domain.models.MemoryChunk
import app.knotwork.android.domain.models.NodeContextConfig
import app.knotwork.android.domain.models.NodeModel
import app.knotwork.android.domain.models.NodeType
import app.knotwork.android.domain.models.PipelineGraph
import app.knotwork.android.domain.models.PipelineRunStatus
import app.knotwork.android.domain.models.Result
import app.knotwork.android.domain.models.ResumeContext
import app.knotwork.android.domain.models.Role
import app.knotwork.android.domain.models.RunTraceRecord
import app.knotwork.android.domain.models.ToolApprovalPolicy
import app.knotwork.android.domain.models.ToolRisk
import app.knotwork.android.domain.prompt.PromptTemplateEngine
import app.knotwork.android.domain.prompt.PromptVariableProvider
import app.knotwork.android.domain.repositories.ApiKeyRepository
import app.knotwork.android.domain.repositories.ChatRepository
import app.knotwork.android.domain.repositories.ClarificationRepository
import app.knotwork.android.domain.repositories.CrashReportingRepository
import app.knotwork.android.domain.repositories.LocalModelRepository
import app.knotwork.android.domain.repositories.MemoryRepository
import app.knotwork.android.domain.repositories.MetricsRepository
import app.knotwork.android.domain.repositories.NetworkActivityTracker
import app.knotwork.android.domain.repositories.PipelineRunRepository
import app.knotwork.android.domain.repositories.RunTraceRepository
import app.knotwork.android.domain.repositories.SettingsRepository
import app.knotwork.android.domain.repositories.ToolRepository
import app.knotwork.android.domain.services.ApprovalNotifier
import app.knotwork.android.domain.usecases.EvaluateIfConditionUseCase
import app.knotwork.android.domain.usecases.GetContextWindowUseCase
import app.knotwork.android.domain.usecases.LoadModelUseCase
import app.knotwork.android.domain.usecases.RetrieveRelevantMemoryUseCase
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.coVerifyOrder
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
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
    private lateinit var cloudLlmModelResolver: KoogCloudLlmModelResolver
    private lateinit var networkActivityTracker: NetworkActivityTracker
    private lateinit var evaluateIfConditionUseCase: EvaluateIfConditionUseCase
    private lateinit var loadModelUseCase: LoadModelUseCase
    private lateinit var clarificationRepository: ClarificationRepository
    private lateinit var crashReportingRepository: CrashReportingRepository
    private lateinit var localModelRepository: LocalModelRepository
    private lateinit var memoryRepository: MemoryRepository
    private lateinit var pipelineRunRepository: PipelineRunRepository
    private lateinit var runTraceRepository: RunTraceRepository

    private lateinit var engine: GraphExecutionEngine
    private lateinit var promptTemplateEngine: PromptTemplateEngine
    private var promptVariableProviders: Set<PromptVariableProvider> = emptySet()

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
        cloudLlmModelResolver = mockk()
        networkActivityTracker = mockk(relaxed = true)
        evaluateIfConditionUseCase = mockk()
        loadModelUseCase = mockk()
        crashReportingRepository = mockk(relaxed = true)
        localModelRepository = mockk(relaxed = true)
        memoryRepository = mockk(relaxed = true)
        pipelineRunRepository = mockk(relaxed = true)
        runTraceRepository = mockk(relaxed = true)
        clarificationRepository = mockk()
        // The resolver is exercised whenever a CLOUD node fires; default to a sensible
        // Koog model so each individual test does not have to wire it up.
        coEvery { cloudLlmModelResolver.resolveModel(any()) } returns AnthropicModels.Sonnet_4_5
        // Provider-keyed dispatch — tests that exercise CLOUD configure Anthropic.
        coEvery { koogClientFactory.createClient(any()) } coAnswers {
            when (firstArg<CloudProvider>()) {
                CloudProvider.ANTHROPIC -> koogClientFactory.createAnthropicExecutor()
                CloudProvider.OPENAI -> koogClientFactory.createOpenAIExecutor()
                CloudProvider.GOOGLE -> koogClientFactory.createGoogleExecutor()
                CloudProvider.DEEPSEEK -> koogClientFactory.createDeepSeekExecutor()
                CloudProvider.OLLAMA -> koogClientFactory.createOllamaExecutor()
            }
        }

        val inputNodeExecutor = InputNodeExecutor()
        val outputNodeExecutor = OutputNodeExecutor(llmEngine, loadModelUseCase, chatRepository)
        val ifConditionNodeExecutor = IfConditionNodeExecutor(evaluateIfConditionUseCase)
        val queueProcessorNodeExecutor = QueueProcessorNodeExecutor()

        val toolNodeExecutor = ToolNodeExecutor(
            llmEngine,
            loadModelUseCase,
            toolRepository,
            settingsRepository,
            approvalNotifier,
            chatRepository,
        )

        val liteRtNodeExecutor = LiteRtNodeExecutor(
            llmEngine,
            toolRepository,
            chatRepository,
            settingsRepository,
            metricsRepository,
            loadModelUseCase,
        )

        val cloudLlmNodeExecutor = CloudLlmNodeExecutor(
            toolRepository,
            chatRepository,
            settingsRepository,
            apiKeyRepository,
            metricsRepository,
            koogClientFactory,
            cloudLlmModelResolver,
            networkActivityTracker,
        )

        val systemNodeExecutor = SystemNodeExecutor(
            llmEngine,
            loadModelUseCase,
            chatRepository,
        )

        val summaryNodeExecutor = SummaryNodeExecutor(
            llmEngine,
            loadModelUseCase,
        )

        val clarificationNodeExecutor = ClarificationNodeExecutor(
            llmEngine,
            loadModelUseCase,
            clarificationRepository,
        )

        val nodeExecutorFactory = NodeExecutorFactory(
            inputNodeExecutor, outputNodeExecutor, ifConditionNodeExecutor,
            toolNodeExecutor, liteRtNodeExecutor, cloudLlmNodeExecutor,
            systemNodeExecutor, queueProcessorNodeExecutor, summaryNodeExecutor,
            clarificationNodeExecutor,
        )

        promptTemplateEngine = PromptTemplateEngine()
        promptVariableProviders = emptySet()
        engine = GraphExecutionEngine(
            nodeExecutorFactory,
            toolNodeExecutor,
            chatRepository,
            settingsRepository,
            metricsRepository,
            promptTemplateEngine,
            promptVariableProviders,
            NodeContextBuilder(),
            retrieveRelevantMemoryUseCase,
            crashReportingRepository,
            localModelRepository,
            memoryRepository,
            pipelineRunRepository,
            runTraceRepository,
        )

        coEvery { getContextWindowUseCase(sessionId) } returns ""
        coEvery { retrieveRelevantMemoryUseCase(any()) } returns emptyList()
        coEvery { retrieveRelevantMemoryUseCase.retrieveScored(any()) } returns emptyList()
        every { settingsRepository.verboseMemoryLoggingEnabled } returns flowOf(false)
        every { chatRepository.getMessagesForSession(any()) } returns flowOf(emptyList())
        every { settingsRepository.systemPromptPrefix } returns flowOf("")
        every { settingsRepository.toolUsageInstruction } returns flowOf("")
        every { settingsRepository.toolApprovalPolicy } returns flowOf(ToolApprovalPolicy.SensitiveOrDestructive)
        every { settingsRepository.blockDestructiveTools } returns flowOf(false)
        every { settingsRepository.pipelineMaxSteps } returns flowOf(15)
        coEvery { toolRepository.getAvailableTools() } returns emptyList()

        coEvery { loadModelUseCase(any()) } returns Result.Success(Unit)
    }

    @Test
    fun `sets active_pipeline_id and active_model crash custom keys at start`() = runTest {
        val inputNode = NodeModel("input_1", NodeType.INPUT, 0f, 0f)
        val outputNode = NodeModel("output_1", NodeType.OUTPUT, 0f, 0f, systemPrompt = null)
        val graph = PipelineGraph(
            id = "graph-xyz",
            name = "Simple",
            nodes = listOf(inputNode, outputNode),
            connections = listOf(ConnectionModel("c1", "input_1", "output_1")),
        )
        coEvery { localModelRepository.getActiveModel() } returns null

        engine(sessionId, "Hi", graph).toList()

        coVerify { crashReportingRepository.setCustomKey("active_pipeline_id", "graph-xyz") }
        coVerify { crashReportingRepository.setCustomKey("active_model", "none") }
    }

    @Test
    fun `routes user reply through CLARIFICATION node into OUTPUT`() = runTest {
        // Arrange a pipeline INPUT → CLARIFICATION → OUTPUT.
        // The clarification node generates a JSON question, the repository returns
        // "user reply", and the OUTPUT node (no systemPrompt) echoes its input.
        val inputNode = NodeModel("input_1", NodeType.INPUT, 0f, 0f)
        val clarificationNode = NodeModel(
            id = "clar_1",
            type = NodeType.CLARIFICATION,
            x = 0f,
            y = 0f,
            systemPrompt = "Ask user for clarification.",
            clarificationTimeoutMs = 5_000L,
        )
        val outputNode = NodeModel("output_1", NodeType.OUTPUT, 0f, 0f, systemPrompt = null)

        val graph = PipelineGraph(
            id = "g1",
            name = "Clarification Graph",
            nodes = listOf(inputNode, clarificationNode, outputNode),
            connections = listOf(
                ConnectionModel("c1", "input_1", "clar_1"),
                ConnectionModel("c2", "clar_1", "output_1"),
            ),
        )

        every { llmEngine.generateResponseStream(any()) } returns flowOf(
            "{\"question\":\"Confirm?\",\"options\":[\"yes\",\"no\"]}",
        )
        coEvery { clarificationRepository.requestAnswer(any()) } returns "user reply"

        // Act
        val states = engine(sessionId, "User prompt", graph).toList()

        // Assert: AwaitingClarification was emitted with the parsed question/options.
        val awaiting = states.filterIsInstance<AgentOrchestratorState.AwaitingClarification>().single()
        assertEquals("Confirm?", awaiting.request.question)
        assertEquals(listOf("yes", "no"), awaiting.request.options)
        assertEquals(5_000L, awaiting.request.timeoutMs)

        // The user's reply propagates downstream and ends up in the final Completed state.
        val completed = states.last() as AgentOrchestratorState.Completed
        assertEquals("user reply", completed.finalResponse)
        coVerify { clarificationRepository.requestAnswer(any()) }
    }

    @Test
    fun `successful traversal from INPUT to OUTPUT`() = runTest {
        val inputNode = NodeModel("input_1", NodeType.INPUT, 0f, 0f)
        val llmNode = NodeModel("llm_1", NodeType.LITE_RT, 0f, 0f)
        val outputNode = NodeModel("output_1", NodeType.OUTPUT, 0f, 0f)

        val graph = PipelineGraph(
            id = "g1",
            name = "Test Graph",
            nodes = listOf(inputNode, llmNode, outputNode),
            connections = listOf(
                ConnectionModel("c1", "input_1", "llm_1"),
                ConnectionModel("c2", "llm_1", "output_1"),
            ),
        )

        every { llmEngine.generateResponseStream(any()) } returns flowOf("LLM ", "Response")

        val states = engine(sessionId, "User prompt", graph).toList()

        val completedState = states.last() as AgentOrchestratorState.Completed
        assertEquals("LLM Response", completedState.finalResponse)
    }

    /**
     * Cooperative-cancellation contract of the per-node catch in the engine
     * loop: a [CancellationException] escaping a node executor must propagate
     * out of the engine flow unchanged and must never be collapsed into an
     * [AgentOrchestratorState.Error] emission.
     */
    @Test
    fun `given executor throws CancellationException then engine rethrows without Error emission`() = runTest {
        val inputNode = NodeModel("input_1", NodeType.INPUT, 0f, 0f)
        val llmNode = NodeModel("llm_1", NodeType.LITE_RT, 0f, 0f)
        val outputNode = NodeModel("output_1", NodeType.OUTPUT, 0f, 0f)

        val graph = PipelineGraph(
            id = "g1",
            name = "Test Graph",
            nodes = listOf(inputNode, llmNode, outputNode),
            connections = listOf(
                ConnectionModel("c1", "input_1", "llm_1"),
                ConnectionModel("c2", "llm_1", "output_1"),
            ),
        )

        every { llmEngine.generateResponseStream(any()) } returns flow {
            throw CancellationException("user stop")
        }

        val states = mutableListOf<AgentOrchestratorState>()
        var cancellation: CancellationException? = null
        try {
            engine(sessionId, "User prompt", graph).collect { states.add(it) }
        } catch (e: CancellationException) {
            cancellation = e
        }

        assertNotNull("CancellationException must propagate out of the engine flow", cancellation)
        assertTrue(
            "No Error state may be emitted on cancellation, got $states",
            states.none { it is AgentOrchestratorState.Error },
        )
    }

    @Test
    fun `evaluates IF_CONDITION and branches correctly`() = runTest {
        val inputNode = NodeModel("input_1", NodeType.INPUT, 0f, 0f)
        val ifNode = NodeModel("if_1", NodeType.IF_CONDITION, 0f, 0f)
        val outputTrue = NodeModel("out_true", NodeType.OUTPUT, 0f, 0f)
        val outputFalse = NodeModel("out_false", NodeType.OUTPUT, 0f, 0f)

        val graph = PipelineGraph(
            id = "g1",
            name = "Test Graph",
            nodes = listOf(inputNode, ifNode, outputTrue, outputFalse),
            connections = listOf(
                ConnectionModel("c1", "input_1", "if_1"),
                ConnectionModel("c2", "if_1", "out_true", label = "True"),
                ConnectionModel("c3", "if_1", "out_false", label = "False"),
            ),
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
            id = "g1",
            name = "Output Test",
            nodes = listOf(inputNode, outputNode),
            connections = listOf(
                ConnectionModel("c1", "input_1", "output_1"),
            ),
        )

        every { llmEngine.generateResponseStream(any()) } returns flowOf("Formatted Response")

        val states = engine(sessionId, "Raw User Input", graph).toList()

        val completedState = states.last() as AgentOrchestratorState.Completed
        assertEquals("Formatted Response", completedState.finalResponse)

        io.mockk.verify {
            llmEngine.generateResponseStream(
                match {
                    it.contains("Format this text:") && it.contains("Raw User Input")
                },
            )
        }
    }

    @Test
    fun `prevents infinite cycles via DAG validation`() = runTest {
        val n1 = NodeModel("n1", NodeType.INPUT, 0f, 0f)
        val n2 = NodeModel("n2", NodeType.LITE_RT, 0f, 0f)

        val cyclicGraph = PipelineGraph(
            id = "g1",
            name = "Cyclic",
            nodes = listOf(n1, n2),
            connections = listOf(
                ConnectionModel("c1", "n1", "n2"),
                ConnectionModel("c2", "n2", "n1"), // cycle back
            ),
        )

        val states = engine(sessionId, "Test", cyclicGraph).toList()

        // The terminal orchestrator state must be Error so observers reading
        // the latest value of `globalState` (e.g. `TaskQueueManagerImpl`) see
        // the failure rather than a trailing ConsoleLog snapshot.
        val last = states.last()
        assertTrue(last is AgentOrchestratorState.Error)
        assertTrue((last as AgentOrchestratorState.Error).message.contains("cycles"))
    }

    @Test
    fun `emits Error if pipeline ends without reaching OUTPUT`() = runTest {
        val inputNode = NodeModel("input_1", NodeType.INPUT, 0f, 0f)
        val llmNode = NodeModel("llm_1", NodeType.LITE_RT, 0f, 0f)

        val graph = PipelineGraph(
            id = "g1",
            name = "Incomplete",
            nodes = listOf(inputNode, llmNode),
            connections = listOf(
                ConnectionModel("c1", "input_1", "llm_1"),
                // No connection to an output node
            ),
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
        val mockSettings = mockk<SettingsRepository>(relaxed = true)
        every { mockSettings.pipelineMaxSteps } returns flowOf(15)
        val engineWithMock = GraphExecutionEngine(
            mockFactory,
            mockToolNodeExecutor,
            mockk(relaxed = true),
            mockSettings,
            mockk(relaxed = true),
            PromptTemplateEngine(),
            emptySet(),
            NodeContextBuilder(),
            mockk(relaxed = true),
            mockk(relaxed = true),
            mockk(relaxed = true),
            mockk(relaxed = true),
            mockk(relaxed = true),
            mockk(relaxed = true),
        )

        engineWithMock.resumeWithApproval("session_id_123", true)

        io.mockk.verify { mockToolNodeExecutor.resumeWithApproval("session_id_123", true) }
    }

    @Test
    fun `given maxSteps exceeded when pipeline loops then emits error`() = runTest {
        every { settingsRepository.pipelineMaxSteps } returns flowOf(2)

        val inputNode = NodeModel("input_1", NodeType.INPUT, 0f, 0f)
        val llmNode = NodeModel("llm_1", NodeType.LITE_RT, 0f, 0f)

        val graph = PipelineGraph(
            id = "g1",
            name = "Long Graph",
            nodes = listOf(inputNode, llmNode),
            connections = listOf(ConnectionModel("c1", "input_1", "llm_1")),
        )
        every { llmEngine.generateResponseStream(any()) } returns flowOf("response")

        val states = engine(sessionId, "prompt", graph).toList()

        val last = states.last()
        assertTrue(last is AgentOrchestratorState.Error)
        assertTrue((last as AgentOrchestratorState.Error).message.contains("2"))
    }

    @Test
    fun `given maxSteps from settings when pipeline completes within limit then succeeds`() = runTest {
        every { settingsRepository.pipelineMaxSteps } returns flowOf(5)

        val inputNode = NodeModel("input_1", NodeType.INPUT, 0f, 0f)
        val outputNode = NodeModel("output_1", NodeType.OUTPUT, 0f, 0f)

        val graph = PipelineGraph(
            id = "g1",
            name = "Short Graph",
            nodes = listOf(inputNode, outputNode),
            connections = listOf(ConnectionModel("c1", "input_1", "output_1")),
        )
        every { llmEngine.generateResponseStream(any()) } returns flowOf("done")

        val states = engine(sessionId, "prompt", graph).toList()

        assertTrue(states.last() is AgentOrchestratorState.Completed)
    }

    // ─── Dynamic progress (totalSteps) tests ─────────────────────────────────

    @Test
    fun `given linear graph with no branching then totalSteps is known from first step`() = runTest {
        every { settingsRepository.pipelineMaxSteps } returns flowOf(15)

        val inputNode = NodeModel("input", NodeType.INPUT, 0f, 0f)
        val liteRtNode = NodeModel("lite_rt", NodeType.LITE_RT, 0f, 0f)
        val outputNode = NodeModel("output", NodeType.OUTPUT, 0f, 0f)

        val graph = PipelineGraph(
            id = "g1",
            name = "Linear",
            nodes = listOf(inputNode, liteRtNode, outputNode),
            connections = listOf(
                ConnectionModel("c1", "input", "lite_rt"),
                ConnectionModel("c2", "lite_rt", "output"),
            ),
        )
        every { llmEngine.generateResponseStream(any()) } returns flowOf("answer")

        val stages = engine(sessionId, "query", graph).toList()
            .filterIsInstance<AgentOrchestratorState.PipelineStage>()

        // All stages should have totalSteps = 3 (graph.nodes.size) from the start
        assertTrue("First stage should have known total", stages.first().stepInfo.totalSteps == 3)
        assertTrue("All stages must have same totalSteps = 3", stages.all { it.stepInfo.totalSteps == 3 })
    }

    @Test
    fun `given branching graph then totalSteps is null before routing and concrete after`() = runTest {
        every { settingsRepository.pipelineMaxSteps } returns flowOf(15)

        val inputNode = NodeModel("input", NodeType.INPUT, 0f, 0f)
        val routerNode = NodeModel("router", NodeType.INTENT_ROUTER, 0f, 0f)
        val liteRtNode = NodeModel("lite_rt", NodeType.LITE_RT, 0f, 0f)
        val outputNode = NodeModel("output", NodeType.OUTPUT, 0f, 0f)

        val graph = PipelineGraph(
            id = "g1",
            name = "Branching",
            nodes = listOf(inputNode, routerNode, liteRtNode, outputNode),
            connections = listOf(
                ConnectionModel("c1", "input", "router"),
                ConnectionModel("c2", "router", "lite_rt", label = "Data"),
                ConnectionModel("c3", "lite_rt", "output"),
            ),
        )
        every { llmEngine.generateResponseStream(any()) } returnsMany listOf(
            flowOf("Data"),
            flowOf("answer"),
            flowOf("final"),
        )

        val stages = engine(sessionId, "query", graph).toList()
            .filterIsInstance<AgentOrchestratorState.PipelineStage>()

        // INPUT and INTENT_ROUTER stages must show unknown total
        val unknownStages = stages.take(2)
        assertTrue(
            "INPUT and INTENT_ROUTER should have null totalSteps",
            unknownStages.all { it.stepInfo.totalSteps == null },
        )

        // After routing resolves, LITE_RT and OUTPUT stages must have a concrete total
        val knownStages = stages.drop(2)
        assertTrue(
            "Post-routing stages should have concrete totalSteps",
            knownStages.all { it.stepInfo.totalSteps != null },
        )
        // stepIndex must never exceed totalSteps
        knownStages.forEach { stage ->
            assertTrue(
                "stepIndex ${stage.stepInfo.stepIndex} must not exceed totalSteps ${stage.stepInfo.totalSteps}",
                stage.stepInfo.stepIndex <= stage.stepInfo.totalSteps!!,
            )
        }
    }

    // ─── INTENT_ROUTER tests ─────────────────────────────────────────────────

    @Test
    fun `given INTENT_ROUTER when routing key emitted then downstream node receives original query`() = runTest {
        every { settingsRepository.pipelineMaxSteps } returns flowOf(15)

        val inputNode = NodeModel("input", NodeType.INPUT, 0f, 0f)
        val routerNode = NodeModel("router", NodeType.INTENT_ROUTER, 0f, 0f)
        val liteRtNode = NodeModel("lite_rt", NodeType.LITE_RT, 0f, 0f)
        val outputNode = NodeModel("output", NodeType.OUTPUT, 0f, 0f)

        val graph = PipelineGraph(
            id = "g1",
            name = "Router Fix Test",
            nodes = listOf(inputNode, routerNode, liteRtNode, outputNode),
            connections = listOf(
                ConnectionModel("c1", "input", "router"),
                ConnectionModel("c2", "router", "lite_rt", label = "Data"),
                ConnectionModel("c3", "lite_rt", "output"),
            ),
        )

        every { llmEngine.generateResponseStream(any()) } returnsMany listOf(
            flowOf("Data"), // INTENT_ROUTER routing decision
            flowOf("Correct answer"), // LITE_RT processes original prompt
            flowOf("Final"), // OUTPUT formats the response
        )

        val states = engine(sessionId, "fuel consumption query", graph).toList()

        assertTrue(
            "Expected Completed but got: ${states.last()}",
            states.last() is AgentOrchestratorState.Completed,
        )

        // LITE_RT must receive the original user query (now wrapped by NodeContextBuilder),
        // not the routing key. The cleanup removed the legacy "USER/INPUT:" prefix
        // because the assembled context already carries `--- Previous Node Output ---`,
        // so we assert via the context-builder header instead.
        io.mockk.verify {
            llmEngine.generateResponseStream(
                match {
                    it.contains("--- Previous Node Output ---") && it.contains("fuel consumption query")
                },
            )
        }
        // No downstream node must receive the routing key as its previous-node-output payload.
        io.mockk.verify(exactly = 0) {
            llmEngine.generateResponseStream(match { it.contains("Previous Node Output ---\nData") })
        }
    }

    // ─── EVALUATION tests ─────────────────────────────────────────────────────

    /**
     * Builds an INPUT → EVALUATION → {Pass, Fail} → OUTPUT graph where each
     * branch carries a distinct LITE_RT marker prompt, so a routing assertion
     * can tell which output port the verdict selected.
     */
    private fun evaluationBranchGraph(): PipelineGraph = PipelineGraph(
        id = "ge",
        name = "Evaluation routing",
        nodes = listOf(
            NodeModel("input", NodeType.INPUT, 0f, 0f),
            NodeModel("eval", NodeType.EVALUATION, 0f, 0f),
            NodeModel("pass", NodeType.LITE_RT, 0f, 0f, systemPrompt = "PASS_BRANCH_MARKER"),
            NodeModel("fail", NodeType.LITE_RT, 0f, 0f, systemPrompt = "FAIL_BRANCH_MARKER"),
            NodeModel("output", NodeType.OUTPUT, 0f, 0f),
        ),
        connections = listOf(
            ConnectionModel("c1", "input", "eval"),
            ConnectionModel("c2", "eval", "pass", label = "Pass"),
            ConnectionModel("c3", "eval", "fail", label = "Fail"),
            ConnectionModel("c4", "pass", "output"),
            ConnectionModel("c5", "fail", "output"),
        ),
    )

    @Test
    fun `given EVALUATION verdict PASS then routes through the Pass output port`() = runTest {
        every { settingsRepository.pipelineMaxSteps } returns flowOf(15)
        every { llmEngine.generateResponseStream(any()) } returnsMany listOf(
            flowOf("PASS — the subtask result satisfies the goal."), // EVALUATION verdict
            flowOf("pass-branch answer"), // LITE_RT on the Pass branch
            flowOf("Final"), // OUTPUT
        )

        val states = engine(sessionId, "evaluate this", evaluationBranchGraph()).toList()

        assertTrue("Expected Completed but got: ${states.last()}", states.last() is AgentOrchestratorState.Completed)
        io.mockk.verify { llmEngine.generateResponseStream(match { it.contains("PASS_BRANCH_MARKER") }) }
        io.mockk.verify(exactly = 0) {
            llmEngine.generateResponseStream(match { it.contains("FAIL_BRANCH_MARKER") })
        }
    }

    @Test
    fun `given EVALUATION verdict FAIL then routes through the Fail output port`() = runTest {
        every { settingsRepository.pipelineMaxSteps } returns flowOf(15)
        every { llmEngine.generateResponseStream(any()) } returnsMany listOf(
            flowOf("FAIL: the result is incorrect and cannot be repaired."), // EVALUATION verdict
            flowOf("fail-branch answer"), // LITE_RT on the Fail branch
            flowOf("Final"), // OUTPUT
        )

        val states = engine(sessionId, "evaluate this", evaluationBranchGraph()).toList()

        assertTrue("Expected Completed but got: ${states.last()}", states.last() is AgentOrchestratorState.Completed)
        io.mockk.verify { llmEngine.generateResponseStream(match { it.contains("FAIL_BRANCH_MARKER") }) }
        io.mockk.verify(exactly = 0) {
            llmEngine.generateResponseStream(match { it.contains("PASS_BRANCH_MARKER") })
        }
    }

    // ─── QUEUE_PROCESSOR tests ────────────────────────────────────────────────

    @Test
    fun `given QUEUE_PROCESSOR when LLM returns JSON list then each item is processed and pipeline completes`() =
        runTest {
            every { settingsRepository.pipelineMaxSteps } returns flowOf(20)

            val inputNode = NodeModel("input", NodeType.INPUT, 0f, 0f)
            val listGenNode = NodeModel("list_gen", NodeType.LITE_RT, 0f, 0f)
            val queueNode = NodeModel("queue", NodeType.QUEUE_PROCESSOR, 0f, 0f)
            val itemProcNode = NodeModel("item_proc", NodeType.LITE_RT, 0f, 0f)
            val outputNode = NodeModel("output", NodeType.OUTPUT, 0f, 0f)

            // item_proc has no outgoing connection — engine treats it as end-of-subtask
            val graph = PipelineGraph(
                id = "g1",
                name = "Queue Test",
                nodes = listOf(inputNode, listGenNode, queueNode, itemProcNode, outputNode),
                connections = listOf(
                    ConnectionModel("c1", "input", "list_gen"),
                    ConnectionModel("c2", "list_gen", "queue"),
                    ConnectionModel("c3", "queue", "item_proc", label = "Item"),
                    ConnectionModel("c4", "queue", "output", label = "Done"),
                ),
            )

            every { llmEngine.generateResponseStream(any()) } returnsMany listOf(
                flowOf("""["subtask_one", "subtask_two"]"""),
                flowOf("result_one"),
                flowOf("result_two"),
            )

            val states = engine(sessionId, "Process the list", graph).toList()

            assertTrue(
                "Expected Completed but got: ${states.last()}",
                states.last() is AgentOrchestratorState.Completed,
            )
        }

    @Test
    fun `given QUEUE_PROCESSOR when maxSteps exceeded during queue iteration then emits error`() = runTest {
        // maxSteps=3 is not enough to process INPUT + list_gen + queue + item_proc × 2 + output
        every { settingsRepository.pipelineMaxSteps } returns flowOf(3)

        val inputNode = NodeModel("input", NodeType.INPUT, 0f, 0f)
        val listGenNode = NodeModel("list_gen", NodeType.LITE_RT, 0f, 0f)
        val queueNode = NodeModel("queue", NodeType.QUEUE_PROCESSOR, 0f, 0f)
        val itemProcNode = NodeModel("item_proc", NodeType.LITE_RT, 0f, 0f)
        val outputNode = NodeModel("output", NodeType.OUTPUT, 0f, 0f)

        val graph = PipelineGraph(
            id = "g2",
            name = "Queue MaxSteps",
            nodes = listOf(inputNode, listGenNode, queueNode, itemProcNode, outputNode),
            connections = listOf(
                ConnectionModel("c1", "input", "list_gen"),
                ConnectionModel("c2", "list_gen", "queue"),
                ConnectionModel("c3", "queue", "item_proc", label = "Item"),
                ConnectionModel("c4", "queue", "output", label = "Done"),
            ),
        )

        every { llmEngine.generateResponseStream(any()) } returnsMany listOf(
            flowOf("""["item_a", "item_b"]"""),
            flowOf("result_a"),
            flowOf("result_b"),
        )

        val states = engine(sessionId, "Too many steps", graph).toList()

        val last = states.last()
        assertTrue("Expected Error but got: $last", last is AgentOrchestratorState.Error)
        assertTrue((last as AgentOrchestratorState.Error).message.contains("3"))
    }

    // ─── Per-node metrics tests ───────────────────────────────────────────────

    @Test
    fun `given pipeline executes LITE_RT node then metricsRepository records node execution with tokenCount`() =
        runTest {
            every { settingsRepository.pipelineMaxSteps } returns flowOf(15)

            val inputNode = NodeModel("input", NodeType.INPUT, 0f, 0f)
            val llmNode = NodeModel("llm", NodeType.LITE_RT, 0f, 0f)
            val outputNode = NodeModel("output", NodeType.OUTPUT, 0f, 0f)

            val graph = PipelineGraph(
                id = "g1",
                name = "Metrics Test",
                nodes = listOf(inputNode, llmNode, outputNode),
                connections = listOf(
                    ConnectionModel("c1", "input", "llm"),
                    ConnectionModel("c2", "llm", "output"),
                ),
            )

            // LITE_RT should emit 3 tokens, OUTPUT executor also calls generateResponseStream.
            every { llmEngine.generateResponseStream(any()) } returnsMany listOf(
                flowOf("one ", "two ", "three"),
                flowOf("final"),
            )

            engine(sessionId, "prompt", graph).toList()

            // INPUT, LITE_RT, OUTPUT — three nodes, three recordings
            verify(exactly = 1) { metricsRepository.recordNodeExecution(NodeType.INPUT, any(), any()) }
            verify(exactly = 1) {
                metricsRepository.recordNodeExecution(NodeType.LITE_RT, any(), match { it != null && it > 0 })
            }
            verify(exactly = 1) { metricsRepository.recordNodeExecution(NodeType.OUTPUT, any(), any()) }
        }

    @Test
    fun `given pipeline executes node then trace step includes durationMs and tokenCount`() = runTest {
        every { settingsRepository.pipelineMaxSteps } returns flowOf(15)

        val inputNode = NodeModel("input", NodeType.INPUT, 0f, 0f)
        val llmNode = NodeModel("llm", NodeType.LITE_RT, 0f, 0f)
        val outputNode = NodeModel("output", NodeType.OUTPUT, 0f, 0f)

        val graph = PipelineGraph(
            id = "g1",
            name = "Trace Timing",
            nodes = listOf(inputNode, llmNode, outputNode),
            connections = listOf(
                ConnectionModel("c1", "input", "llm"),
                ConnectionModel("c2", "llm", "output"),
            ),
        )
        every { llmEngine.generateResponseStream(any()) } returnsMany listOf(
            flowOf("tok1 tok2"),
            flowOf("final"),
        )

        val states = engine(sessionId, "prompt", graph, runId = "run-trace-1").toList()

        val trace = states.filterIsInstance<AgentOrchestratorState.PipelineTrace>().last()
        assertTrue(trace.steps.any { it.nodeName == "LITE_RT" && (it.tokenCount ?: 0) > 0 })
        assertTrue(trace.steps.all { it.durationMs >= 0 })
        coVerify(atLeast = 1) {
            runTraceRepository.append(
                match { record ->
                    record is RunTraceRecord.NodeIo &&
                        record.runId == "run-trace-1" &&
                        record.nodeType == "LITE_RT" &&
                        (record.tokenCount ?: 0) > 0 &&
                        record.durationMs >= 0
                },
            )
        }
    }

    @Test
    fun `given QUEUE_PROCESSOR when item processor throws exception then emits error`() = runTest {
        every { settingsRepository.pipelineMaxSteps } returns flowOf(20)

        val inputNode = NodeModel("input", NodeType.INPUT, 0f, 0f)
        val listGenNode = NodeModel("list_gen", NodeType.LITE_RT, 0f, 0f)
        val queueNode = NodeModel("queue", NodeType.QUEUE_PROCESSOR, 0f, 0f)
        val itemProcNode = NodeModel("item_proc", NodeType.LITE_RT, 0f, 0f)
        val outputNode = NodeModel("output", NodeType.OUTPUT, 0f, 0f)

        val graph = PipelineGraph(
            id = "g3",
            name = "Queue Error",
            nodes = listOf(inputNode, listGenNode, queueNode, itemProcNode, outputNode),
            connections = listOf(
                ConnectionModel("c1", "input", "list_gen"),
                ConnectionModel("c2", "list_gen", "queue"),
                ConnectionModel("c3", "queue", "item_proc", label = "Item"),
                ConnectionModel("c4", "queue", "output", label = "Done"),
            ),
        )

        every { llmEngine.generateResponseStream(any()) } returnsMany listOf(
            flowOf("""["item_x"]"""),
            kotlinx.coroutines.flow.flow { throw RuntimeException("item processor crashed") },
        )

        val states = engine(sessionId, "Will crash", graph).toList()

        val last = states.last()
        assertTrue("Expected Error but got: $last", last is AgentOrchestratorState.Error)
        assertTrue((last as AgentOrchestratorState.Error).message.contains("item processor crashed"))
    }

    // ─── PromptTemplateEngine integration ────────────────────────────────────

    @Test
    fun `given LITE_RT node with DATE placeholder when execute then substitutes value before LLM call`() = runTest {
        every { settingsRepository.pipelineMaxSteps } returns flowOf(15)

        val dateProvider = mockk<PromptVariableProvider>()
        every { dateProvider.key() } returns "DATE"
        coEvery { dateProvider.resolve() } returns "01 May 2026"

        // Rebuild the engine with the provider set wired in. We construct a fresh
        // executor factory so the engine instance is completely owned by this test
        // and there is no risk of state from setUp() leaking in.
        val realFactory = NodeExecutorFactory(
            InputNodeExecutor(),
            OutputNodeExecutor(llmEngine, loadModelUseCase, chatRepository),
            IfConditionNodeExecutor(evaluateIfConditionUseCase),
            ToolNodeExecutor(
                llmEngine,
                loadModelUseCase,
                toolRepository,
                settingsRepository,
                approvalNotifier,
                chatRepository,
            ),
            LiteRtNodeExecutor(
                llmEngine,
                toolRepository,
                chatRepository,
                settingsRepository,
                metricsRepository,
                loadModelUseCase,
            ),
            CloudLlmNodeExecutor(
                toolRepository,
                chatRepository,
                settingsRepository,
                apiKeyRepository,
                metricsRepository,
                koogClientFactory,
                cloudLlmModelResolver,
                networkActivityTracker,
            ),
            SystemNodeExecutor(llmEngine, loadModelUseCase, chatRepository),
            QueueProcessorNodeExecutor(),
            SummaryNodeExecutor(llmEngine, loadModelUseCase),
            ClarificationNodeExecutor(llmEngine, loadModelUseCase, clarificationRepository),
        )
        val engineWithProvider = GraphExecutionEngine(
            realFactory,
            ToolNodeExecutor(
                llmEngine,
                loadModelUseCase,
                toolRepository,
                settingsRepository,
                approvalNotifier,
                chatRepository,
            ),
            chatRepository,
            settingsRepository,
            metricsRepository,
            PromptTemplateEngine(),
            setOf(dateProvider),
            NodeContextBuilder(),
            retrieveRelevantMemoryUseCase,
            crashReportingRepository,
            localModelRepository,
            memoryRepository,
            pipelineRunRepository,
            runTraceRepository,
        )

        val inputNode = NodeModel("input", NodeType.INPUT, 0f, 0f)
        val llmNode = NodeModel("llm", NodeType.LITE_RT, 0f, 0f, systemPrompt = "Today is \$DATE.")
        val outputNode = NodeModel("output", NodeType.OUTPUT, 0f, 0f)

        val graph = PipelineGraph(
            id = "g1",
            name = "Render Test",
            nodes = listOf(inputNode, llmNode, outputNode),
            connections = listOf(
                ConnectionModel("c1", "input", "llm"),
                ConnectionModel("c2", "llm", "output"),
            ),
        )

        every { llmEngine.generateResponseStream(any()) } returns flowOf("ok")

        engineWithProvider(sessionId, "user query", graph).toList()

        // The rendered prompt — and ONLY the rendered prompt — must reach the LLM.
        verify { llmEngine.generateResponseStream(match { it.contains("Today is 01 May 2026.") }) }
        verify(exactly = 0) { llmEngine.generateResponseStream(match { it.contains("Today is \$DATE") }) }
    }

    // ─── End-to-end clarification scenarios ──────────────────────────────────

    @Test
    fun `given INPUT-CLARIFICATION-CLOUD-OUTPUT when user replies then answer flows into response`() = runTest {
        every { settingsRepository.pipelineMaxSteps } returns flowOf(15)

        // Wire CLOUD to a mocked Anthropic client so we can capture the prompt it
        // receives and verify that the user's clarification reply flows downstream.
        val mockAnthropicClient: LLMClient = mockk(relaxed = true)
        val capturedPrompt = slot<Prompt>()
        coEvery {
            mockAnthropicClient.executeStreaming(capture(capturedPrompt), any<LLModel>())
        } returns flowOf(StreamFrame.TextDelta("cloud_response_for_user_reply"))
        coEvery { koogClientFactory.createAnthropicExecutor() } returns mockAnthropicClient

        every { apiKeyRepository.getAnthropicKey() } returns flowOf("anthropic-test-key")
        every { apiKeyRepository.getAnthropicModel() } returns flowOf("claude-sonnet-4-5")
        // Other providers are unconfigured so the auto-selection path also lands on Anthropic.
        every { apiKeyRepository.getOpenAIKey() } returns flowOf(null)
        every { apiKeyRepository.getGoogleKey() } returns flowOf(null)
        every { apiKeyRepository.getDeepSeekKey() } returns flowOf(null)

        // CLARIFICATION uses llmEngine to generate the JSON question; OUTPUT has no
        // systemPrompt so it just echoes its input — that lets us assert that the
        // CLOUD response is what the user finally sees.
        every { llmEngine.generateResponseStream(any()) } returns flowOf(
            "{\"question\":\"Confirm?\",\"options\":[\"yes\",\"no\"]}",
        )
        coEvery { clarificationRepository.requestAnswer(any()) } returns "user reply"

        val inputNode = NodeModel("input_1", NodeType.INPUT, 0f, 0f)
        val clarificationNode = NodeModel(
            id = "clar_1",
            type = NodeType.CLARIFICATION,
            x = 0f,
            y = 0f,
            systemPrompt = "Ask user for clarification.",
            clarificationTimeoutMs = 5_000L,
        )
        val cloudNode = NodeModel(
            id = "cloud_1",
            type = NodeType.CLOUD,
            x = 0f,
            y = 0f,
            cloudProvider = "anthropic",
            systemPrompt = "Answer the user.",
        )
        val outputNode = NodeModel("output_1", NodeType.OUTPUT, 0f, 0f, systemPrompt = null)

        val graph = PipelineGraph(
            id = "g1",
            name = "Clarification → Cloud Graph",
            nodes = listOf(inputNode, clarificationNode, cloudNode, outputNode),
            connections = listOf(
                ConnectionModel("c1", "input_1", "clar_1"),
                ConnectionModel("c2", "clar_1", "cloud_1"),
                ConnectionModel("c3", "cloud_1", "output_1"),
            ),
        )

        val states = engine(sessionId, "User prompt", graph).toList()

        // The clarification request reached the user.
        val awaiting = states.filterIsInstance<AgentOrchestratorState.AwaitingClarification>().single()
        assertEquals("Confirm?", awaiting.request.question)
        assertEquals(listOf("yes", "no"), awaiting.request.options)
        coVerify { clarificationRepository.requestAnswer(any()) }

        // The CLOUD node was invoked with the user's reply embedded in its prompt.
        coVerify { mockAnthropicClient.executeStreaming(any<Prompt>(), any<LLModel>()) }
        val cloudPromptText = capturedPrompt.captured.messages.joinToString("\n") { it.textContent() }
        assertTrue(
            "Cloud prompt must contain the clarification reply, was: $cloudPromptText",
            cloudPromptText.contains("user reply"),
        )

        // OUTPUT (no systemPrompt) echoes its input verbatim, so the cloud's response
        // is what propagates to the final Completed state.
        val completed = states.last() as AgentOrchestratorState.Completed
        assertEquals("cloud_response_for_user_reply", completed.finalResponse)
    }

    @Test
    fun `given CLARIFICATION node when no answer arrives before timeout then pipeline continues with default option`() =
        runTest {
            every { settingsRepository.pipelineMaxSteps } returns flowOf(15)

            // Use a real ClarificationRepositoryImpl to exercise the actual withTimeout
            // behaviour: when no reply arrives, the suspended request resolves with the
            // first option and the pipeline keeps going. A mock would only prove that
            // the executor consumes whatever the repository returns — not that the
            // timeout machinery itself works under the engine.
            val realClarificationRepository = ClarificationRepositoryImpl()

            val realFactory = NodeExecutorFactory(
                InputNodeExecutor(),
                OutputNodeExecutor(llmEngine, loadModelUseCase, chatRepository),
                IfConditionNodeExecutor(evaluateIfConditionUseCase),
                ToolNodeExecutor(
                    llmEngine,
                    loadModelUseCase,
                    toolRepository,
                    settingsRepository,
                    approvalNotifier,
                    chatRepository,
                ),
                LiteRtNodeExecutor(
                    llmEngine,
                    toolRepository,
                    chatRepository,
                    settingsRepository,
                    metricsRepository,
                    loadModelUseCase,
                ),
                CloudLlmNodeExecutor(
                    toolRepository,
                    chatRepository,
                    settingsRepository,
                    apiKeyRepository,
                    metricsRepository,
                    koogClientFactory,
                    cloudLlmModelResolver,
                    networkActivityTracker,
                ),
                SystemNodeExecutor(llmEngine, loadModelUseCase, chatRepository),
                QueueProcessorNodeExecutor(),
                SummaryNodeExecutor(llmEngine, loadModelUseCase),
                ClarificationNodeExecutor(llmEngine, loadModelUseCase, realClarificationRepository),
            )
            val engineWithRealRepo = GraphExecutionEngine(
                realFactory,
                ToolNodeExecutor(
                    llmEngine,
                    loadModelUseCase,
                    toolRepository,
                    settingsRepository,
                    approvalNotifier,
                    chatRepository,
                ),
                chatRepository,
                settingsRepository,
                metricsRepository,
                PromptTemplateEngine(),
                emptySet(),
                NodeContextBuilder(),
                retrieveRelevantMemoryUseCase,
                crashReportingRepository,
                localModelRepository,
                memoryRepository,
                pipelineRunRepository,
                runTraceRepository,
            )

            // Generate a question with two options; the first one is the default the
            // repository falls back to on timeout.
            every { llmEngine.generateResponseStream(any()) } returns flowOf(
                "{\"question\":\"Pick one\",\"options\":[\"default-option\",\"other\"]}",
            )

            val inputNode = NodeModel("input_1", NodeType.INPUT, 0f, 0f)
            val clarificationNode = NodeModel(
                id = "clar_1",
                type = NodeType.CLARIFICATION,
                x = 0f,
                y = 0f,
                systemPrompt = "Ask user for clarification.",
                // Short timeout — runTest virtual time advances past it without any
                // submitClarification call, so the repository returns the default.
                clarificationTimeoutMs = 50L,
            )
            val outputNode = NodeModel("output_1", NodeType.OUTPUT, 0f, 0f, systemPrompt = null)

            val graph = PipelineGraph(
                id = "g1",
                name = "Clarification Timeout Graph",
                nodes = listOf(inputNode, clarificationNode, outputNode),
                connections = listOf(
                    ConnectionModel("c1", "input_1", "clar_1"),
                    ConnectionModel("c2", "clar_1", "output_1"),
                ),
            )

            val states = engineWithRealRepo(sessionId, "User prompt", graph).toList()

            // The clarification was published before timing out.
            val awaiting = states.filterIsInstance<AgentOrchestratorState.AwaitingClarification>().single()
            assertEquals("Pick one", awaiting.request.question)
            assertEquals(50L, awaiting.request.timeoutMs)

            // The default option (first in the list) reached OUTPUT and surfaced as the
            // final completed response — proving the pipeline did NOT stall on the
            // unanswered request.
            val completed = states.last() as AgentOrchestratorState.Completed
            assertEquals("default-option", completed.finalResponse)
        }

    // ─── NodeContextBuilder integration ──────────────────────────────────────

    @Test
    fun `given LITE_RT with default ALL_ENABLED config when executed then assembled context reaches LLM`() = runTest {
        every { settingsRepository.pipelineMaxSteps } returns flowOf(15)

        val inputNode = NodeModel("input", NodeType.INPUT, 0f, 0f)
        val llmNode = NodeModel("llm", NodeType.LITE_RT, 0f, 0f)
        val outputNode = NodeModel("output", NodeType.OUTPUT, 0f, 0f, systemPrompt = null)

        val graph = PipelineGraph(
            id = "g1",
            name = "Context Wrap Test",
            nodes = listOf(inputNode, llmNode, outputNode),
            connections = listOf(
                ConnectionModel("c1", "input", "llm"),
                ConnectionModel("c2", "llm", "output"),
            ),
        )

        every { llmEngine.generateResponseStream(any()) } returns flowOf("ok")

        engine(sessionId, "what's the weather?", graph).toList()

        // The Original Task header must wrap the user message; Previous Node Output
        // header must carry INPUT's echoed payload (also "what's the weather?").
        io.mockk.verify {
            llmEngine.generateResponseStream(
                match {
                    it.contains("--- Original Task ---") &&
                        it.contains("what's the weather?") &&
                        it.contains("--- Previous Node Output ---")
                },
            )
        }
    }

    @Test
    fun `given LITE_RT with only originalTask flag when executed then disabled blocks are absent from prompt`() =
        runTest {
            every { settingsRepository.pipelineMaxSteps } returns flowOf(15)

            val inputNode = NodeModel("input", NodeType.INPUT, 0f, 0f)
            val llmNode = NodeModel(
                id = "llm",
                type = NodeType.LITE_RT,
                x = 0f,
                y = 0f,
                contextConfig = NodeContextConfig(
                    chatHistory = false,
                    originalTask = true,
                    nodeInput = false,
                    longTermMemory = false,
                    toolResults = false,
                ),
            )
            val outputNode = NodeModel("output", NodeType.OUTPUT, 0f, 0f, systemPrompt = null)

            val graph = PipelineGraph(
                id = "g1",
                name = "Single-Flag Config Test",
                nodes = listOf(inputNode, llmNode, outputNode),
                connections = listOf(
                    ConnectionModel("c1", "input", "llm"),
                    ConnectionModel("c2", "llm", "output"),
                ),
            )

            every { llmEngine.generateResponseStream(any()) } returns flowOf("ok")

            engine(sessionId, "the question", graph).toList()

            // Only the Original Task block (with the user prompt) is allowed in the prompt;
            // the headers for disabled blocks must not appear.
            io.mockk.verify {
                llmEngine.generateResponseStream(
                    match {
                        it.contains("--- Original Task ---") &&
                            it.contains("the question") &&
                            !it.contains("--- Chat History ---") &&
                            !it.contains("--- Long-Term Memory ---") &&
                            !it.contains("--- Tool Results ---") &&
                            !it.contains("--- Previous Node Output ---")
                    },
                )
            }
        }

    @Test
    fun `given LITE_RT with longTermMemory flag when executed then retrieved chunk reaches the prompt`() = runTest {
        every { settingsRepository.pipelineMaxSteps } returns flowOf(15)

        // The retrieval use case is resolved once per run, keyed off the user
        // prompt; stub it to surface exactly one relevant chunk (with a score —
        // the engine consumes the score-preserving variant for the console).
        coEvery { retrieveRelevantMemoryUseCase.retrieveScored(any()) } returns listOf(
            MemoryChunk(
                id = 7L,
                text = "user prefers dark mode",
                embedding = FloatArray(0),
                timestamp = 0L,
            ) to 0.83f,
        )

        val inputNode = NodeModel("input", NodeType.INPUT, 0f, 0f)
        val llmNode = NodeModel(
            id = "llm",
            type = NodeType.LITE_RT,
            x = 0f,
            y = 0f,
            // Only Long-Term Memory is enabled, so the assembled prompt must
            // carry that block and nothing else.
            contextConfig = NodeContextConfig(
                chatHistory = false,
                originalTask = false,
                nodeInput = false,
                longTermMemory = true,
                toolResults = false,
            ),
        )
        val outputNode = NodeModel("output", NodeType.OUTPUT, 0f, 0f, systemPrompt = null)

        val graph = PipelineGraph(
            id = "g1",
            name = "Long-Term Memory Flag Test",
            nodes = listOf(inputNode, llmNode, outputNode),
            connections = listOf(
                ConnectionModel("c1", "input", "llm"),
                ConnectionModel("c2", "llm", "output"),
            ),
        )

        every { llmEngine.generateResponseStream(any()) } returns flowOf("ok")

        engine(sessionId, "what's my UI preference?", graph).toList()

        // The LITE_RT prompt must carry the Long-Term Memory block with the
        // retrieved chunk text, and no other context block headers.
        io.mockk.verify {
            llmEngine.generateResponseStream(
                match {
                    it.contains("--- Long-Term Memory ---") &&
                        it.contains("user prefers dark mode") &&
                        !it.contains("--- Original Task ---") &&
                        !it.contains("--- Chat History ---") &&
                        !it.contains("--- Tool Results ---") &&
                        !it.contains("--- Previous Node Output ---")
                },
            )
        }
    }

    @Test
    fun `given TOOL node configured as auto when executed then resolved tool name reaches downstream`() = runTest {
        every { settingsRepository.pipelineMaxSteps } returns flowOf(15)
        every { settingsRepository.toolApprovalPolicy } returns flowOf(ToolApprovalPolicy.SensitiveOrDestructive)
        every { settingsRepository.blockDestructiveTools } returns flowOf(false)

        // Two tools registered; the LITE_RT used by ToolNodeExecutor for auto-selection
        // returns a JSON object naming "web.search" — so the observation must be
        // attributed to "web.search", not to the configured placeholder "auto".
        coEvery { toolRepository.getAvailableTools() } returns listOf(
            AgentTool("web.search", "Search the web", "{}"),
            AgentTool("calendar.read", "Read the calendar", "{}"),
        )
        coEvery { toolRepository.executeTool("web.search", any(), any()) } returns "search-result"

        val inputNode = NodeModel("input", NodeType.INPUT, 0f, 0f)
        val toolNode = NodeModel(
            id = "tool",
            type = NodeType.TOOL,
            x = 0f,
            y = 0f,
            toolName = "auto",
            // Sparse config: only Tool Results — proves that downstream nodes see
            // the resolved tool, not the literal "auto" placeholder.
            contextConfig = NodeContextConfig(
                chatHistory = false,
                originalTask = false,
                nodeInput = false,
                longTermMemory = false,
                toolResults = true,
            ),
        )
        val downstreamNode = NodeModel(
            id = "llm",
            type = NodeType.LITE_RT,
            x = 0f,
            y = 0f,
            contextConfig = NodeContextConfig(
                chatHistory = false,
                originalTask = false,
                nodeInput = false,
                longTermMemory = false,
                toolResults = true,
            ),
        )
        val outputNode = NodeModel("output", NodeType.OUTPUT, 0f, 0f, systemPrompt = null)

        val graph = PipelineGraph(
            id = "g1",
            name = "Auto Tool Attribution",
            nodes = listOf(inputNode, toolNode, downstreamNode, outputNode),
            connections = listOf(
                ConnectionModel("c1", "input", "tool"),
                ConnectionModel("c2", "tool", "llm"),
                ConnectionModel("c3", "llm", "output"),
            ),
        )

        // Two LLM consumers fire in order:
        //   1. ToolNodeExecutor's auto-selection LLM → returns the JSON tool choice
        //   2. The downstream LITE_RT node → return value is irrelevant for this test
        every { llmEngine.generateResponseStream(any()) } returnsMany listOf(
            flowOf("{\"tool\":\"web.search\",\"arguments\":{\"q\":\"weather\"}}"),
            flowOf("downstream answer"),
        )

        engine(sessionId, "find weather", graph).toList()

        // The downstream LITE_RT prompt must carry the Tool Results block attributed
        // to the resolved tool name, NOT the literal "auto" or the node label.
        io.mockk.verify {
            llmEngine.generateResponseStream(
                match {
                    it.contains("--- Tool Results ---") &&
                        it.contains("web.search: search-result") &&
                        !it.contains("auto: search-result") &&
                        !it.contains("TOOL: search-result")
                },
            )
        }
    }

    @Test
    fun `given control-flow nodes when executed then their input is not wrapped with context headers`() = runTest {
        every { settingsRepository.pipelineMaxSteps } returns flowOf(15)

        // INPUT echoes its raw input; if the engine wrapped it, the OUTPUT (echo mode)
        // would surface the wrapped string as the final response. The fact that the
        // pipeline below produces "raw passthrough" verifies INPUT is left untouched.
        val inputNode = NodeModel("input", NodeType.INPUT, 0f, 0f)
        val outputNode = NodeModel("output", NodeType.OUTPUT, 0f, 0f, systemPrompt = null)

        val graph = PipelineGraph(
            id = "g1",
            name = "Passthrough Test",
            nodes = listOf(inputNode, outputNode),
            connections = listOf(
                ConnectionModel("c1", "input", "output"),
            ),
        )

        val states = engine(sessionId, "raw passthrough", graph).toList()

        val completed = states.last() as AgentOrchestratorState.Completed
        assertEquals("raw passthrough", completed.finalResponse)
    }

    @Test
    fun `given INPUT-TOOL-CLOUD-OUTPUT with distinct contexts when run then TOOL is nodeInput-only`() = runTest {
        every { settingsRepository.pipelineMaxSteps } returns flowOf(15)
        every { settingsRepository.toolApprovalPolicy } returns flowOf(ToolApprovalPolicy.SensitiveOrDestructive)
        every { settingsRepository.blockDestructiveTools } returns flowOf(false)

        // Pre-seed the pipeline-scoped data sources so every block in
        // ALL_ENABLED has something visible to render. Without these stubs the
        // builder would correctly drop empty blocks and we could not
        // distinguish "block omitted because flag is false" from "block
        // omitted because data is empty".
        every { chatRepository.getMessagesForSession(sessionId) } returns flowOf(
            listOf(
                ChatMessage(
                    id = 1L,
                    sessionId = sessionId,
                    role = Role.USER,
                    content = "earlier question",
                    timestamp = 0L,
                ),
                ChatMessage(
                    id = 2L,
                    sessionId = sessionId,
                    role = Role.AGENT,
                    content = "earlier answer",
                    timestamp = 1L,
                ),
            ),
        )
        coEvery { retrieveRelevantMemoryUseCase.retrieveScored(any()) } returns listOf(
            MemoryChunk(
                id = 99L,
                text = "user lives in Berlin",
                embedding = FloatArray(0),
                timestamp = 0L,
            ) to 0.77f,
        )

        // ToolRepository: one available tool plus a deterministic execution
        // result. The auto-selector LLM call (see returnsMany below) names
        // "web.search", so this is the tool the engine actually invokes.
        coEvery { toolRepository.getAvailableTools() } returns listOf(
            AgentTool("web.search", "Search the web", "{}"),
        )
        coEvery { toolRepository.executeTool("web.search", any(), any()) } returns "search-result"

        // Cloud client mock — captures the prompt the CLOUD node receives so we
        // can assert that the full-context wrap reached the cloud LLM.
        val mockAnthropicClient: LLMClient = mockk(relaxed = true)
        val capturedCloudPrompt = slot<Prompt>()
        coEvery {
            mockAnthropicClient.executeStreaming(capture(capturedCloudPrompt), any<LLModel>())
        } returns flowOf(StreamFrame.TextDelta("cloud_answer"))
        coEvery { koogClientFactory.createAnthropicExecutor() } returns mockAnthropicClient

        every { apiKeyRepository.getAnthropicKey() } returns flowOf("anthropic-test-key")
        every { apiKeyRepository.getAnthropicModel() } returns flowOf("claude-sonnet-4-5")
        every { apiKeyRepository.getOpenAIKey() } returns flowOf(null)
        every { apiKeyRepository.getGoogleKey() } returns flowOf(null)
        every { apiKeyRepository.getDeepSeekKey() } returns flowOf(null)

        // Two LITE_RT consumers fire in order:
        //   1. ToolNodeExecutor's auto-selection LLM → returns the JSON tool choice
        //   2. OUTPUT (with systemPrompt set) → returns the final formatted reply
        every { llmEngine.generateResponseStream(any()) } returnsMany listOf(
            flowOf("{\"tool\":\"web.search\",\"arguments\":{\"q\":\"weather\"}}"),
            flowOf("final_formatted_reply"),
        )

        // ─── Pipeline definition ───
        // TOOL: nodeInput-only — must NOT see chat history, memory or tool
        //                        results (none yet) in its assembled input.
        // CLOUD: ALL_ENABLED   — should see every block, including the tool
        //                        result produced by the upstream TOOL node.
        // OUTPUT: ALL_ENABLED + systemPrompt — should also see every block.
        val inputNode = NodeModel("input_1", NodeType.INPUT, 0f, 0f)
        val toolNode = NodeModel(
            id = "tool_1",
            type = NodeType.TOOL,
            x = 0f,
            y = 0f,
            toolName = "auto",
            contextConfig = NodeContextConfig(
                chatHistory = false,
                originalTask = false,
                nodeInput = true,
                longTermMemory = false,
                toolResults = false,
            ),
        )
        val cloudNode = NodeModel(
            id = "cloud_1",
            type = NodeType.CLOUD,
            x = 0f,
            y = 0f,
            cloudProvider = "anthropic",
            systemPrompt = "Answer the user.",
            contextConfig = NodeContextConfig.ALL_ENABLED,
        )
        val outputNode = NodeModel(
            id = "output_1",
            type = NodeType.OUTPUT,
            x = 0f,
            y = 0f,
            systemPrompt = "Format reply:",
            contextConfig = NodeContextConfig.ALL_ENABLED,
        )

        val graph = PipelineGraph(
            id = "g1",
            name = "Integration test",
            nodes = listOf(inputNode, toolNode, cloudNode, outputNode),
            connections = listOf(
                ConnectionModel("c1", "input_1", "tool_1"),
                ConnectionModel("c2", "tool_1", "cloud_1"),
                ConnectionModel("c3", "cloud_1", "output_1"),
            ),
        )

        // ─── Act ───
        val states = engine(sessionId, "user prompt", graph).toList()

        // ─── Assert: pipeline ran end-to-end ───
        val completed = states.last() as AgentOrchestratorState.Completed
        assertEquals("final_formatted_reply", completed.finalResponse)

        // ─── Assert: TOOL's auto-selector LITE_RT prompt carries ONLY the
        // Previous Node Output block — no chat history, memory or tool
        // results bleed in (TOOL has not produced any results at that point
        // anyway, but the configuration must also block the other blocks).
        io.mockk.verify {
            llmEngine.generateResponseStream(
                match {
                    it.contains("--- Previous Node Output ---") &&
                        it.contains("user prompt") &&
                        !it.contains("--- Chat History ---") &&
                        !it.contains("--- Long-Term Memory ---") &&
                        !it.contains("--- Tool Results ---") &&
                        !it.contains("--- Original Task ---")
                },
            )
        }

        // ─── Assert: OUTPUT's LITE_RT prompt carries the full ALL_ENABLED
        // wrap, including the tool result accumulated upstream.
        io.mockk.verify {
            llmEngine.generateResponseStream(
                match {
                    it.contains("--- Original Task ---") &&
                        it.contains("user prompt") &&
                        it.contains("--- Chat History ---") &&
                        it.contains("USER: earlier question") &&
                        it.contains("--- Long-Term Memory ---") &&
                        it.contains("user lives in Berlin") &&
                        it.contains("--- Tool Results ---") &&
                        it.contains("web.search: search-result") &&
                        it.contains("--- Previous Node Output ---")
                },
            )
        }

        // ─── Assert: CLOUD received the full context wrap (ALL_ENABLED). The
        // cloud client serialises every prompt message into its own field, so
        // we collapse them into a single string for substring assertions.
        coVerify { mockAnthropicClient.executeStreaming(any<Prompt>(), any<LLModel>()) }
        val cloudPromptText = capturedCloudPrompt.captured.messages.joinToString("\n") { it.textContent() }
        assertTrue(
            "CLOUD prompt missing Original Task block: $cloudPromptText",
            cloudPromptText.contains("--- Original Task ---") && cloudPromptText.contains("user prompt"),
        )
        assertTrue(
            "CLOUD prompt missing Chat History block: $cloudPromptText",
            cloudPromptText.contains("--- Chat History ---") && cloudPromptText.contains("earlier question"),
        )
        assertTrue(
            "CLOUD prompt missing Long-Term Memory block: $cloudPromptText",
            cloudPromptText.contains(
                "--- Long-Term Memory ---",
            ) &&
                cloudPromptText.contains("user lives in Berlin"),
        )
        assertTrue(
            "CLOUD prompt missing Tool Results block: $cloudPromptText",
            cloudPromptText.contains("--- Tool Results ---") &&
                cloudPromptText.contains("web.search: search-result"),
        )
        assertTrue(
            "CLOUD prompt missing Previous Node Output block: $cloudPromptText",
            cloudPromptText.contains("--- Previous Node Output ---"),
        )
    }

    // ─── Agent console event emissions ──────────────────────────

    @Test
    fun `given linear pipeline when run completes then console log spans memory and node lifecycle events`() = runTest {
        every { settingsRepository.pipelineMaxSteps } returns flowOf(15)

        val inputNode = NodeModel("input_1", NodeType.INPUT, 0f, 0f)
        val llmNode = NodeModel("llm_1", NodeType.LITE_RT, 0f, 0f)
        val outputNode = NodeModel("output_1", NodeType.OUTPUT, 0f, 0f)

        val graph = PipelineGraph(
            id = "g1",
            name = "Console Linear",
            nodes = listOf(inputNode, llmNode, outputNode),
            connections = listOf(
                ConnectionModel("c1", "input_1", "llm_1"),
                ConnectionModel("c2", "llm_1", "output_1"),
            ),
        )
        every { llmEngine.generateResponseStream(any()) } returns flowOf("answer")

        // The latest ConsoleLog snapshot contains the full event sequence by
        // construction (the engine accumulates and emits a copy on every push).
        val finalLog = engine(sessionId, "User prompt", graph).toList()
            .filterIsInstance<AgentOrchestratorState.ConsoleLog>()
            .last()
            .events

        // Memory access is reported even when the corpus is empty, carrying the
        // truncated query and a zero-hit count.
        val memEvent = finalLog.single { it.type == ConsoleEventType.MemoryAccess }
        assertTrue("Missing query echo: ${memEvent.message}", memEvent.message.contains("query='User prompt'"))
        assertTrue("Missing zero-hit count: ${memEvent.message}", memEvent.message.contains("0 hits"))

        // Every non-OUTPUT node yields a paired ▶ / ✓ NodeExecution event;
        // OUTPUT only gets a ▶ because its own Completed state already serves
        // as the success marker (engine deliberately suppresses the trailing
        // "✓" so Completed remains the last orchestrator state).
        val nodeMessages = finalLog.filter { it.type == ConsoleEventType.NodeExecution }
            .map { it.message }
        assertTrue("Missing INPUT start", nodeMessages.any { it.startsWith("▶") && it.contains("INPUT") })
        assertTrue("Missing INPUT done", nodeMessages.any { it.startsWith("✓") && it.contains("INPUT") })
        assertTrue("Missing LITE_RT start", nodeMessages.any { it.startsWith("▶") && it.contains("LITE_RT") })
        assertTrue("Missing LITE_RT done", nodeMessages.any { it.startsWith("✓") && it.contains("LITE_RT") })
        assertTrue("Missing OUTPUT start", nodeMessages.any { it.startsWith("▶") && it.contains("OUTPUT") })
        assertTrue("OUTPUT must not push ✓", nodeMessages.none { it.startsWith("✓") && it.contains("OUTPUT") })
    }

    @Test
    fun `given retrieved hits when run completes then MemoryAccess event carries query and scores`() = runTest {
        every { settingsRepository.pipelineMaxSteps } returns flowOf(15)
        coEvery { retrieveRelevantMemoryUseCase.retrieveScored(any()) } returns listOf(
            MemoryChunk(id = 1L, text = "user prefers dark mode", embedding = FloatArray(0), timestamp = 0L) to 0.9f,
            MemoryChunk(id = 2L, text = "user lives in Berlin", embedding = FloatArray(0), timestamp = 0L) to 0.4f,
        )

        val graph = PipelineGraph(
            id = "g1",
            name = "Memory Console",
            nodes = listOf(
                NodeModel("input_1", NodeType.INPUT, 0f, 0f),
                NodeModel("llm_1", NodeType.LITE_RT, 0f, 0f),
                NodeModel("output_1", NodeType.OUTPUT, 0f, 0f),
            ),
            connections = listOf(
                ConnectionModel("c1", "input_1", "llm_1"),
                ConnectionModel("c2", "llm_1", "output_1"),
            ),
        )
        every { llmEngine.generateResponseStream(any()) } returns flowOf("answer")

        val memEvent = engine(sessionId, "what is my UI preference", graph).toList()
            .filterIsInstance<AgentOrchestratorState.ConsoleLog>()
            .last()
            .events
            .single { it.type == ConsoleEventType.MemoryAccess }

        // Terse format (verbose off): single line with query echo, hit count and scores.
        assertEquals(
            "Memory: query='what is my UI preference' → 2 hits (0.90, 0.40)",
            memEvent.message,
        )
    }

    @Test
    fun `given verbose memory logging when run completes then MemoryAccess event expands per-hit snippets`() = runTest {
        every { settingsRepository.pipelineMaxSteps } returns flowOf(15)
        every { settingsRepository.verboseMemoryLoggingEnabled } returns flowOf(true)
        coEvery { retrieveRelevantMemoryUseCase.retrieveScored(any()) } returns listOf(
            MemoryChunk(id = 1L, text = "user prefers dark mode", embedding = FloatArray(0), timestamp = 0L) to 0.9f,
        )

        val graph = PipelineGraph(
            id = "g1",
            name = "Memory Console Verbose",
            nodes = listOf(
                NodeModel("input_1", NodeType.INPUT, 0f, 0f),
                NodeModel("llm_1", NodeType.LITE_RT, 0f, 0f),
                NodeModel("output_1", NodeType.OUTPUT, 0f, 0f),
            ),
            connections = listOf(
                ConnectionModel("c1", "input_1", "llm_1"),
                ConnectionModel("c2", "llm_1", "output_1"),
            ),
        )
        every { llmEngine.generateResponseStream(any()) } returns flowOf("answer")

        val memEvent = engine(sessionId, "prefs", graph).toList()
            .filterIsInstance<AgentOrchestratorState.ConsoleLog>()
            .last()
            .events
            .single { it.type == ConsoleEventType.MemoryAccess }

        assertTrue(
            "Missing header line: ${memEvent.message}",
            memEvent.message.startsWith("Memory: query='prefs' → 1 hits (0.90)"),
        )
        assertTrue(
            "Missing per-hit snippet: ${memEvent.message}",
            memEvent.message.contains("1. [0.90] user prefers dark mode"),
        )
    }

    @Test
    fun `given pipeline ending without OUTPUT when run terminates then last console event is Error`() = runTest {
        every { settingsRepository.pipelineMaxSteps } returns flowOf(15)

        val inputNode = NodeModel("input_1", NodeType.INPUT, 0f, 0f)
        val llmNode = NodeModel("llm_1", NodeType.LITE_RT, 0f, 0f)

        val graph = PipelineGraph(
            id = "g1",
            name = "No Output",
            nodes = listOf(inputNode, llmNode),
            connections = listOf(ConnectionModel("c1", "input_1", "llm_1")),
        )
        every { llmEngine.generateResponseStream(any()) } returns flowOf("Response")

        val finalLog = engine(sessionId, "Prompt", graph).toList()
            .filterIsInstance<AgentOrchestratorState.ConsoleLog>()
            .last()
            .events

        assertEquals(ConsoleEventType.Error, finalLog.last().type)
        assertTrue(
            "Last error should mention missing OUTPUT: ${finalLog.last().message}",
            finalLog.last().message.contains("OUTPUT", ignoreCase = true),
        )
    }

    // ─── Risk-based HITL gate end-to-end ──────────────────────

    @Test
    fun `given pipeline with READ_ONLY tool node when run then completes without HITL pause`() = runTest {
        every { settingsRepository.pipelineMaxSteps } returns flowOf(15)
        every { settingsRepository.toolApprovalPolicy } returns flowOf(ToolApprovalPolicy.SensitiveOrDestructive)
        every { settingsRepository.blockDestructiveTools } returns flowOf(false)

        // READ_ONLY + global override OFF must skip the HITL gate entirely: no
        // WaitingForApproval emission, no notifier call, and the pipeline
        // reaches OUTPUT without intervention.
        coEvery { toolRepository.getRisk("web.search") } returns ToolRisk.READ_ONLY
        coEvery { toolRepository.getAvailableTools() } returns listOf(AgentTool("web.search", "Search", "{}"))
        coEvery { toolRepository.executeTool("web.search", any(), any()) } returns "search-result"

        val inputNode = NodeModel("input", NodeType.INPUT, 0f, 0f)
        val toolNode = NodeModel(
            id = "tool",
            type = NodeType.TOOL,
            x = 0f,
            y = 0f,
            toolName = "web.search",
        )
        val outputNode = NodeModel("output", NodeType.OUTPUT, 0f, 0f, systemPrompt = null)

        val graph = PipelineGraph(
            id = "g1",
            name = "Read-only Tool Without HITL",
            nodes = listOf(inputNode, toolNode, outputNode),
            connections = listOf(
                ConnectionModel("c1", "input", "tool"),
                ConnectionModel("c2", "tool", "output"),
            ),
        )

        every { llmEngine.generateResponseStream(any()) } returns
            flowOf("""{"tool":"web.search","arguments":"q=weather"}""")

        val emissions = engine(sessionId, "find weather", graph).toList()

        val sawApproval = emissions.any { it is AgentOrchestratorState.WaitingForApproval }
        assertTrue("READ_ONLY tool must not pause for approval", !sawApproval)
        verify(exactly = 0) { approvalNotifier.sendApprovalRequest(any(), any(), any(), any()) }
        assertTrue(
            "Pipeline should reach Completed when HITL is skipped",
            emissions.any { it is AgentOrchestratorState.Completed },
        )
    }

    // ─── Persistent run write-through ──────────────────────────

    @Test
    fun `given runId then currentNodeId is written for every executed node`() = runTest {
        every { settingsRepository.pipelineMaxSteps } returns flowOf(15)

        val graph = PipelineGraph(
            id = "g1",
            name = "Linear",
            nodes = listOf(
                NodeModel("input_1", NodeType.INPUT, 0f, 0f),
                NodeModel("llm_1", NodeType.LITE_RT, 0f, 0f),
                NodeModel("output_1", NodeType.OUTPUT, 0f, 0f, systemPrompt = null),
            ),
            connections = listOf(
                ConnectionModel("c1", "input_1", "llm_1"),
                ConnectionModel("c2", "llm_1", "output_1"),
            ),
        )
        every { llmEngine.generateResponseStream(any()) } returns flowOf("answer")

        engine(sessionId, "prompt", graph, "run-42").toList()

        coVerifyOrder {
            pipelineRunRepository.updateCurrentNode("run-42", "input_1")
            pipelineRunRepository.updateCurrentNode("run-42", "llm_1")
            pipelineRunRepository.updateCurrentNode("run-42", "output_1")
        }
    }

    @Test
    fun `given null runId then run persistence is never touched`() = runTest {
        every { settingsRepository.pipelineMaxSteps } returns flowOf(15)

        val graph = PipelineGraph(
            id = "g1",
            name = "Linear",
            nodes = listOf(
                NodeModel("input_1", NodeType.INPUT, 0f, 0f),
                NodeModel("output_1", NodeType.OUTPUT, 0f, 0f, systemPrompt = null),
            ),
            connections = listOf(ConnectionModel("c1", "input_1", "output_1")),
        )
        every { llmEngine.generateResponseStream(any()) } returns flowOf("answer")

        engine(sessionId, "prompt", graph).toList()

        coVerify(exactly = 0) { pipelineRunRepository.updateCurrentNode(any(), any()) }
        coVerify(exactly = 0) { pipelineRunRepository.updateStatus(any(), any()) }
    }

    /**
     * A CLARIFICATION suspension persists WAITING_CLARIFICATION when the
     * question is surfaced, and the record flips back to RUNNING on the first
     * state forwarded after the user's reply resolves the suspension.
     */
    @Test
    fun `given clarification suspension then record is WAITING_CLARIFICATION then RUNNING`() = runTest {
        val graph = PipelineGraph(
            id = "g1",
            name = "Clarification",
            nodes = listOf(
                NodeModel("input_1", NodeType.INPUT, 0f, 0f),
                NodeModel(
                    id = "clar_1",
                    type = NodeType.CLARIFICATION,
                    x = 0f,
                    y = 0f,
                    systemPrompt = "Ask user for clarification.",
                    clarificationTimeoutMs = 5_000L,
                ),
                NodeModel("output_1", NodeType.OUTPUT, 0f, 0f, systemPrompt = null),
            ),
            connections = listOf(
                ConnectionModel("c1", "input_1", "clar_1"),
                ConnectionModel("c2", "clar_1", "output_1"),
            ),
        )
        every { llmEngine.generateResponseStream(any()) } returns flowOf(
            "{\"question\":\"Confirm?\",\"options\":[\"yes\",\"no\"]}",
        )
        coEvery { clarificationRepository.requestAnswer(any()) } returns "user reply"

        engine(sessionId, "prompt", graph, "run-43").toList()

        coVerifyOrder {
            pipelineRunRepository.updateStatus("run-43", PipelineRunStatus.WAITING_CLARIFICATION)
            pipelineRunRepository.updateStatus("run-43", PipelineRunStatus.RUNNING)
        }
    }

    /**
     * A SENSITIVE tool's HITL gate persists WAITING_APPROVAL while the run
     * is suspended on the user, and the record flips back to RUNNING once
     * the approval resolves and execution proceeds.
     */
    @Test
    fun `given approval suspension then record is WAITING_APPROVAL then RUNNING`() = runTest {
        every { settingsRepository.pipelineMaxSteps } returns flowOf(15)
        every { settingsRepository.toolApprovalPolicy } returns flowOf(ToolApprovalPolicy.SensitiveOrDestructive)
        every { settingsRepository.blockDestructiveTools } returns flowOf(false)
        every { settingsRepository.toolCallTimeoutMs } returns flowOf(5_000L)
        coEvery { toolRepository.getRisk("sens.tool") } returns ToolRisk.SENSITIVE
        coEvery { toolRepository.getAvailableTools() } returns listOf(AgentTool("sens.tool", "Desc", "{}"))
        coEvery { toolRepository.executeTool("sens.tool", any(), any()) } returns "tool-result"

        val graph = PipelineGraph(
            id = "g1",
            name = "Sensitive Tool",
            nodes = listOf(
                NodeModel("input_1", NodeType.INPUT, 0f, 0f),
                NodeModel("tool_1", NodeType.TOOL, 0f, 0f, toolName = "sens.tool"),
                NodeModel("output_1", NodeType.OUTPUT, 0f, 0f, systemPrompt = null),
            ),
            connections = listOf(
                ConnectionModel("c1", "input_1", "tool_1"),
                ConnectionModel("c2", "tool_1", "output_1"),
            ),
        )
        every { llmEngine.generateResponseStream(any()) } returns
            flowOf("""{"tool":"sens.tool","arguments":"a=1"}""")

        val job = launch {
            engine(sessionId, "prompt", graph, "run-44").toList()
        }
        // Flush pending tasks WITHOUT advancing virtual time so the executor
        // suspends inside the approval gate without firing the timeout.
        runCurrent()
        engine.resumeWithApproval(sessionId, true)
        advanceUntilIdle()

        coVerifyOrder {
            pipelineRunRepository.updateStatus("run-44", PipelineRunStatus.WAITING_APPROVAL)
            pipelineRunRepository.updateStatus("run-44", PipelineRunStatus.RUNNING)
        }
        job.cancel()
    }

    /**
     * The persistent run trace must be complete after a run: every console
     * event and every per-node I/O snapshot reaches the trace repository,
     * attributed to the run, with a strictly monotonic per-run seq shared
     * across both record kinds, and the buffer is force-flushed at the
     * terminal point.
     */
    @Test
    fun `given persisted run when pipeline completes then full trace lands in repository`() = runTest {
        every { settingsRepository.pipelineMaxSteps } returns flowOf(15)
        val appended = mutableListOf<RunTraceRecord>()
        coEvery { runTraceRepository.append(capture(appended)) } returns Unit

        val graph = PipelineGraph(
            id = "g-trace",
            name = "Trace Completeness",
            nodes = listOf(
                NodeModel("input", NodeType.INPUT, 0f, 0f),
                NodeModel("llm", NodeType.LITE_RT, 0f, 0f),
                NodeModel("output", NodeType.OUTPUT, 0f, 0f, systemPrompt = null),
            ),
            connections = listOf(
                ConnectionModel("c1", "input", "llm"),
                ConnectionModel("c2", "llm", "output"),
            ),
        )
        every { llmEngine.generateResponseStream(any()) } returnsMany listOf(
            flowOf("answer"),
            flowOf("final"),
        )

        val states = engine(sessionId, "prompt", graph, runId = "run-full").toList()

        // Every console event emitted to the UI also landed in the trace.
        val emittedConsoleCount =
            states.filterIsInstance<AgentOrchestratorState.ConsoleLog>().maxOf { it.events.size }
        val persistedConsole = appended.filterIsInstance<RunTraceRecord.ConsoleEntry>()
        assertEquals(emittedConsoleCount, persistedConsole.size)
        assertTrue(persistedConsole.any { it.message == "▶ LITE_RT" })
        // The LITE_RT node's I/O snapshot landed with its full input/output pair.
        val nodeIo = appended.filterIsInstance<RunTraceRecord.NodeIo>().single { it.nodeId == "llm" }
        assertEquals("LITE_RT", nodeIo.nodeType)
        assertEquals("answer", nodeIo.outputText)
        assertTrue(nodeIo.inputText.isNotBlank())
        // Run attribution and strictly monotonic seq across both record kinds.
        assertTrue(appended.all { it.runId == "run-full" && it.sessionId == sessionId })
        val seqs = appended.map { it.seq }
        assertEquals(seqs.sorted(), seqs)
        assertEquals(seqs.size, seqs.distinct().size)
        // Terminal flush makes the trace durable the moment the run ends.
        coVerify(atLeast = 1) { runTraceRepository.flush() }
    }

    /**
     * `runId = null` (editor test runs, legacy flows) disables trace
     * persistence entirely — no appends, no flushes.
     */
    @Test
    fun `given no runId when pipeline completes then no trace records are appended`() = runTest {
        every { settingsRepository.pipelineMaxSteps } returns flowOf(15)

        val graph = PipelineGraph(
            id = "g-no-run",
            name = "No Run Id",
            nodes = listOf(
                NodeModel("input", NodeType.INPUT, 0f, 0f),
                NodeModel("output", NodeType.OUTPUT, 0f, 0f, systemPrompt = null),
            ),
            connections = listOf(ConnectionModel("c1", "input", "output")),
        )
        every { llmEngine.generateResponseStream(any()) } returns flowOf("final")

        engine(sessionId, "prompt", graph).toList()

        coVerify(exactly = 0) { runTraceRepository.append(any()) }
        coVerify(exactly = 0) { runTraceRepository.flush() }
    }

    /**
     * Entering a HITL suspension force-flushes the buffered trace right
     * after the WAITING_APPROVAL status write — the process may die while
     * waiting, so the persisted trace must already cover everything up to
     * the suspension point.
     */
    @Test
    fun `given approval suspension then trace is flushed at the suspension point`() = runTest {
        every { settingsRepository.pipelineMaxSteps } returns flowOf(15)
        every { settingsRepository.toolApprovalPolicy } returns flowOf(ToolApprovalPolicy.SensitiveOrDestructive)
        every { settingsRepository.blockDestructiveTools } returns flowOf(false)
        every { settingsRepository.toolCallTimeoutMs } returns flowOf(5_000L)
        coEvery { toolRepository.getRisk("sens.tool") } returns ToolRisk.SENSITIVE
        coEvery { toolRepository.getAvailableTools() } returns listOf(AgentTool("sens.tool", "Desc", "{}"))
        coEvery { toolRepository.executeTool("sens.tool", any(), any()) } returns "tool-result"

        val graph = PipelineGraph(
            id = "g-susp-flush",
            name = "Suspension Flush",
            nodes = listOf(
                NodeModel("input_1", NodeType.INPUT, 0f, 0f),
                NodeModel("tool_1", NodeType.TOOL, 0f, 0f, toolName = "sens.tool"),
                NodeModel("output_1", NodeType.OUTPUT, 0f, 0f, systemPrompt = null),
            ),
            connections = listOf(
                ConnectionModel("c1", "input_1", "tool_1"),
                ConnectionModel("c2", "tool_1", "output_1"),
            ),
        )
        every { llmEngine.generateResponseStream(any()) } returns
            flowOf("""{"tool":"sens.tool","arguments":"a=1"}""")

        val job = launch {
            engine(sessionId, "prompt", graph, "run-45").toList()
        }
        // Flush pending tasks WITHOUT advancing virtual time so the executor
        // suspends inside the approval gate without firing the timeout.
        runCurrent()
        engine.resumeWithApproval(sessionId, true)
        advanceUntilIdle()

        // The suspension flush must land between the WAITING_APPROVAL write
        // and the RUNNING flip — the terminal flush (after RUNNING) cannot
        // satisfy this order, so the assertion pins the suspension-point
        // flush specifically.
        coVerifyOrder {
            pipelineRunRepository.updateStatus("run-45", PipelineRunStatus.WAITING_APPROVAL)
            runTraceRepository.flush()
            pipelineRunRepository.updateStatus("run-45", PipelineRunStatus.RUNNING)
        }
        job.cancel()
    }

    // ─── Checkpoint resume ──────────────────────────────────────────────────

    /** Recorded NodeIo snapshot shorthand for the resume tests. */
    private fun nodeIoRecord(
        runId: String,
        seq: Long,
        nodeId: String,
        nodeType: NodeType,
        outputText: String,
        conditionResult: Boolean? = null,
        routingKey: String? = null,
        resolvedToolName: String? = null,
    ): RunTraceRecord.NodeIo = RunTraceRecord.NodeIo(
        runId = runId,
        sessionId = sessionId,
        seq = seq,
        timestamp = 0L,
        nodeId = nodeId,
        nodeType = nodeType.name,
        inputText = "recorded-input",
        outputText = outputText,
        durationMs = 5L,
        tokenCount = null,
        conditionResult = conditionResult,
        routingKey = routingKey,
        resolvedToolName = resolvedToolName,
    )

    @Test
    fun `given resume with full prefix then completed nodes replay without executor calls`() = runTest {
        val graph = PipelineGraph(
            id = "g-resume",
            name = "Resume",
            nodes = listOf(
                NodeModel("input_1", NodeType.INPUT, 0f, 0f),
                NodeModel("llm_1", NodeType.LITE_RT, 0f, 0f),
                NodeModel("output_1", NodeType.OUTPUT, 0f, 0f, systemPrompt = null),
            ),
            connections = listOf(
                ConnectionModel("c1", "input_1", "llm_1"),
                ConnectionModel("c2", "llm_1", "output_1"),
            ),
        )
        val resume = ResumeContext(
            records = listOf(nodeIoRecord("run-r1", 3L, "llm_1", NodeType.LITE_RT, "Recorded")),
            memorySnapshot = null,
            nextSeq = 4L,
        )

        val states = engine(sessionId, "prompt", graph, "run-r1", resume).toList()

        // The LITE_RT node replays from the checkpoint: zero LLM calls, the
        // recorded output reaches OUTPUT (echo mode), and the compact replay
        // event lands in the console.
        verify(exactly = 0) { llmEngine.generateResponseStream(any()) }
        val completed = states.last() as AgentOrchestratorState.Completed
        assertEquals("Recorded", completed.finalResponse)
        val consoleLines = states.filterIsInstance<AgentOrchestratorState.ConsoleLog>()
            .last().events.map { it.message }
        assertTrue(consoleLines.any { it.contains("replayed from checkpoint") })
        // The replayed node appends no second NodeIo record.
        coVerify(exactly = 0) {
            runTraceRepository.append(match { it is RunTraceRecord.NodeIo && it.nodeId == "llm_1" })
        }
    }

    @Test
    fun `given resume with partial prefix then first unrecorded node executes live with continued seq`() = runTest {
        val graph = PipelineGraph(
            id = "g-resume-partial",
            name = "Resume Partial",
            nodes = listOf(
                NodeModel("input_1", NodeType.INPUT, 0f, 0f),
                NodeModel("llm_1", NodeType.LITE_RT, 0f, 0f),
                NodeModel("llm_2", NodeType.LITE_RT, 0f, 0f),
                NodeModel("output_1", NodeType.OUTPUT, 0f, 0f, systemPrompt = null),
            ),
            connections = listOf(
                ConnectionModel("c1", "input_1", "llm_1"),
                ConnectionModel("c2", "llm_1", "llm_2"),
                ConnectionModel("c3", "llm_2", "output_1"),
            ),
        )
        every { llmEngine.generateResponseStream(any()) } returns flowOf("Live")
        val resume = ResumeContext(
            records = listOf(nodeIoRecord("run-r2", 2L, "llm_1", NodeType.LITE_RT, "Recorded")),
            memorySnapshot = null,
            nextSeq = 3L,
        )

        val states = engine(sessionId, "prompt", graph, "run-r2", resume).toList()

        // Exactly one live LLM call (llm_2); llm_1 replayed.
        verify(exactly = 1) { llmEngine.generateResponseStream(any()) }
        assertEquals("Live", (states.last() as AgentOrchestratorState.Completed).finalResponse)
        // The live node's NodeIo continues the persisted seq numbering.
        val appended = mutableListOf<RunTraceRecord>()
        coVerify { runTraceRepository.append(capture(appended)) }
        val liveNodeIo = appended.filterIsInstance<RunTraceRecord.NodeIo>().single { it.nodeId == "llm_2" }
        assertTrue("live record seq must continue after nextSeq", liveNodeIo.seq >= 3L)
    }

    @Test
    fun `given resume with recorded IF_CONDITION verdict then branch is restored without re-evaluation`() = runTest {
        val graph = PipelineGraph(
            id = "g-resume-if",
            name = "Resume If",
            nodes = listOf(
                NodeModel("input_1", NodeType.INPUT, 0f, 0f),
                NodeModel("if_1", NodeType.IF_CONDITION, 0f, 0f),
                NodeModel("llm_true", NodeType.LITE_RT, 0f, 0f),
                NodeModel("llm_false", NodeType.LITE_RT, 0f, 0f),
                NodeModel("output_1", NodeType.OUTPUT, 0f, 0f, systemPrompt = null),
            ),
            connections = listOf(
                ConnectionModel("c1", "input_1", "if_1"),
                ConnectionModel("c2", "if_1", "llm_true", label = "True"),
                ConnectionModel("c3", "if_1", "llm_false", label = "False"),
                ConnectionModel("c4", "llm_true", "output_1"),
                ConnectionModel("c5", "llm_false", "output_1"),
            ),
        )
        every { llmEngine.generateResponseStream(any()) } returns flowOf("FalseBranch")
        val resume = ResumeContext(
            records = listOf(
                nodeIoRecord(
                    runId = "run-r3",
                    seq = 1L,
                    nodeId = "if_1",
                    nodeType = NodeType.IF_CONDITION,
                    outputText = "prompt",
                    conditionResult = false,
                ),
            ),
            memorySnapshot = null,
            nextSeq = 2L,
        )

        val states = engine(sessionId, "prompt", graph, "run-r3", resume).toList()

        // The condition is never re-evaluated (the strict mock would throw),
        // and the recorded False verdict routes into llm_false.
        coVerify(exactly = 0) { evaluateIfConditionUseCase(any(), any()) }
        assertEquals("FalseBranch", (states.last() as AgentOrchestratorState.Completed).finalResponse)
    }

    @Test
    fun `given resume with completed TOOL record then observation replays without HITL`() = runTest {
        every { settingsRepository.toolApprovalPolicy } returns flowOf(ToolApprovalPolicy.SensitiveOrDestructive)
        every { settingsRepository.blockDestructiveTools } returns flowOf(false)
        val graph = PipelineGraph(
            id = "g-resume-tool-done",
            name = "Resume Tool Done",
            nodes = listOf(
                NodeModel("input_1", NodeType.INPUT, 0f, 0f),
                NodeModel("tool_1", NodeType.TOOL, 0f, 0f, toolName = "sens.tool"),
                NodeModel("output_1", NodeType.OUTPUT, 0f, 0f, systemPrompt = null),
            ),
            connections = listOf(
                ConnectionModel("c1", "input_1", "tool_1"),
                ConnectionModel("c2", "tool_1", "output_1"),
            ),
        )
        val resume = ResumeContext(
            records = listOf(
                nodeIoRecord(
                    runId = "run-r4",
                    seq = 1L,
                    nodeId = "tool_1",
                    nodeType = NodeType.TOOL,
                    outputText = "tool-observation",
                    resolvedToolName = "sens.tool",
                ),
            ),
            memorySnapshot = null,
            nextSeq = 2L,
        )

        val states = engine(sessionId, "prompt", graph, "run-r4", resume).toList()

        // Completed before the interruption → replayed: no approval gate, no
        // tool execution, the recorded observation flows to OUTPUT.
        assertTrue(states.filterIsInstance<AgentOrchestratorState.WaitingForApproval>().isEmpty())
        coVerify(exactly = 0) { toolRepository.executeTool(any(), any(), any()) }
        assertEquals("tool-observation", (states.last() as AgentOrchestratorState.Completed).finalResponse)
    }

    @Test
    fun `given resume stopping at TOOL node then tool executes live with fresh HITL`() = runTest {
        every { settingsRepository.toolApprovalPolicy } returns flowOf(ToolApprovalPolicy.SensitiveOrDestructive)
        every { settingsRepository.blockDestructiveTools } returns flowOf(false)
        every { settingsRepository.toolCallTimeoutMs } returns flowOf(5_000L)
        coEvery { toolRepository.getRisk("sens.tool") } returns ToolRisk.SENSITIVE
        coEvery { toolRepository.getAvailableTools() } returns listOf(AgentTool("sens.tool", "Desc", "{}"))
        coEvery { toolRepository.executeTool("sens.tool", any(), any()) } returns "fresh-result"

        val graph = PipelineGraph(
            id = "g-resume-tool-live",
            name = "Resume Tool Live",
            nodes = listOf(
                NodeModel("input_1", NodeType.INPUT, 0f, 0f),
                NodeModel("llm_1", NodeType.LITE_RT, 0f, 0f),
                NodeModel("tool_1", NodeType.TOOL, 0f, 0f, toolName = "sens.tool"),
                NodeModel("output_1", NodeType.OUTPUT, 0f, 0f, systemPrompt = null),
            ),
            connections = listOf(
                ConnectionModel("c1", "input_1", "llm_1"),
                ConnectionModel("c2", "llm_1", "tool_1"),
                ConnectionModel("c3", "tool_1", "output_1"),
            ),
        )
        // The TOOL executor's planning step goes through the LLM; the replayed
        // llm_1 never calls it, so the single stream stub serves tool planning.
        every { llmEngine.generateResponseStream(any()) } returns
            flowOf("""{"tool":"sens.tool","arguments":"a=1"}""")
        val resume = ResumeContext(
            records = listOf(
                nodeIoRecord("run-r5", 1L, "llm_1", NodeType.LITE_RT, """{"tool":"sens.tool","arguments":"a=1"}"""),
            ),
            memorySnapshot = null,
            nextSeq = 2L,
        )

        val states = mutableListOf<AgentOrchestratorState>()
        val job = launch {
            engine(sessionId, "prompt", graph, "run-r5", resume).toList(states)
        }
        // Walk past the per-node prewarm delays (INPUT + TOOL, 500 ms each;
        // the replayed llm_1 skips its delay) without reaching the 5 s
        // approval timeout, so the gate is raised but still pending.
        advanceTimeBy(1_500L)
        runCurrent()
        // Interrupted at the TOOL node → never replayed: a fresh approval
        // gate must be raised even though the run is a resume.
        assertTrue(states.filterIsInstance<AgentOrchestratorState.WaitingForApproval>().isNotEmpty())
        engine.resumeWithApproval(sessionId, true)
        advanceUntilIdle()

        coVerify(exactly = 1) { toolRepository.executeTool("sens.tool", any(), any()) }
        assertEquals("fresh-result", (states.last() as AgentOrchestratorState.Completed).finalResponse)
        job.cancel()
    }

    @Test
    fun `given resume whose trace diverges from the graph walk then run fails with explicit error`() = runTest {
        val graph = PipelineGraph(
            id = "g-resume-diverged",
            name = "Resume Diverged",
            nodes = listOf(
                NodeModel("input_1", NodeType.INPUT, 0f, 0f),
                NodeModel("llm_1", NodeType.LITE_RT, 0f, 0f),
                NodeModel("output_1", NodeType.OUTPUT, 0f, 0f, systemPrompt = null),
            ),
            connections = listOf(
                ConnectionModel("c1", "input_1", "llm_1"),
                ConnectionModel("c2", "llm_1", "output_1"),
            ),
        )
        val resume = ResumeContext(
            records = listOf(nodeIoRecord("run-r6", 1L, "some_other_node", NodeType.LITE_RT, "stale")),
            memorySnapshot = null,
            nextSeq = 2L,
        )

        val states = engine(sessionId, "prompt", graph, "run-r6", resume).toList()

        val error = states.last() as AgentOrchestratorState.Error
        assertTrue(error.message.contains("no longer matches"))
        verify(exactly = 0) { llmEngine.generateResponseStream(any()) }
    }

    @Test
    fun `given resume with memory snapshot then retrieval is not re-run and the block is rebuilt`() = runTest {
        val graph = PipelineGraph(
            id = "g-resume-mem",
            name = "Resume Memory",
            nodes = listOf(
                NodeModel("input_1", NodeType.INPUT, 0f, 0f),
                NodeModel(
                    id = "llm_1",
                    type = NodeType.LITE_RT,
                    x = 0f,
                    y = 0f,
                    contextConfig = NodeContextConfig(
                        chatHistory = false,
                        originalTask = false,
                        nodeInput = false,
                        longTermMemory = true,
                        toolResults = false,
                    ),
                ),
                NodeModel("output_1", NodeType.OUTPUT, 0f, 0f, systemPrompt = null),
            ),
            connections = listOf(
                ConnectionModel("c1", "input_1", "llm_1"),
                ConnectionModel("c2", "llm_1", "output_1"),
            ),
        )
        every { llmEngine.generateResponseStream(any()) } returns flowOf("ok")
        val resume = ResumeContext(
            records = emptyList(),
            memorySnapshot = listOf(
                MemoryChunk(id = 7L, text = "user prefers dark mode", embedding = FloatArray(0), timestamp = 0L),
            ),
            nextSeq = 1L,
        )

        engine(sessionId, "prompt", graph, "run-r7", resume).toList()

        // The snapshot seeds the memoized list: no fresh retrieval, no usage
        // re-count, and the snapshot chunk reaches the LLM prompt.
        coVerify(exactly = 0) { retrieveRelevantMemoryUseCase.retrieveScored(any()) }
        coVerify(exactly = 0) { memoryRepository.recordUsage(any(), any()) }
        verify {
            llmEngine.generateResponseStream(
                match { it.contains("--- Long-Term Memory ---") && it.contains("user prefers dark mode") },
            )
        }
    }

    @Test
    fun `given fresh memory resolution with runId then memory snapshot record lands in the trace`() = runTest {
        coEvery { retrieveRelevantMemoryUseCase.retrieveScored(any()) } returns listOf(
            MemoryChunk(id = 7L, text = "user prefers dark mode", embedding = FloatArray(0), timestamp = 0L) to 0.8f,
        )
        val graph = PipelineGraph(
            id = "g-mem-snap",
            name = "Memory Snapshot",
            nodes = listOf(
                NodeModel("input_1", NodeType.INPUT, 0f, 0f),
                NodeModel(
                    id = "llm_1",
                    type = NodeType.LITE_RT,
                    x = 0f,
                    y = 0f,
                    contextConfig = NodeContextConfig(
                        chatHistory = false,
                        originalTask = false,
                        nodeInput = false,
                        longTermMemory = true,
                        toolResults = false,
                    ),
                ),
                NodeModel("output_1", NodeType.OUTPUT, 0f, 0f, systemPrompt = null),
            ),
            connections = listOf(
                ConnectionModel("c1", "input_1", "llm_1"),
                ConnectionModel("c2", "llm_1", "output_1"),
            ),
        )
        every { llmEngine.generateResponseStream(any()) } returns flowOf("ok")

        engine(sessionId, "prompt", graph, "run-snap").toList()

        coVerify {
            runTraceRepository.append(
                match {
                    it is RunTraceRecord.MemorySnapshot &&
                        it.runId == "run-snap" &&
                        it.entries.single().text == "user prefers dark mode"
                },
            )
        }
    }
}
