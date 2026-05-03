package ai.agent.android.domain.engine

import ai.agent.android.data.engine.KoogClientFactory
import ai.agent.android.domain.engine.executors.*
import ai.agent.android.domain.models.*
import ai.agent.android.domain.prompt.PromptTemplateEngine
import ai.agent.android.domain.prompt.PromptVariableProvider
import ai.agent.android.domain.repositories.*
import ai.agent.android.domain.services.ApprovalNotifier
import ai.agent.android.domain.usecases.EvaluateIfConditionUseCase
import ai.agent.android.domain.usecases.GetContextWindowUseCase
import ai.agent.android.domain.usecases.RetrieveRelevantMemoryUseCase
import ai.agent.android.domain.usecases.LoadModelUseCase
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
    private lateinit var clarificationRepository: ai.agent.android.domain.repositories.ClarificationRepository

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
        evaluateIfConditionUseCase = mockk()
        loadModelUseCase = mockk()
        clarificationRepository = mockk()

        val inputNodeExecutor = InputNodeExecutor()
        val outputNodeExecutor = OutputNodeExecutor(llmEngine, loadModelUseCase, chatRepository)
        val ifConditionNodeExecutor = IfConditionNodeExecutor(evaluateIfConditionUseCase)
        val queueProcessorNodeExecutor = QueueProcessorNodeExecutor()
        
        val toolNodeExecutor = ToolNodeExecutor(
            llmEngine, loadModelUseCase, toolRepository, settingsRepository, approvalNotifier, chatRepository
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

        val summaryNodeExecutor = ai.agent.android.domain.engine.executors.SummaryNodeExecutor(
            llmEngine, loadModelUseCase
        )

        val clarificationNodeExecutor = ClarificationNodeExecutor(
            llmEngine, loadModelUseCase, clarificationRepository
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
        )

        coEvery { getContextWindowUseCase(sessionId) } returns ""
        coEvery { retrieveRelevantMemoryUseCase(any()) } returns emptyList()
        every { settingsRepository.systemPromptPrefix } returns flowOf("")
        every { settingsRepository.toolUsageInstruction } returns flowOf("")
        every { settingsRepository.requiresUserConfirmation } returns flowOf(false)
        every { settingsRepository.pipelineMaxSteps } returns flowOf(15)
        coEvery { toolRepository.getAvailableTools() } returns emptyList()
        
        coEvery { loadModelUseCase(any()) } returns ai.agent.android.domain.models.Result.Success(Unit)
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
            id = "g1", name = "Clarification Graph",
            nodes = listOf(inputNode, clarificationNode, outputNode),
            connections = listOf(
                ConnectionModel("c1", "input_1", "clar_1"),
                ConnectionModel("c2", "clar_1", "output_1"),
            )
        )

        every { llmEngine.generateResponseStream(any()) } returns flowOf(
            "{\"question\":\"Confirm?\",\"options\":[\"yes\",\"no\"]}"
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
            id = "g1", name = "Long Graph",
            nodes = listOf(inputNode, llmNode),
            connections = listOf(ConnectionModel("c1", "input_1", "llm_1"))
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
            id = "g1", name = "Short Graph",
            nodes = listOf(inputNode, outputNode),
            connections = listOf(ConnectionModel("c1", "input_1", "output_1"))
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
            id = "g1", name = "Linear",
            nodes = listOf(inputNode, liteRtNode, outputNode),
            connections = listOf(
                ConnectionModel("c1", "input", "lite_rt"),
                ConnectionModel("c2", "lite_rt", "output"),
            )
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
            id = "g1", name = "Branching",
            nodes = listOf(inputNode, routerNode, liteRtNode, outputNode),
            connections = listOf(
                ConnectionModel("c1", "input", "router"),
                ConnectionModel("c2", "router", "lite_rt", label = "Data"),
                ConnectionModel("c3", "lite_rt", "output"),
            )
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
        assertTrue("INPUT and INTENT_ROUTER should have null totalSteps",
            unknownStages.all { it.stepInfo.totalSteps == null })

        // After routing resolves, LITE_RT and OUTPUT stages must have a concrete total
        val knownStages = stages.drop(2)
        assertTrue("Post-routing stages should have concrete totalSteps",
            knownStages.all { it.stepInfo.totalSteps != null })
        // stepIndex must never exceed totalSteps
        knownStages.forEach { stage ->
            assertTrue("stepIndex ${stage.stepInfo.stepIndex} must not exceed totalSteps ${stage.stepInfo.totalSteps}",
                stage.stepInfo.stepIndex <= stage.stepInfo.totalSteps!!)
        }
    }

    // ─── INTENT_ROUTER tests ─────────────────────────────────────────────────

    @Test
    fun `given INTENT_ROUTER when routing key emitted then downstream node receives original user query not routing key`() = runTest {
        every { settingsRepository.pipelineMaxSteps } returns flowOf(15)

        val inputNode = NodeModel("input", NodeType.INPUT, 0f, 0f)
        val routerNode = NodeModel("router", NodeType.INTENT_ROUTER, 0f, 0f)
        val liteRtNode = NodeModel("lite_rt", NodeType.LITE_RT, 0f, 0f)
        val outputNode = NodeModel("output", NodeType.OUTPUT, 0f, 0f)

        val graph = PipelineGraph(
            id = "g1", name = "Router Fix Test",
            nodes = listOf(inputNode, routerNode, liteRtNode, outputNode),
            connections = listOf(
                ConnectionModel("c1", "input", "router"),
                ConnectionModel("c2", "router", "lite_rt", label = "Data"),
                ConnectionModel("c3", "lite_rt", "output"),
            )
        )

        every { llmEngine.generateResponseStream(any()) } returnsMany listOf(
            flowOf("Data"),           // INTENT_ROUTER routing decision
            flowOf("Correct answer"), // LITE_RT processes original prompt
            flowOf("Final"),          // OUTPUT formats the response
        )

        val states = engine(sessionId, "fuel consumption query", graph).toList()

        assertTrue("Expected Completed but got: ${states.last()}", states.last() is AgentOrchestratorState.Completed)

        // LITE_RT must receive the original user query, not the routing key
        io.mockk.verify {
            llmEngine.generateResponseStream(match { it.contains("USER/INPUT: fuel consumption query") })
        }
        // No downstream node must receive the routing key as its user input
        io.mockk.verify(exactly = 0) {
            llmEngine.generateResponseStream(match { it.contains("USER/INPUT: Data") })
        }
    }

    // ─── QUEUE_PROCESSOR tests ────────────────────────────────────────────────

    @Test
    fun `given QUEUE_PROCESSOR when LLM returns JSON list then each item is processed and pipeline completes`() = runTest {
        every { settingsRepository.pipelineMaxSteps } returns flowOf(20)

        val inputNode = NodeModel("input", NodeType.INPUT, 0f, 0f)
        val listGenNode = NodeModel("list_gen", NodeType.LITE_RT, 0f, 0f)
        val queueNode = NodeModel("queue", NodeType.QUEUE_PROCESSOR, 0f, 0f)
        val itemProcNode = NodeModel("item_proc", NodeType.LITE_RT, 0f, 0f)
        val outputNode = NodeModel("output", NodeType.OUTPUT, 0f, 0f)

        // item_proc has no outgoing connection — engine treats it as end-of-subtask
        val graph = PipelineGraph(
            id = "g1", name = "Queue Test",
            nodes = listOf(inputNode, listGenNode, queueNode, itemProcNode, outputNode),
            connections = listOf(
                ConnectionModel("c1", "input", "list_gen"),
                ConnectionModel("c2", "list_gen", "queue"),
                ConnectionModel("c3", "queue", "item_proc", label = "Item"),
                ConnectionModel("c4", "queue", "output", label = "Done"),
            )
        )

        every { llmEngine.generateResponseStream(any()) } returnsMany listOf(
            flowOf("""["subtask_one", "subtask_two"]"""),
            flowOf("result_one"),
            flowOf("result_two"),
        )

        val states = engine(sessionId, "Process the list", graph).toList()

        assertTrue("Expected Completed but got: ${states.last()}", states.last() is AgentOrchestratorState.Completed)
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
            id = "g2", name = "Queue MaxSteps",
            nodes = listOf(inputNode, listGenNode, queueNode, itemProcNode, outputNode),
            connections = listOf(
                ConnectionModel("c1", "input", "list_gen"),
                ConnectionModel("c2", "list_gen", "queue"),
                ConnectionModel("c3", "queue", "item_proc", label = "Item"),
                ConnectionModel("c4", "queue", "output", label = "Done"),
            )
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
    fun `given pipeline executes LITE_RT node then metricsRepository records node execution with tokenCount`() = runTest {
        every { settingsRepository.pipelineMaxSteps } returns flowOf(15)

        val inputNode = NodeModel("input", NodeType.INPUT, 0f, 0f)
        val llmNode = NodeModel("llm", NodeType.LITE_RT, 0f, 0f)
        val outputNode = NodeModel("output", NodeType.OUTPUT, 0f, 0f)

        val graph = PipelineGraph(
            id = "g1", name = "Metrics Test",
            nodes = listOf(inputNode, llmNode, outputNode),
            connections = listOf(
                ConnectionModel("c1", "input", "llm"),
                ConnectionModel("c2", "llm", "output"),
            )
        )

        // LITE_RT should emit 3 tokens, OUTPUT executor also calls generateResponseStream.
        every { llmEngine.generateResponseStream(any()) } returnsMany listOf(
            flowOf("one ", "two ", "three"),
            flowOf("final"),
        )

        engine(sessionId, "prompt", graph).toList()

        // INPUT, LITE_RT, OUTPUT — three nodes, three recordings
        verify(exactly = 1) { metricsRepository.recordNodeExecution("INPUT", any(), any()) }
        verify(exactly = 1) { metricsRepository.recordNodeExecution("LITE_RT", any(), match { it != null && it > 0 }) }
        verify(exactly = 1) { metricsRepository.recordNodeExecution("OUTPUT", any(), any()) }
    }

    @Test
    fun `given pipeline executes node then trace step includes durationMs and tokenCount`() = runTest {
        every { settingsRepository.pipelineMaxSteps } returns flowOf(15)

        val inputNode = NodeModel("input", NodeType.INPUT, 0f, 0f)
        val llmNode = NodeModel("llm", NodeType.LITE_RT, 0f, 0f)
        val outputNode = NodeModel("output", NodeType.OUTPUT, 0f, 0f)

        val graph = PipelineGraph(
            id = "g1", name = "Trace Timing",
            nodes = listOf(inputNode, llmNode, outputNode),
            connections = listOf(
                ConnectionModel("c1", "input", "llm"),
                ConnectionModel("c2", "llm", "output"),
            )
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
            id = "g3", name = "Queue Error",
            nodes = listOf(inputNode, listGenNode, queueNode, itemProcNode, outputNode),
            connections = listOf(
                ConnectionModel("c1", "input", "list_gen"),
                ConnectionModel("c2", "list_gen", "queue"),
                ConnectionModel("c3", "queue", "item_proc", label = "Item"),
                ConnectionModel("c4", "queue", "output", label = "Done"),
            )
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
            ToolNodeExecutor(llmEngine, loadModelUseCase, toolRepository, settingsRepository, approvalNotifier, chatRepository),
            LiteRtNodeExecutor(
                llmEngine, toolRepository, chatRepository, getContextWindowUseCase,
                retrieveRelevantMemoryUseCase, settingsRepository, metricsRepository, loadModelUseCase,
            ),
            CloudLlmNodeExecutor(
                toolRepository, chatRepository, getContextWindowUseCase,
                retrieveRelevantMemoryUseCase, settingsRepository, apiKeyRepository,
                metricsRepository, koogClientFactory,
            ),
            SystemNodeExecutor(llmEngine, loadModelUseCase, chatRepository),
            QueueProcessorNodeExecutor(),
            ai.agent.android.domain.engine.executors.SummaryNodeExecutor(llmEngine, loadModelUseCase),
            ClarificationNodeExecutor(llmEngine, loadModelUseCase, clarificationRepository),
        )
        val engineWithProvider = GraphExecutionEngine(
            realFactory,
            ToolNodeExecutor(llmEngine, loadModelUseCase, toolRepository, settingsRepository, approvalNotifier, chatRepository),
            chatRepository,
            settingsRepository,
            metricsRepository,
            PromptTemplateEngine(),
            setOf(dateProvider),
        )

        val inputNode = NodeModel("input", NodeType.INPUT, 0f, 0f)
        val llmNode = NodeModel("llm", NodeType.LITE_RT, 0f, 0f, systemPrompt = "Today is \$DATE.")
        val outputNode = NodeModel("output", NodeType.OUTPUT, 0f, 0f)

        val graph = PipelineGraph(
            id = "g1", name = "Render Test",
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
}