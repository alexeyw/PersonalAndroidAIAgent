package ai.agent.android.presentation.ui.orchestrator

import ai.agent.android.domain.models.NodeType
import ai.agent.android.domain.models.PipelineGraph
import ai.agent.android.domain.usecases.LoadPipelineUseCase
import ai.agent.android.domain.usecases.SavePipelineUseCase
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
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class OrchestratorViewModelTest {

    private lateinit var savePipelineUseCase: SavePipelineUseCase
    private lateinit var loadPipelineUseCase: LoadPipelineUseCase
    private lateinit var viewModel: OrchestratorViewModel

    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        savePipelineUseCase = mockk()
        loadPipelineUseCase = mockk()
        
        every { loadPipelineUseCase.observeAllPipelines() } returns flowOf(emptyList())
        
        viewModel = OrchestratorViewModel(savePipelineUseCase, loadPipelineUseCase)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
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
    fun `addConnection creates connection if DAG is valid`() {
        // Arrange
        viewModel.addNode(NodeType.INPUT, 0f, 0f)
        viewModel.addNode(NodeType.OUTPUT, 100f, 100f)
        val nodes = viewModel.uiState.value.currentPipeline.nodes
        val n1 = nodes[0].id
        val n2 = nodes[1].id

        // Act
        viewModel.addConnection(n1, n2)

        // Assert
        val connections = viewModel.uiState.value.currentPipeline.connections
        assertEquals(1, connections.size)
        assertEquals(n1, connections[0].sourceNodeId)
        assertEquals(n2, connections[0].targetNodeId)
        assertEquals(null, viewModel.uiState.value.errorMessage)
    }

    @Test
    fun `addConnection sets error if DAG becomes invalid (cycle)`() {
        // Arrange
        viewModel.addNode(NodeType.INPUT, 0f, 0f)
        viewModel.addNode(NodeType.OUTPUT, 100f, 100f)
        val nodes = viewModel.uiState.value.currentPipeline.nodes
        val n1 = nodes[0].id
        val n2 = nodes[1].id

        viewModel.addConnection(n1, n2)

        // Act
        viewModel.addConnection(n2, n1) // This should create a cycle

        // Assert
        val connections = viewModel.uiState.value.currentPipeline.connections
        assertEquals(1, connections.size) // The cyclic connection should not be added
        assertTrue(viewModel.uiState.value.errorMessage?.contains("Cycle detected") == true)
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
}
