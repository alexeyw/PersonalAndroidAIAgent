package app.knotwork.android.data.engine

import app.knotwork.android.domain.engine.GraphExecutionEngine
import app.knotwork.android.domain.models.AgentOrchestratorState
import app.knotwork.android.domain.models.AgentTask
import app.knotwork.android.domain.models.PipelineGraph
import app.knotwork.android.domain.models.TaskPriority
import app.knotwork.android.domain.repositories.ChatRepository
import app.knotwork.android.domain.repositories.PipelineRepository
import app.knotwork.android.domain.repositories.SettingsRepository
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class TaskQueueManagerImplTest {

    private val testDispatcher = StandardTestDispatcher()
    private val testScope = TestScope(testDispatcher)

    private lateinit var chatRepository: ChatRepository
    private lateinit var pipelineRepository: PipelineRepository
    private lateinit var settingsRepository: SettingsRepository
    private lateinit var graphExecutionEngine: GraphExecutionEngine

    private lateinit var taskQueueManager: TaskQueueManagerImpl

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)

        chatRepository = mockk(relaxed = true)
        pipelineRepository = mockk()
        settingsRepository = mockk()
        graphExecutionEngine = mockk()

        // Baseline library: a single pipeline marked as the user default, so
        // tests that exercise unrelated behaviour (eviction, cancellation)
        // resolve a pipeline without caring about the resolution chain.
        val seedPipeline = PipelineGraph(id = "seed-id", name = "Seed")
        every { pipelineRepository.getAllPipelines() } returns flowOf(listOf(seedPipeline))
        every { settingsRepository.defaultPipelineId } returns flowOf("seed-id")

        taskQueueManager = TaskQueueManagerImpl(
            chatRepository = chatRepository,
            pipelineRepository = pipelineRepository,
            settingsRepository = settingsRepository,
            graphExecutionEngine = graphExecutionEngine,
        ).apply {
            dispatcher = testDispatcher
        }
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `given 21 terminal sessions when state flows created then oldest terminal is evicted`() {
        repeat(21) { i ->
            taskQueueManager.observeTaskState("session_$i")
        }
        // All sessions start as Idle (terminal), so session_0 must be evicted to make room for session_20
        assertEquals(20, taskQueueManager.sessionStates.size)
        assertTrue("session_0 should be evicted", !taskQueueManager.sessionStates.containsKey("session_0"))
        assertTrue("session_20 should be present", taskQueueManager.sessionStates.containsKey("session_20"))
    }

    @Test
    fun `given all sessions are active when at capacity then no session is evicted`() {
        repeat(20) { i ->
            taskQueueManager.observeTaskState("session_$i")
            // Push the session into a non-terminal state. The per-session
            // flow is a SharedFlow now (was a StateFlow); `tryEmit`
            // replaces the `.value =` setter while keeping the test free
            // of suspending calls.
            taskQueueManager.sessionStates["session_$i"]?.tryEmit(AgentOrchestratorState.Loading)
        }
        taskQueueManager.observeTaskState("session_20")
        // No terminal session to evict, so size grows beyond MAX_SESSION_STATES
        assertEquals(21, taskQueueManager.sessionStates.size)
        assertTrue("active session_0 must not be evicted", taskQueueManager.sessionStates.containsKey("session_0"))
    }

    /**
     * Tests that enqueuing a task processes it successfully and updates the session state
     * without race conditions or deadlocks.
     */
    @Test
    fun `enqueueTask processes task successfully`() = testScope.runTest {
        val sessionId = "session_1"
        val prompt = "Hello"

        val task = AgentTask(
            sessionId = sessionId,
            prompt = prompt,
            priority = TaskPriority.NORMAL,
        )

        every {
            graphExecutionEngine.invoke(any(), any(), any())
        } returns flowOf(AgentOrchestratorState.Completed("Result"))

        taskQueueManager.enqueueTask(task)

        advanceUntilIdle() // Wait for the IO dispatcher to process the task without delay

        val state = taskQueueManager.observeTaskState(sessionId).first()
        println("Final state: $state")
        assertTrue(
            "Expected Completed or Idle after processing, got $state",
            state is AgentOrchestratorState.Idle || state is AgentOrchestratorState.Completed,
        )
    }

    /**
     * When an [AgentTask] carries a `pipelineId`, the queue
     * must execute that specific pipeline rather than the global default
     * (the first pipeline in the repository).
     */
    @Test
    fun `enqueueTask honours per-task pipelineId`() = testScope.runTest {
        val defaultPipeline = PipelineGraph(id = "default-id", name = "Default")
        val boundPipeline = PipelineGraph(id = "bound-id", name = "Bound")
        every { pipelineRepository.getAllPipelines() } returns flowOf(
            listOf(defaultPipeline, boundPipeline),
        )
        every {
            graphExecutionEngine.invoke(any(), any(), any())
        } returns flowOf(AgentOrchestratorState.Completed("ok"))

        val task = AgentTask(
            sessionId = "session-bound",
            prompt = "go",
            priority = TaskPriority.NORMAL,
            pipelineId = "bound-id",
        )

        taskQueueManager.enqueueTask(task)
        advanceUntilIdle()

        verify {
            graphExecutionEngine.invoke(
                "session-bound",
                "go",
                match<PipelineGraph> { it.id == "bound-id" },
            )
        }
    }

    /**
     * When the bound pipeline has been deleted while the task waited in
     * the queue, fall back to the user-marked default rather than failing
     * the task. The chat-level UI handles the rebind + Snackbar
     * notification separately. The default is deliberately not the first
     * element of the library to prove the resolution is order-independent.
     */
    @Test
    fun `enqueueTask falls back to user default when bound id is missing`() = testScope.runTest {
        val otherPipeline = PipelineGraph(id = "other-id", name = "Other")
        val defaultPipeline = PipelineGraph(id = "default-id", name = "Default")
        every { pipelineRepository.getAllPipelines() } returns flowOf(
            listOf(otherPipeline, defaultPipeline),
        )
        every { settingsRepository.defaultPipelineId } returns flowOf("default-id")
        every {
            graphExecutionEngine.invoke(any(), any(), any())
        } returns flowOf(AgentOrchestratorState.Completed("ok"))

        val task = AgentTask(
            sessionId = "session-orphaned",
            prompt = "go",
            priority = TaskPriority.NORMAL,
            pipelineId = "missing-id",
        )

        taskQueueManager.enqueueTask(task)
        advanceUntilIdle()

        verify {
            graphExecutionEngine.invoke(
                "session-orphaned",
                "go",
                match<PipelineGraph> { it.id == "default-id" },
            )
        }
    }

    /**
     * When the task carries no `pipelineId`, the queue uses the
     * user-marked default from `SettingsRepository.defaultPipelineId` —
     * never "whatever the repository returned first". The default sits
     * last in the library to prove the order does not matter.
     */
    @Test
    fun `enqueueTask uses default pipeline when task has no pipelineId`() = testScope.runTest {
        val otherPipeline = PipelineGraph(id = "other-id", name = "Other")
        val defaultPipeline = PipelineGraph(id = "default-id", name = "Default")
        every { pipelineRepository.getAllPipelines() } returns flowOf(
            listOf(otherPipeline, defaultPipeline),
        )
        every { settingsRepository.defaultPipelineId } returns flowOf("default-id")
        every {
            graphExecutionEngine.invoke(any(), any(), any())
        } returns flowOf(AgentOrchestratorState.Completed("ok"))

        val task = AgentTask(
            sessionId = "session-unbound",
            prompt = "go",
            priority = TaskPriority.NORMAL,
            pipelineId = null,
        )

        taskQueueManager.enqueueTask(task)
        advanceUntilIdle()

        verify {
            graphExecutionEngine.invoke(
                "session-unbound",
                "go",
                match<PipelineGraph> { it.id == "default-id" },
            )
        }
    }

    /**
     * No binding and no configured default must surface an explicit,
     * actionable `Error` — even though the library is non-empty, the
     * queue never executes an arbitrary pipeline.
     */
    @Test
    fun `enqueueTask errors when no binding and no default configured`() = testScope.runTest {
        val pipelines = listOf(
            PipelineGraph(id = "a-id", name = "A"),
            PipelineGraph(id = "b-id", name = "B"),
        )
        every { pipelineRepository.getAllPipelines() } returns flowOf(pipelines)
        every { settingsRepository.defaultPipelineId } returns flowOf(null)

        val task = AgentTask(
            sessionId = "session-no-default",
            prompt = "go",
            priority = TaskPriority.NORMAL,
            pipelineId = null,
        )

        taskQueueManager.enqueueTask(task)
        advanceUntilIdle()

        val state = taskQueueManager.observeTaskState("session-no-default").first()
        assertTrue("Expected Error, got $state", state is AgentOrchestratorState.Error)
        assertEquals(
            "No default pipeline configured. Set one in Settings or bind a pipeline to this chat.",
            (state as AgentOrchestratorState.Error).message,
        )
        verify(exactly = 0) { graphExecutionEngine.invoke(any(), any(), any()) }
    }

    /**
     * A stale binding combined with a missing default has nothing left in
     * the resolution chain — the task fails with the same explicit error
     * instead of silently substituting a library pipeline.
     */
    @Test
    fun `enqueueTask errors when bound id is missing and no default configured`() = testScope.runTest {
        every { pipelineRepository.getAllPipelines() } returns flowOf(
            listOf(PipelineGraph(id = "a-id", name = "A")),
        )
        every { settingsRepository.defaultPipelineId } returns flowOf(null)

        val task = AgentTask(
            sessionId = "session-orphaned-no-default",
            prompt = "go",
            priority = TaskPriority.NORMAL,
            pipelineId = "missing-id",
        )

        taskQueueManager.enqueueTask(task)
        advanceUntilIdle()

        val state = taskQueueManager.observeTaskState("session-orphaned-no-default").first()
        assertTrue("Expected Error, got $state", state is AgentOrchestratorState.Error)
        verify(exactly = 0) { graphExecutionEngine.invoke(any(), any(), any()) }
    }

    /**
     * An empty pipeline library keeps its own, more specific error copy —
     * "Set one in Settings" would be misleading when there is nothing to
     * set a default to.
     */
    @Test
    fun `enqueueTask errors with create-one message when library is empty`() = testScope.runTest {
        every { pipelineRepository.getAllPipelines() } returns flowOf(emptyList())
        every { settingsRepository.defaultPipelineId } returns flowOf(null)

        val task = AgentTask(
            sessionId = "session-empty-library",
            prompt = "go",
            priority = TaskPriority.NORMAL,
        )

        taskQueueManager.enqueueTask(task)
        advanceUntilIdle()

        val state = taskQueueManager.observeTaskState("session-empty-library").first()
        assertTrue("Expected Error, got $state", state is AgentOrchestratorState.Error)
        assertEquals(
            "No active pipeline found. Please create one in the Visual Orchestrator.",
            (state as AgentOrchestratorState.Error).message,
        )
    }

    /**
     * Cancelling the worker scope while a task is mid-execution must never
     * surface as a user-facing `Error`: the `CancellationException` is
     * re-thrown (cooperative cancellation) and the `finally` block resets the
     * session to `Idle` under `NonCancellable`.
     */
    @Test
    fun `given running task when scope is cancelled then state resets to Idle not Error`() = testScope.runTest {
        val sessionId = "session_cancelled"
        every { graphExecutionEngine.invoke(any(), any(), any()) } returns flow {
            emit(AgentOrchestratorState.Loading)
            awaitCancellation()
        }

        taskQueueManager.enqueueTask(
            AgentTask(sessionId = sessionId, prompt = "p", priority = TaskPriority.NORMAL),
        )
        advanceUntilIdle() // Task is now suspended inside the engine flow.

        taskQueueManager.scope.cancel()
        advanceUntilIdle()

        val state = taskQueueManager.observeTaskState(sessionId).first()
        assertTrue("Cancellation must not surface as Error, got $state", state !is AgentOrchestratorState.Error)
        assertEquals(AgentOrchestratorState.Idle, state)
        assertEquals(AgentOrchestratorState.Idle, taskQueueManager.globalState.value)
    }

    /**
     * A `CancellationException` escaping the engine flow itself (e.g. an
     * executor honouring a Stop) must propagate out of `processTask` instead
     * of being mapped to `Error`; the session still settles on `Idle`.
     */
    @Test
    fun `given engine flow throws CancellationException then state settles on Idle not Error`() = testScope.runTest {
        val sessionId = "session_engine_ce"
        every { graphExecutionEngine.invoke(any(), any(), any()) } returns flow {
            emit(AgentOrchestratorState.Loading)
            throw CancellationException("user stop")
        }

        taskQueueManager.enqueueTask(
            AgentTask(sessionId = sessionId, prompt = "p", priority = TaskPriority.NORMAL),
        )
        advanceUntilIdle()

        val state = taskQueueManager.observeTaskState(sessionId).first()
        assertTrue("Cancellation must not surface as Error, got $state", state !is AgentOrchestratorState.Error)
        assertEquals(AgentOrchestratorState.Idle, state)
    }

    /**
     * Counter-case locking the other side of the contract: a plain exception
     * from the engine flow is still mapped to a user-facing `Error` state.
     */
    @Test
    fun `given engine flow throws plain exception then state is Error`() = testScope.runTest {
        val sessionId = "session_engine_error"
        every { graphExecutionEngine.invoke(any(), any(), any()) } returns flow {
            emit(AgentOrchestratorState.Loading)
            throw IllegalStateException("engine blew up")
        }

        taskQueueManager.enqueueTask(
            AgentTask(sessionId = sessionId, prompt = "p", priority = TaskPriority.NORMAL),
        )
        advanceUntilIdle()

        val state = taskQueueManager.observeTaskState(sessionId).first()
        assertTrue("Expected Error, got $state", state is AgentOrchestratorState.Error)
        assertEquals("engine blew up", (state as AgentOrchestratorState.Error).message)
    }
}
