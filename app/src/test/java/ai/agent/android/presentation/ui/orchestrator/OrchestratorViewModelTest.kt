package ai.agent.android.presentation.ui.orchestrator

import ai.agent.android.domain.models.NodeType
import ai.agent.android.domain.models.PipelineGraph
import ai.agent.android.domain.models.AgentTool
import ai.agent.android.domain.repositories.ApiKeyRepository
import ai.agent.android.domain.repositories.ToolRepository
import ai.agent.android.domain.usecases.LoadPipelineUseCase
import ai.agent.android.domain.usecases.SavePipelineUseCase
import ai.agent.android.domain.usecases.GetPromptTemplatesUseCase
import ai.agent.android.domain.usecases.SavePromptTemplateUseCase
import io.mockk.coEvery
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
    private lateinit var getPromptTemplatesUseCase: GetPromptTemplatesUseCase
    private lateinit var savePromptTemplateUseCase: SavePromptTemplateUseCase
    private lateinit var apiKeyRepository: ApiKeyRepository
    private lateinit var toolRepository: ToolRepository
    private lateinit var viewModel: OrchestratorViewModel

    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        savePipelineUseCase = mockk()
        loadPipelineUseCase = mockk()
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
        
        viewModel = OrchestratorViewModel(
            savePipelineUseCase, 
            loadPipelineUseCase, 
            getPromptTemplatesUseCase,
            savePromptTemplateUseCase,
            apiKeyRepository, 
            toolRepository
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
    fun `importPipelineFromJson updates current pipeline from json`() {
        viewModel.applyBasePreset()
        val json = viewModel.exportPipelineToJson()
        
        viewModel.clearPipeline()
        assertEquals(0, viewModel.uiState.value.currentPipeline.nodes.size)
        
        viewModel.importPipelineFromJson(json)
        
        val state = viewModel.uiState.value
        assertEquals("Base Preset", state.currentPipeline.name)
        assertEquals(10, state.currentPipeline.nodes.size)
        assertEquals(12, state.currentPipeline.connections.size)
        assertEquals(null, state.errorMessage)
    }

    @Test
    fun `importPipelineFromJson sets error on invalid json`() {
        viewModel.importPipelineFromJson("{ invalid json }")
        
        assertNotNull(viewModel.uiState.value.errorMessage)
        assertTrue(viewModel.uiState.value.errorMessage!!.contains("Invalid JSON format"))
    }
}
