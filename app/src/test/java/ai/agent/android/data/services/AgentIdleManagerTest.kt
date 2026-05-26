package ai.agent.android.data.services

import ai.agent.android.domain.engine.LlmInferenceEngine
import ai.agent.android.domain.models.AgentOrchestratorState
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AgentIdleManagerTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var llmEngine: LlmInferenceEngine
    private lateinit var stateFlow: MutableStateFlow<AgentOrchestratorState>
    private lateinit var idleManager: AgentIdleManager

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        llmEngine = mockk(relaxed = true)
        every { llmEngine.isInitialized } returns true

        stateFlow = MutableStateFlow(AgentOrchestratorState.Loading)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `when state becomes Idle, timer starts and engine unloads after timeout`() = runTest(testDispatcher) {
        idleManager = AgentIdleManager(
            scope = CoroutineScope(testDispatcher),
            engine = llmEngine,
            agentState = stateFlow,
            idleTimeoutMs = 1000L,
        )
        idleManager.startObserving()

        // Change state to Idle
        stateFlow.value = AgentOrchestratorState.Idle

        // Advance time just before timeout
        advanceTimeBy(900L)
        verify(exactly = 0) { llmEngine.unload() }

        // Advance time to pass the timeout
        advanceTimeBy(200L)
        verify(exactly = 1) { llmEngine.unload() }
    }

    @Test
    fun `when state becomes Active during timer, timer is cancelled`() = runTest(testDispatcher) {
        idleManager = AgentIdleManager(
            scope = CoroutineScope(testDispatcher),
            engine = llmEngine,
            agentState = stateFlow,
            idleTimeoutMs = 1000L,
        )
        idleManager.startObserving()

        // Start timer by going Idle
        stateFlow.value = AgentOrchestratorState.Idle
        advanceTimeBy(500L)

        // Become active again
        stateFlow.value = AgentOrchestratorState.Thinking("Hmm")
        advanceTimeBy(600L) // Total 1100L from the start of Idle

        // Timer should have been cancelled, so unload is not called
        verify(exactly = 0) { llmEngine.unload() }
    }

    @Test
    fun `when state becomes Completed, timer starts and unloads after timeout`() = runTest(testDispatcher) {
        idleManager = AgentIdleManager(
            scope = CoroutineScope(testDispatcher),
            engine = llmEngine,
            agentState = stateFlow,
            idleTimeoutMs = 1000L,
        )
        idleManager.startObserving()

        // Change state to Completed
        stateFlow.value = AgentOrchestratorState.Completed("Done")

        // Advance time past timeout
        advanceTimeBy(1100L)
        verify(exactly = 1) { llmEngine.unload() }
    }

    @Test
    fun `given engine not initialized when timer fires then unload is not called`() = runTest(testDispatcher) {
        every { llmEngine.isInitialized } returns false

        idleManager = AgentIdleManager(
            scope = CoroutineScope(testDispatcher),
            engine = llmEngine,
            agentState = stateFlow,
            idleTimeoutMs = 1000L,
        )
        idleManager.startObserving()

        stateFlow.value = AgentOrchestratorState.Idle
        advanceTimeBy(1100L)

        verify(exactly = 0) { llmEngine.unload() }
    }

    @Test
    fun `given no startObserving when state changes then unload is never called`() = runTest(testDispatcher) {
        idleManager = AgentIdleManager(
            scope = CoroutineScope(testDispatcher),
            engine = llmEngine,
            agentState = stateFlow,
            idleTimeoutMs = 1000L,
        )
        // Deliberately skip startObserving().
        stateFlow.value = AgentOrchestratorState.Idle
        advanceTimeBy(5000L)

        verify(exactly = 0) { llmEngine.unload() }
    }

    @Test
    fun `given Loading state when emitted then idle timer is not started`() = runTest(testDispatcher) {
        idleManager = AgentIdleManager(
            scope = CoroutineScope(testDispatcher),
            engine = llmEngine,
            agentState = stateFlow,
            idleTimeoutMs = 1000L,
        )
        idleManager.startObserving()

        // Loading is not in the idle set — timer must NOT fire.
        stateFlow.value = AgentOrchestratorState.Loading
        advanceTimeBy(5000L)

        verify(exactly = 0) { llmEngine.unload() }
    }

    @Test
    fun `given Error state when emitted then idle timer starts and unloads`() = runTest(testDispatcher) {
        idleManager = AgentIdleManager(
            scope = CoroutineScope(testDispatcher),
            engine = llmEngine,
            agentState = stateFlow,
            idleTimeoutMs = 1000L,
        )
        idleManager.startObserving()

        stateFlow.value = AgentOrchestratorState.Error("boom")
        advanceTimeBy(1100L)

        verify(exactly = 1) { llmEngine.unload() }
    }

    @Test
    fun `given default constructor when instantiated then the default idleTimeoutMs is 5 minutes`() {
        // Locks the documented default — bumping it requires updating the KDoc on
        // `AgentIdleManager.DEFAULT_IDLE_TIMEOUT_MINUTES` and this assertion together.
        val withDefault = AgentIdleManager(
            scope = CoroutineScope(testDispatcher),
            engine = llmEngine,
            agentState = stateFlow,
        )
        val field = AgentIdleManager::class.java.getDeclaredField("idleTimeoutMs")
        field.isAccessible = true
        val resolved = field.getLong(withDefault)
        org.junit.Assert.assertEquals(5L * 60_000L, resolved)
    }

    @Test
    fun `concurrent idle timers do not call unload more than once via Mutex`() = runTest(testDispatcher) {
        // Defect 2 regression guard: replacing `synchronized(engine)` with `Mutex.withLock`
        // must still serialise the unload path. We force several rapid Idle→Active→Idle
        // transitions; only the most recent timer survives (`collectLatest` cancels the
        // previous launch), so unload must fire exactly once after the final timeout.
        idleManager = AgentIdleManager(
            scope = CoroutineScope(testDispatcher),
            engine = llmEngine,
            agentState = stateFlow,
            idleTimeoutMs = 1000L,
        )
        idleManager.startObserving()

        repeat(3) {
            stateFlow.value = AgentOrchestratorState.Idle
            advanceTimeBy(200L)
            stateFlow.value = AgentOrchestratorState.Thinking("...")
            advanceTimeBy(50L)
        }
        // Final transition into Idle, then advance past the timeout.
        stateFlow.value = AgentOrchestratorState.Idle
        advanceTimeBy(1100L)

        verify(exactly = 1) { llmEngine.unload() }
    }
}
