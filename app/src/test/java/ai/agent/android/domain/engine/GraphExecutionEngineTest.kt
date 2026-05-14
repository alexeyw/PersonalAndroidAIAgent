package ai.agent.android.domain.engine

import ai.agent.android.data.engine.KoogClientFactory
import ai.agent.android.data.engine.KoogCloudLlmModelResolver
import ai.agent.android.data.repositories.ClarificationRepositoryImpl
import ai.agent.android.domain.engine.executors.ClarificationNodeExecutor
import ai.agent.android.domain.engine.executors.CloudLlmNodeExecutor
import ai.agent.android.domain.engine.executors.IfConditionNodeExecutor
import ai.agent.android.domain.engine.executors.InputNodeExecutor
import ai.agent.android.domain.engine.executors.LiteRtNodeExecutor
import ai.agent.android.domain.engine.executors.NodeExecutorFactory
import ai.agent.android.domain.engine.executors.OutputNodeExecutor
import ai.agent.android.domain.engine.executors.QueueProcessorNodeExecutor
import ai.agent.android.domain.engine.executors.SummaryNodeExecutor
import ai.agent.android.domain.engine.executors.SystemNodeExecutor
import ai.agent.android.domain.engine.executors.ToolNodeExecutor
import ai.agent.android.domain.models.AgentOrchestratorState
import ai.agent.android.domain.models.AgentTool
import ai.agent.android.domain.models.ChatMessage
import ai.agent.android.domain.models.CloudProvider
import ai.agent.android.domain.models.ConnectionModel
import ai.agent.android.domain.models.ConsoleEventType
import ai.agent.android.domain.models.MemoryChunk
import ai.agent.android.domain.models.NodeContextConfig
import ai.agent.android.domain.models.NodeModel
import ai.agent.android.domain.models.NodeType
import ai.agent.android.domain.models.PipelineGraph
import ai.agent.android.domain.models.Result
import ai.agent.android.domain.models.Role
import ai.agent.android.domain.models.ToolRisk
import ai.agent.android.domain.prompt.PromptTemplateEngine
import ai.agent.android.domain.prompt.PromptVariableProvider
import ai.agent.android.domain.repositories.ApiKeyRepository
import ai.agent.android.domain.repositories.ChatRepository
import ai.agent.android.domain.repositories.ClarificationRepository
import ai.agent.android.domain.repositories.CrashReportingRepository
import ai.agent.android.domain.repositories.LocalModelRepository
import ai.agent.android.domain.repositories.MetricsRepository
import ai.agent.android.domain.repositories.SettingsRepository
import ai.agent.android.domain.repositories.ToolRepository
import ai.agent.android.domain.services.ApprovalNotifier
import ai.agent.android.domain.usecases.EvaluateIfConditionUseCase
import ai.agent.android.domain.usecases.GetContextWindowUseCase
import ai.agent.android.domain.usecases.LoadModelUseCase
import ai.agent.android.domain.usecases.RetrieveRelevantMemoryUseCase
import ai.koog.prompt.dsl.Prompt
import ai.koog.prompt.executor.clients.LLMClient
import ai.koog.prompt.executor.clients.anthropic.AnthropicModels
import ai.koog.prompt.llm.LLModel
import ai.koog.prompt.streaming.StreamFrame
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
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
    private lateinit var cloudLlmModelResolver: KoogCloudLlmModelResolver
    private lateinit var evaluateIfConditionUseCase: EvaluateIfConditionUseCase
    private lateinit var loadModelUseCase: LoadModelUseCase
    private lateinit var clarificationRepository: ClarificationRepository
    private lateinit var crashReportingRepository: CrashReportingRepository
    private lateinit var localModelRepository: LocalModelRepository

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
        evaluateIfConditionUseCase = mockk()
        loadModelUseCase = mockk()
        crashReportingRepository = mockk(relaxed = true)
        localModelRepository = mockk(relaxed = true)
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
        )

        coEvery { getContextWindowUseCase(sessionId) } returns ""
        coEvery { retrieveRelevantMemoryUseCase(any()) } returns emptyList()
        every { chatRepository.getMessagesForSession(any()) } returns flowOf(emptyList())
        every { settingsRepository.systemPromptPrefix } returns flowOf("")
        every { settingsRepository.toolUsageInstruction } returns flowOf("")
        every { settingsRepository.requiresUserConfirmation } returns flowOf(false)
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
        // not the routing key. The Phase 17 cleanup removed the legacy "USER/INPUT:" prefix
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

        val states = engine(sessionId, "prompt", graph).toList()

        val trace = states.filterIsInstance<AgentOrchestratorState.PipelineTrace>().last()
        assertTrue(trace.steps.any { it.nodeName == "LITE_RT" && (it.tokenCount ?: 0) > 0 })
        assertTrue(trace.steps.all { it.durationMs >= 0 })
        coVerify(atLeast = 1) {
            chatRepository.saveTraceStep(
                sessionId = any(),
                nodeName = "LITE_RT",
                outputText = any(),
                durationMs = any(),
                tokenCount = match { it != null && it > 0 },
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
        val cloudPromptText = capturedPrompt.captured.messages.joinToString("\n") { it.content }
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
    fun `given TOOL node configured as auto when executed then resolved tool name reaches downstream`() = runTest {
        every { settingsRepository.pipelineMaxSteps } returns flowOf(15)
        every { settingsRepository.requiresUserConfirmation } returns flowOf(false)

        // Two tools registered; the LITE_RT used by ToolNodeExecutor for auto-selection
        // returns a JSON object naming "web.search" — so the observation must be
        // attributed to "web.search", not to the configured placeholder "auto".
        coEvery { toolRepository.getAvailableTools() } returns listOf(
            AgentTool("web.search", "Search the web", "{}"),
            AgentTool("calendar.read", "Read the calendar", "{}"),
        )
        coEvery { toolRepository.executeTool("web.search", any()) } returns "search-result"

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
        every { settingsRepository.requiresUserConfirmation } returns flowOf(false)

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
        coEvery { retrieveRelevantMemoryUseCase(any()) } returns listOf(
            MemoryChunk(
                id = 99L,
                text = "user lives in Berlin",
                embedding = FloatArray(0),
                timestamp = 0L,
            ),
        )

        // ToolRepository: one available tool plus a deterministic execution
        // result. The auto-selector LLM call (see returnsMany below) names
        // "web.search", so this is the tool the engine actually invokes.
        coEvery { toolRepository.getAvailableTools() } returns listOf(
            AgentTool("web.search", "Search the web", "{}"),
        )
        coEvery { toolRepository.executeTool("web.search", any()) } returns "search-result"

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
            name = "Phase 15-6 integration",
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
        val cloudPromptText = capturedCloudPrompt.captured.messages.joinToString("\n") { it.content }
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

    // ─── Phase 17.4 — Agent console event emissions ──────────────────────────

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

        // Memory access is reported even when the corpus is empty.
        val memEvent = finalLog.single { it.type == ConsoleEventType.MemoryAccess }
        assertTrue(memEvent.message.contains("0"))

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

    // ─── Phase 20 / 4 — Risk-based HITL gate end-to-end ──────────────────────

    @Test
    fun `given pipeline with READ_ONLY tool node when run then completes without HITL pause`() = runTest {
        every { settingsRepository.pipelineMaxSteps } returns flowOf(15)
        every { settingsRepository.requiresUserConfirmation } returns flowOf(false)

        // READ_ONLY + global override OFF must skip the HITL gate entirely: no
        // WaitingForApproval emission, no notifier call, and the pipeline
        // reaches OUTPUT without intervention.
        coEvery { toolRepository.getRisk("web.search") } returns ToolRisk.READ_ONLY
        coEvery { toolRepository.getAvailableTools() } returns listOf(AgentTool("web.search", "Search", "{}"))
        coEvery { toolRepository.executeTool("web.search", any()) } returns "search-result"

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
}
