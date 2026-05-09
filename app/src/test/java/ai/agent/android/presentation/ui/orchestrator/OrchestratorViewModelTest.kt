package ai.agent.android.presentation.ui.orchestrator

import ai.agent.android.domain.models.NodeContextConfig
import ai.agent.android.domain.models.NodeType
import ai.agent.android.domain.models.PipelineGraph
import ai.agent.android.domain.models.AgentTool
import ai.agent.android.domain.prompt.PromptSegment
import ai.agent.android.domain.prompt.PromptTemplateEngine
import ai.agent.android.domain.prompt.PromptVariableProvider
import ai.agent.android.domain.repositories.ApiKeyRepository
import ai.agent.android.domain.repositories.ToolRepository
import ai.agent.android.domain.usecases.CreatePipelineUseCase
import ai.agent.android.domain.usecases.DeletePipelineUseCase
import ai.agent.android.domain.usecases.DuplicatePipelineUseCase
import ai.agent.android.domain.usecases.LoadPipelineUseCase
import ai.agent.android.domain.usecases.RenamePipelineUseCase
import ai.agent.android.domain.usecases.SavePipelineUseCase
import ai.agent.android.domain.usecases.GetPromptTemplatesUseCase
import ai.agent.android.domain.usecases.SavePromptTemplateUseCase
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class OrchestratorViewModelTest {

    private lateinit var savePipelineUseCase: SavePipelineUseCase
    private lateinit var loadPipelineUseCase: LoadPipelineUseCase
    private lateinit var importPipelineUseCase: ai.agent.android.domain.usecases.ImportPipelineUseCase
    private lateinit var renamePipelineUseCase: RenamePipelineUseCase
    private lateinit var duplicatePipelineUseCase: DuplicatePipelineUseCase
    private lateinit var deletePipelineUseCase: DeletePipelineUseCase
    private lateinit var createPipelineUseCase: CreatePipelineUseCase
    private lateinit var getPromptTemplatesUseCase: GetPromptTemplatesUseCase
    private lateinit var savePromptTemplateUseCase: SavePromptTemplateUseCase
    private lateinit var apiKeyRepository: ApiKeyRepository
    private lateinit var toolRepository: ToolRepository
    private lateinit var promptTemplateEngine: PromptTemplateEngine
    private lateinit var providerDate: PromptVariableProvider
    private lateinit var providerTime: PromptVariableProvider
    private lateinit var viewModel: OrchestratorViewModel

    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        savePipelineUseCase = mockk()
        // Default to a successful save so the import-then-save pipeline used
        // by `importPipelineFromJson` round-trips without extra wiring.
        coEvery { savePipelineUseCase(any()) } returns Result.success(Unit)
        loadPipelineUseCase = mockk()
        // Use the real ImportPipelineUseCase against the mocked save: the
        // import path here is exercised end-to-end (parse + persist).
        importPipelineUseCase = ai.agent.android.domain.usecases.ImportPipelineUseCase(savePipelineUseCase)
        renamePipelineUseCase = mockk()
        duplicatePipelineUseCase = mockk()
        deletePipelineUseCase = mockk()
        createPipelineUseCase = mockk()
        getPromptTemplatesUseCase = mockk()
        savePromptTemplateUseCase = mockk()
        apiKeyRepository = mockk()
        toolRepository = mockk()
        
        every { loadPipelineUseCase.observeAllPipelines() } returns flowOf(emptyList())
        every { getPromptTemplatesUseCase() } returns flowOf(emptyList())
        
        every { apiKeyRepository.getOpenAIKey() } returns flowOf(null)
        every { apiKeyRepository.getAnthropicKey() } returns flowOf("key")
        every { apiKeyRepository.getGoogleKey() } returns flowOf(null)
        every { apiKeyRepository.getDeepSeekKey() } returns flowOf(null)

        coEvery { toolRepository.getAvailableTools() } returns listOf(AgentTool("Tool1", "Desc", "{}"))

        promptTemplateEngine = mockk()
        providerDate = mockk {
            every { key() } returns "DATE"
            coEvery { resolve() } returns "01 May 2026"
        }
        providerTime = mockk {
            every { key() } returns "TIME"
            coEvery { resolve() } returns "15:30"
        }

        viewModel = OrchestratorViewModel(
            savePipelineUseCase,
            loadPipelineUseCase,
            importPipelineUseCase,
            renamePipelineUseCase,
            duplicatePipelineUseCase,
            deletePipelineUseCase,
            createPipelineUseCase,
            getPromptTemplatesUseCase,
            savePromptTemplateUseCase,
            apiKeyRepository,
            toolRepository,
            promptTemplateEngine,
            setOf(providerDate, providerTime),
        )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `init loads provider keys and tools`() = runTest {
        testDispatcher.scheduler.advanceUntilIdle()
        
        val state = viewModel.uiState.value
        assertEquals(true, state.providerKeys["anthropic"])
        assertEquals(false, state.providerKeys["openai"])
        assertEquals(1, state.availableTools.size)
        assertEquals("Tool1", state.availableTools[0].name)
    }

    @Test
    fun `addNode adds a new node to currentPipeline`() {
        // Act
        viewModel.addNode(NodeType.TOOL, 100f, 200f)

        // Assert
        val nodes = viewModel.uiState.value.currentPipeline.nodes
        assertEquals(1, nodes.size)
        assertEquals(NodeType.TOOL, nodes[0].type)
        assertEquals(100f, nodes[0].x)
        assertEquals(200f, nodes[0].y)
    }

    @Test
    fun `moveNode updates coordinates of existing node by delta`() {
        // Arrange
        viewModel.addNode(NodeType.TOOL, 100f, 200f)
        val nodeId = viewModel.uiState.value.currentPipeline.nodes[0].id

        // Act
        viewModel.moveNode(nodeId, 50f, -20f)

        // Assert
        val nodes = viewModel.uiState.value.currentPipeline.nodes
        assertEquals(1, nodes.size)
        assertEquals(150f, nodes[0].x)
        assertEquals(180f, nodes[0].y)
    }

    @Test
    fun `addConnection creates connection if DAG is valid and returns its ID`() {
        // Arrange
        viewModel.addNode(NodeType.INPUT, 0f, 0f)
        viewModel.addNode(NodeType.OUTPUT, 100f, 100f)
        val nodes = viewModel.uiState.value.currentPipeline.nodes
        val n1 = nodes[0].id
        val n2 = nodes[1].id

        // Act
        val connectionId = viewModel.addConnection(n1, n2)

        // Assert
        assertNotNull(connectionId)
        val connections = viewModel.uiState.value.currentPipeline.connections
        assertEquals(1, connections.size)
        assertEquals(connectionId, connections[0].id)
        assertEquals(n1, connections[0].sourceNodeId)
        assertEquals(n2, connections[0].targetNodeId)
        assertEquals(null, viewModel.uiState.value.errorMessage)
    }

    @Test
    fun `addConnection sets error if DAG becomes invalid (cycle) and returns null`() {
        // Arrange
        viewModel.addNode(NodeType.INPUT, 0f, 0f)
        viewModel.addNode(NodeType.OUTPUT, 100f, 100f)
        val nodes = viewModel.uiState.value.currentPipeline.nodes
        val n1 = nodes[0].id
        val n2 = nodes[1].id

        viewModel.addConnection(n1, n2)

        // Act
        val connectionId = viewModel.addConnection(n2, n1) // This should create a cycle

        // Assert
        assertEquals(null, connectionId)
        val connections = viewModel.uiState.value.currentPipeline.connections
        assertEquals(1, connections.size) // The cyclic connection should not be added
        assertTrue(viewModel.uiState.value.errorMessage?.contains("Cycle detected") == true)
    }

    @Test
    fun `updateConnectionLabel updates the label of a connection`() {
        // Arrange
        viewModel.addNode(NodeType.INPUT, 0f, 0f)
        viewModel.addNode(NodeType.OUTPUT, 100f, 100f)
        val nodes = viewModel.uiState.value.currentPipeline.nodes
        val n1 = nodes[0].id
        val n2 = nodes[1].id
        val connectionId = viewModel.addConnection(n1, n2)

        // Act
        viewModel.updateConnectionLabel(connectionId!!, "NewLabel")

        // Assert
        val connections = viewModel.uiState.value.currentPipeline.connections
        assertEquals(1, connections.size)
        assertEquals("NewLabel", connections[0].label)
    }

    @Test
    fun `removeConnection removes connection by ID`() {
        // Arrange
        viewModel.addNode(NodeType.INPUT, 0f, 0f)
        viewModel.addNode(NodeType.OUTPUT, 100f, 100f)
        val nodes = viewModel.uiState.value.currentPipeline.nodes
        val n1 = nodes[0].id
        val n2 = nodes[1].id
        val connectionId = viewModel.addConnection(n1, n2)

        // Act
        viewModel.removeConnection(connectionId!!)

        // Assert
        val connections = viewModel.uiState.value.currentPipeline.connections
        assertEquals(0, connections.size)
    }

    @Test
    fun `removeNode removes node and associated connections`() {
        // Arrange
        viewModel.addNode(NodeType.INPUT, 0f, 0f)
        viewModel.addNode(NodeType.OUTPUT, 100f, 100f)
        val nodes = viewModel.uiState.value.currentPipeline.nodes
        val n1 = nodes[0].id
        val n2 = nodes[1].id
        viewModel.addConnection(n1, n2)

        // Act
        viewModel.removeNode(n1)

        // Assert
        val state = viewModel.uiState.value
        assertEquals(1, state.currentPipeline.nodes.size)
        assertEquals(0, state.currentPipeline.connections.size)
    }

    @Test
    fun `clearPipeline empties nodes and connections`() {
        viewModel.addNode(NodeType.INPUT, 0f, 0f)
        viewModel.clearPipeline()
        
        val state = viewModel.uiState.value
        assertEquals(0, state.currentPipeline.nodes.size)
        assertEquals(0, state.currentPipeline.connections.size)
    }

    @Test
    fun `saveCurrentPipeline calls use case and updates state`() = runTest {
        coEvery { savePipelineUseCase(any()) } returns Result.success(Unit)
        
        viewModel.saveCurrentPipeline()
        testDispatcher.scheduler.advanceUntilIdle()
        
        coEvery { savePipelineUseCase(any()) }
        assertEquals(false, viewModel.uiState.value.isLoading)
        assertEquals(null, viewModel.uiState.value.errorMessage)
    }

    @Test
    fun `saveCurrentPipeline handles validation errors and updates errorMessage`() = runTest {
        val errors = listOf(
            ai.agent.android.domain.models.PipelineValidationError.MissingInput,
            ai.agent.android.domain.models.PipelineValidationError.MissingOutput
        )
        val exception = ai.agent.android.domain.models.PipelineValidationException(errors)
        coEvery { savePipelineUseCase(any()) } returns Result.failure(exception)
        
        viewModel.saveCurrentPipeline()
        testDispatcher.scheduler.advanceUntilIdle()
        
        val errorMessage = viewModel.uiState.value.errorMessage
        assertEquals(false, viewModel.uiState.value.isLoading)
        assertTrue(errorMessage?.contains("Missing INPUT node") == true)
        assertTrue(errorMessage?.contains("Missing OUTPUT node") == true)
    }

    @Test
    fun `loadPipeline loads from use case and updates current pipeline`() = runTest {
        val mockPipeline = PipelineGraph(id = "test-1", name = "Test Pipeline")
        coEvery { loadPipelineUseCase.getPipelineById("test-1") } returns mockPipeline
        
        viewModel.loadPipeline("test-1")
        testDispatcher.scheduler.advanceUntilIdle()
        
        assertEquals("test-1", viewModel.uiState.value.currentPipeline.id)
        assertEquals(false, viewModel.uiState.value.isLoading)
        assertEquals(null, viewModel.uiState.value.errorMessage)
    }

    @Test
    fun `clearError sets error message to null`() {
        viewModel.addNode(NodeType.INPUT, 0f, 0f)
        viewModel.addNode(NodeType.OUTPUT, 100f, 100f)
        val nodes = viewModel.uiState.value.currentPipeline.nodes
        viewModel.addConnection(nodes[0].id, nodes[1].id) // valid
        viewModel.addConnection(nodes[1].id, nodes[0].id) // invalid cycle
        assertTrue(viewModel.uiState.value.errorMessage != null)
        
        viewModel.clearError()
        assertEquals(null, viewModel.uiState.value.errorMessage)
    }

    @Test
    fun `updateNodeTool updates the tool assigned to a specific node`() {
        viewModel.addNode(NodeType.TOOL, 0f, 0f)
        val nodeId = viewModel.uiState.value.currentPipeline.nodes.first().id
        
        viewModel.updateNodeTool(nodeId, "CalendarTool")
        
        val updatedNode = viewModel.uiState.value.currentPipeline.nodes.first()
        assertEquals("CalendarTool", updatedNode.toolName)
        assertEquals("CalendarTool", updatedNode.label)
    }

    @Test
    fun `applyBasePreset creates complex routing pipeline with nodes and connections`() {
        viewModel.applyBasePreset()
        
        val state = viewModel.uiState.value
        val nodes = state.currentPipeline.nodes
        val connections = state.currentPipeline.connections
        
        assertEquals(10, nodes.size)
        assertEquals(NodeType.INPUT, nodes.find { it.type == NodeType.INPUT }?.type)
        assertEquals(NodeType.INTENT_ROUTER, nodes.find { it.type == NodeType.INTENT_ROUTER }?.type)
        assertEquals(NodeType.DECOMPOSITION, nodes.find { it.type == NodeType.DECOMPOSITION }?.type)
        assertEquals(NodeType.OUTPUT, nodes.find { it.type == NodeType.OUTPUT }?.type)
        
        assertEquals(12, connections.size)
        assertEquals("Base Preset", state.currentPipeline.name)
    }

    @Test
    fun `exportPipelineToJson returns valid json string`() {
        viewModel.applyBasePreset()
        
        val json = viewModel.exportPipelineToJson()
        
        assertTrue(json.contains("Base Preset"))
        assertTrue(json.contains("INPUT"))
        assertTrue(json.contains("LITE_RT"))
        assertTrue(json.contains("OUTPUT"))
    }

    @Test
    fun `importPipelineFromJson updates current pipeline from json`() = runTest {
        viewModel.applyBasePreset()
        val json = viewModel.exportPipelineToJson()

        viewModel.clearPipeline()
        assertEquals(0, viewModel.uiState.value.currentPipeline.nodes.size)

        viewModel.importPipelineFromJson(json)
        testDispatcher.scheduler.advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals("Base Preset", state.currentPipeline.name)
        assertEquals(10, state.currentPipeline.nodes.size)
        assertEquals(12, state.currentPipeline.connections.size)
        assertEquals(null, state.errorMessage)
    }

    @Test
    fun `importPipelineFromJson sets error on invalid json`() = runTest {
        viewModel.importPipelineFromJson("{ invalid json }")
        testDispatcher.scheduler.advanceUntilIdle()

        assertNotNull(viewModel.uiState.value.errorMessage)
        assertTrue(viewModel.uiState.value.errorMessage!!.contains("Invalid JSON"))
    }

    @Test
    fun `availableVariables are derived from injected providers and sorted`() {
        val state = viewModel.uiState.value

        assertEquals(listOf("\$DATE", "\$TIME"), state.availableVariables)
    }

    @Test
    fun `requestPromptPreview transitions Hidden to Ready with engine segments`() = runTest {
        val template = "Hello \$DATE"
        val segments = listOf(
            PromptSegment.Literal("Hello "),
            PromptSegment.Resolved("DATE", "01 May 2026"),
        )
        coEvery { promptTemplateEngine.renderSegments(template, any()) } returns segments

        viewModel.requestPromptPreview(template)
        testDispatcher.scheduler.advanceUntilIdle()

        val readyState = viewModel.uiState.value.previewState
        assertTrue(readyState is PromptPreviewState.Ready)
        assertEquals(segments, (readyState as PromptPreviewState.Ready).segments)
        coVerify { promptTemplateEngine.renderSegments(template, any()) }
    }

    @Test
    fun `addNode supports CLARIFICATION type with default fields`() {
        viewModel.addNode(NodeType.CLARIFICATION, 50f, 80f)

        val nodes = viewModel.uiState.value.currentPipeline.nodes
        assertEquals(1, nodes.size)
        val node = nodes[0]
        assertEquals(NodeType.CLARIFICATION, node.type)
        assertEquals(50f, node.x)
        assertEquals(80f, node.y)
        assertEquals(null, node.clarificationTimeoutMs)
        // The default system prompt for CLARIFICATION is the JSON-format instruction.
        assertNotNull(node.systemPrompt)
    }

    @Test
    fun `addNode applies recommended contextConfig for LITE_RT`() {
        viewModel.addNode(NodeType.LITE_RT, 0f, 0f)

        val node = viewModel.uiState.value.currentPipeline.nodes.single()
        assertEquals(NodeContextConfig.defaultForType(NodeType.LITE_RT), node.contextConfig)
        // Sanity: the LITE_RT preset trims chat history / memory / tool results.
        assertEquals(false, node.contextConfig.chatHistory)
        assertEquals(true, node.contextConfig.originalTask)
        assertEquals(true, node.contextConfig.nodeInput)
        assertEquals(false, node.contextConfig.longTermMemory)
        assertEquals(false, node.contextConfig.toolResults)
    }

    @Test
    fun `addNode applies recommended contextConfig for CLOUD`() {
        viewModel.addNode(NodeType.CLOUD, 0f, 0f)

        val node = viewModel.uiState.value.currentPipeline.nodes.single()
        assertEquals(NodeContextConfig.defaultForType(NodeType.CLOUD), node.contextConfig)
        assertEquals(true, node.contextConfig.chatHistory)
        assertEquals(true, node.contextConfig.originalTask)
        assertEquals(true, node.contextConfig.nodeInput)
        assertEquals(false, node.contextConfig.longTermMemory)
        assertEquals(false, node.contextConfig.toolResults)
    }

    @Test
    fun `addNode applies recommended contextConfig for TOOL`() {
        viewModel.addNode(NodeType.TOOL, 0f, 0f)

        val node = viewModel.uiState.value.currentPipeline.nodes.single()
        assertEquals(NodeContextConfig.defaultForType(NodeType.TOOL), node.contextConfig)
        assertEquals(false, node.contextConfig.chatHistory)
        assertEquals(false, node.contextConfig.originalTask)
        assertEquals(true, node.contextConfig.nodeInput)
        assertEquals(false, node.contextConfig.longTermMemory)
        assertEquals(false, node.contextConfig.toolResults)
    }

    @Test
    fun `addNode applies recommended contextConfig for OUTPUT (all enabled)`() {
        viewModel.addNode(NodeType.OUTPUT, 0f, 0f)

        val node = viewModel.uiState.value.currentPipeline.nodes.single()
        assertEquals(NodeContextConfig.ALL_ENABLED, node.contextConfig)
    }

    @Test
    fun `addNode applies recommended contextConfig for CLARIFICATION`() {
        viewModel.addNode(NodeType.CLARIFICATION, 0f, 0f)

        val node = viewModel.uiState.value.currentPipeline.nodes.single()
        assertEquals(NodeContextConfig.defaultForType(NodeType.CLARIFICATION), node.contextConfig)
        assertEquals(false, node.contextConfig.chatHistory)
        assertEquals(true, node.contextConfig.originalTask)
        assertEquals(true, node.contextConfig.nodeInput)
    }

    @Test
    fun `addNode applies recommended contextConfig for QUEUE_PROCESSOR`() {
        viewModel.addNode(NodeType.QUEUE_PROCESSOR, 0f, 0f)

        val node = viewModel.uiState.value.currentPipeline.nodes.single()
        assertEquals(NodeContextConfig.defaultForType(NodeType.QUEUE_PROCESSOR), node.contextConfig)
        assertEquals(true, node.contextConfig.originalTask)
        assertEquals(true, node.contextConfig.nodeInput)
        assertEquals(false, node.contextConfig.chatHistory)
    }

    @Test
    fun `updateNodeClarificationTimeout updates only the target node`() {
        viewModel.addNode(NodeType.CLARIFICATION, 0f, 0f)
        val nodeId = viewModel.uiState.value.currentPipeline.nodes[0].id

        viewModel.updateNodeClarificationTimeout(nodeId, 30_000L)

        val updated = viewModel.uiState.value.currentPipeline.nodes
            .single { it.id == nodeId }
        assertEquals(30_000L, updated.clarificationTimeoutMs)
    }

    @Test
    fun `updateNodeClarificationTimeout clears timeout when null is passed`() {
        viewModel.addNode(NodeType.CLARIFICATION, 0f, 0f)
        val nodeId = viewModel.uiState.value.currentPipeline.nodes[0].id
        viewModel.updateNodeClarificationTimeout(nodeId, 30_000L)

        viewModel.updateNodeClarificationTimeout(nodeId, null)

        val updated = viewModel.uiState.value.currentPipeline.nodes
            .single { it.id == nodeId }
        assertEquals(null, updated.clarificationTimeoutMs)
    }

    @Test
    fun `updateNodeContextConfig updates target node and forces nodeInput true`() {
        viewModel.addNode(NodeType.TOOL, 0f, 0f)
        val nodeId = viewModel.uiState.value.currentPipeline.nodes[0].id

        val requested = NodeContextConfig(
            chatHistory = true,
            originalTask = false,
            nodeInput = false,
            longTermMemory = true,
            toolResults = false,
        )
        viewModel.updateNodeContextConfig(nodeId, requested)

        val updated = viewModel.uiState.value.currentPipeline.nodes.single { it.id == nodeId }
        assertEquals(true, updated.contextConfig.nodeInput)
        assertEquals(true, updated.contextConfig.chatHistory)
        assertEquals(false, updated.contextConfig.originalTask)
        assertEquals(true, updated.contextConfig.longTermMemory)
        assertEquals(false, updated.contextConfig.toolResults)
        assertEquals(null, viewModel.uiState.value.errorMessage)
    }

    @Test
    fun `updateNodeContextConfig with all flags disabled sets snackbar message and forces nodeInput true`() {
        viewModel.addNode(NodeType.TOOL, 0f, 0f)
        val nodeId = viewModel.uiState.value.currentPipeline.nodes[0].id

        val empty = NodeContextConfig(
            chatHistory = false,
            originalTask = false,
            nodeInput = false,
            longTermMemory = false,
            toolResults = false,
        )
        viewModel.updateNodeContextConfig(nodeId, empty)

        val updated = viewModel.uiState.value.currentPipeline.nodes.single { it.id == nodeId }
        assertEquals(true, updated.contextConfig.nodeInput)
        assertEquals(
            "At least one data source must remain enabled",
            viewModel.uiState.value.errorMessage,
        )
    }

    @Test
    fun `updateNodeContextConfig leaves other nodes unchanged`() {
        viewModel.addNode(NodeType.TOOL, 0f, 0f)
        viewModel.addNode(NodeType.LITE_RT, 100f, 100f)
        val nodes = viewModel.uiState.value.currentPipeline.nodes
        val targetId = nodes.first { it.type == NodeType.TOOL }.id
        val otherId = nodes.first { it.type == NodeType.LITE_RT }.id

        viewModel.updateNodeContextConfig(
            targetId,
            NodeContextConfig(
                chatHistory = false,
                originalTask = false,
                nodeInput = true,
                longTermMemory = false,
                toolResults = false,
            ),
        )

        val updated = viewModel.uiState.value.currentPipeline.nodes
        val target = updated.single { it.id == targetId }
        val other = updated.single { it.id == otherId }
        assertEquals(false, target.contextConfig.chatHistory)
        assertEquals(false, target.contextConfig.originalTask)
        assertEquals(false, target.contextConfig.longTermMemory)
        assertEquals(false, target.contextConfig.toolResults)
        // The untouched node still carries the recommended default for its
        // type (LITE_RT — `nodeInput` + `originalTask` only).
        assertEquals(NodeContextConfig.defaultForType(NodeType.LITE_RT), other.contextConfig)
    }

    @Test
    fun `updateNodeContextConfig with valid config clears error message`() {
        viewModel.addNode(NodeType.TOOL, 0f, 0f)
        val nodeId = viewModel.uiState.value.currentPipeline.nodes[0].id
        viewModel.updateNodeContextConfig(
            nodeId,
            NodeContextConfig(false, false, false, false, false),
        )
        assertNotNull(viewModel.uiState.value.errorMessage)

        viewModel.updateNodeContextConfig(
            nodeId,
            NodeContextConfig(
                chatHistory = false,
                originalTask = true,
                nodeInput = true,
                longTermMemory = false,
                toolResults = false,
            ),
        )

        assertEquals(null, viewModel.uiState.value.errorMessage)
    }

    @Test
    fun `saveCurrentPipeline maps NodeEmptyContext to localized message with node label`() = runTest {
        viewModel.addNode(NodeType.TOOL, 0f, 0f)
        val node = viewModel.uiState.value.currentPipeline.nodes[0]

        val errors = listOf(
            ai.agent.android.domain.models.PipelineValidationError.NodeEmptyContext(node.id),
        )
        val exception = ai.agent.android.domain.models.PipelineValidationException(errors)
        coEvery { savePipelineUseCase(any()) } returns Result.failure(exception)

        viewModel.saveCurrentPipeline()
        testDispatcher.scheduler.advanceUntilIdle()

        val msg = viewModel.uiState.value.errorMessage
        assertNotNull(msg)
        assertTrue(msg!!.contains("Node \"${node.label}\""))
        assertTrue(msg.contains("enable at least one source"))
    }

    @Test
    fun `dismissPromptPreview resets state to Hidden`() = runTest {
        coEvery { promptTemplateEngine.renderSegments(any(), any()) } returns emptyList()
        viewModel.requestPromptPreview("anything")
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.dismissPromptPreview()

        assertEquals(PromptPreviewState.Hidden, viewModel.uiState.value.previewState)
    }

    @Test
    fun `renamePipeline patches currentPipeline name and emits feedback when active is renamed`() = runTest {
        // Arrange — load the pipeline so it becomes the active one.
        val active = PipelineGraph(id = "active", name = "Old Name")
        coEvery { loadPipelineUseCase.getPipelineById("active") } returns active
        viewModel.loadPipeline("active")
        testDispatcher.scheduler.advanceUntilIdle()
        coEvery { renamePipelineUseCase("active", "  New Name  ") } returns Result.success(Unit)

        // Act
        viewModel.renamePipeline("active", "  New Name  ")
        testDispatcher.scheduler.advanceUntilIdle()

        // Assert
        val state = viewModel.uiState.value
        assertEquals("New Name", state.currentPipeline.name)
        assertEquals(false, state.isLoading)
        assertEquals(null, state.errorMessage)
        assertEquals("Pipeline renamed", state.feedbackMessage)
        coVerify { renamePipelineUseCase("active", "  New Name  ") }
    }

    @Test
    fun `renamePipeline surfaces error and leaves current pipeline unchanged on failure`() = runTest {
        coEvery {
            renamePipelineUseCase("p1", "")
        } returns Result.failure(IllegalArgumentException("Pipeline name cannot be empty"))

        viewModel.renamePipeline("p1", "")
        testDispatcher.scheduler.advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals("Pipeline name cannot be empty", state.errorMessage)
        assertEquals(null, state.feedbackMessage)
    }

    @Test
    fun `duplicatePipeline loads the duplicate as currentPipeline on success`() = runTest {
        val duplicate = PipelineGraph(id = "dup", name = "Source (copy)")
        coEvery { duplicatePipelineUseCase("src") } returns Result.success(duplicate)

        viewModel.duplicatePipeline("src")
        testDispatcher.scheduler.advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals("dup", state.currentPipeline.id)
        assertEquals("Source (copy)", state.currentPipeline.name)
        assertEquals("Pipeline duplicated", state.feedbackMessage)
        assertEquals(null, state.errorMessage)
    }

    @Test
    fun `duplicatePipeline surfaces error on failure`() = runTest {
        val previousPipelineId = viewModel.uiState.value.currentPipeline.id
        coEvery {
            duplicatePipelineUseCase("missing")
        } returns Result.failure(IllegalStateException("Pipeline not found"))

        viewModel.duplicatePipeline("missing")
        testDispatcher.scheduler.advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(previousPipelineId, state.currentPipeline.id)
        assertEquals("Pipeline not found", state.errorMessage)
    }

    @Test
    fun `deletePipeline forwards active id and surfaces blocked-when-active error`() = runTest {
        val active = PipelineGraph(id = "active", name = "Active")
        coEvery { loadPipelineUseCase.getPipelineById("active") } returns active
        viewModel.loadPipeline("active")
        testDispatcher.scheduler.advanceUntilIdle()
        coEvery {
            deletePipelineUseCase("active", "active")
        } returns Result.failure(IllegalStateException("Active pipeline cannot be deleted"))

        viewModel.deletePipeline("active")
        testDispatcher.scheduler.advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals("Active pipeline cannot be deleted", state.errorMessage)
        assertEquals(null, state.feedbackMessage)
        coVerify { deletePipelineUseCase("active", "active") }
    }

    @Test
    fun `deletePipeline emits feedback on successful deletion`() = runTest {
        coEvery { deletePipelineUseCase("p2", any()) } returns Result.success(Unit)

        viewModel.deletePipeline("p2")
        testDispatcher.scheduler.advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals("Pipeline deleted", state.feedbackMessage)
        assertEquals(null, state.errorMessage)
    }

    @Test
    fun `createNewPipeline loads the seed graph as currentPipeline`() = runTest {
        val seed = PipelineGraph(
            id = "new",
            name = "Brand New",
            nodes = listOf(
                ai.agent.android.domain.models.NodeModel("i", NodeType.INPUT, 0f, 0f),
                ai.agent.android.domain.models.NodeModel("o", NodeType.OUTPUT, 100f, 0f),
            ),
            connections = listOf(
                ai.agent.android.domain.models.ConnectionModel("c", "i", "o"),
            ),
        )
        coEvery { createPipelineUseCase("Brand New") } returns Result.success(seed)

        viewModel.createNewPipeline("Brand New")
        testDispatcher.scheduler.advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals("new", state.currentPipeline.id)
        assertEquals(2, state.currentPipeline.nodes.size)
        assertEquals("Pipeline created", state.feedbackMessage)
    }

    @Test
    fun `createNewPipeline surfaces validation error from use case`() = runTest {
        coEvery {
            createPipelineUseCase("")
        } returns Result.failure(IllegalArgumentException("Pipeline name cannot be empty"))

        viewModel.createNewPipeline("")
        testDispatcher.scheduler.advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals("Pipeline name cannot be empty", state.errorMessage)
        assertEquals(null, state.feedbackMessage)
    }

    @Test
    fun `clearFeedback resets feedbackMessage to null`() = runTest {
        coEvery { deletePipelineUseCase("p2", any()) } returns Result.success(Unit)
        viewModel.deletePipeline("p2")
        testDispatcher.scheduler.advanceUntilIdle()
        assertEquals("Pipeline deleted", viewModel.uiState.value.feedbackMessage)

        viewModel.clearFeedback()

        assertEquals(null, viewModel.uiState.value.feedbackMessage)
    }

    @Test
    fun `activePipelineId returns currentPipeline id when pipeline has nodes`() = runTest {
        viewModel.addNode(NodeType.INPUT, 0f, 0f)

        val state = viewModel.uiState.value
        assertEquals(state.currentPipeline.id, state.activePipelineId)
    }

    @Test
    fun `activePipelineId returns null for empty unsaved pipeline not in saved list`() = runTest {
        // Default state — empty currentPipeline, empty savedPipelines list.
        val state = viewModel.uiState.value
        assertEquals(null, state.activePipelineId)
    }
}
