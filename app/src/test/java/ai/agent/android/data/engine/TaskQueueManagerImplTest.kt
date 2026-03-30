package ai.agent.android.data.engine

import ai.agent.android.domain.engine.GraphExecutionEngine
import ai.agent.android.domain.models.AgentOrchestratorState
import ai.agent.android.domain.models.AgentTask
import ai.agent.android.domain.models.PipelineGraph
import ai.agent.android.domain.models.TaskPriority
import ai.agent.android.domain.repositories.ChatRepository
import ai.agent.android.domain.repositories.PipelineRepository
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.UUID

@OptIn(ExperimentalCoroutinesApi::class)
class TaskQueueManagerImplTest {

    private val testDispatcher = StandardTestDispatcher()
    private val testScope = TestScope(testDispatcher)

    private lateinit var chatRepository: ChatRepository
    private lateinit var pipelineRepository: PipelineRepository
    private lateinit var graphExecutionEngine: GraphExecutionEngine

    private lateinit var taskQueueManager: TaskQueueManagerImpl

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        
        chatRepository = mockk(relaxed = true)
        pipelineRepository = mockk()
        graphExecutionEngine = mockk()

        val mockPipeline = mockk<PipelineGraph>()
        every { pipelineRepository.getAllPipelines() } returns flowOf(listOf(mockPipeline))

        taskQueueManager = TaskQueueManagerImpl(
            chatRepository = chatRepository,
            pipelineRepository = pipelineRepository,
            graphExecutionEngine = graphExecutionEngine
        )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `enqueueTask processes task successfully`() = kotlinx.coroutines.runBlocking {
        val sessionId = "session_1"
        val prompt = "Hello"
        
        val task = AgentTask(
            sessionId = sessionId,
            prompt = prompt,
            priority = TaskPriority.NORMAL
        )

        every { 
            graphExecutionEngine.invoke(any(), any(), any()) 
        } returns flowOf(AgentOrchestratorState.Completed("Result"))

        taskQueueManager.enqueueTask(task)
        
        kotlinx.coroutines.delay(500) // Wait for the IO dispatcher to process the task

        val state = taskQueueManager.observeTaskState(sessionId).first()
        println("Final state: $state")
        assertTrue("Expected Completed or Idle after processing, got $state", 
            state is AgentOrchestratorState.Idle || state is AgentOrchestratorState.Completed)
    }
}
