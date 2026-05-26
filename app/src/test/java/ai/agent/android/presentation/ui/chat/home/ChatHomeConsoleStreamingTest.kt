package ai.agent.android.presentation.ui.chat.home

import ai.agent.android.domain.engine.LlmInferenceEngine
import ai.agent.android.domain.models.AgentOrchestratorState
import ai.agent.android.domain.models.ChatMessage
import ai.agent.android.domain.models.ChatSession
import ai.agent.android.domain.models.ConsoleEvent
import ai.agent.android.domain.models.ConsoleEventType
import ai.agent.android.domain.models.LocalModel
import ai.agent.android.domain.models.PipelineGraph
import ai.agent.android.domain.models.Result
import ai.agent.android.domain.repositories.ChatRepository
import ai.agent.android.domain.repositories.ClarificationRepository
import ai.agent.android.domain.repositories.LocalModelRepository
import ai.agent.android.domain.repositories.PipelineRepository
import ai.agent.android.domain.repositories.SettingsRepository
import ai.agent.android.domain.usecases.AgentOrchestratorUseCase
import ai.agent.android.domain.usecases.GetContextWindowUseCase
import ai.agent.android.domain.usecases.LoadModelUseCase
import app.knotwork.design.components.console.ConsoleLevel
import app.knotwork.design.components.console.ConsoleSource
import app.knotwork.design.components.console.ConsoleTab
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Phase 22 / Task 3/17 — coverage for [ChatHomeViewModel] console pane
 * aggregation: how the orchestrator-emitted `ConsoleLog` / `PipelineTrace`
 * / `NodeIO` states are projected into the three console-pane tabs, and
 * how Clear / Copy / Tab callbacks interact with the resulting flows.
 *
 * The orchestrator flow is fed by a `MutableSharedFlow` so each test can
 * drive a deterministic emission sequence and inspect the VM state after
 * every step.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@Suppress("LongMethod")
class ChatHomeConsoleStreamingTest {

    private val testDispatcher = StandardTestDispatcher()

    private lateinit var agentOrchestratorUseCase: AgentOrchestratorUseCase
    private lateinit var chatRepository: ChatRepository
    private lateinit var pipelineRepository: PipelineRepository
    private lateinit var settingsRepository: SettingsRepository
    private lateinit var getContextWindowUseCase: GetContextWindowUseCase
    private lateinit var llmInferenceEngine: LlmInferenceEngine
    private lateinit var clarificationRepository: ClarificationRepository
    private lateinit var localModelRepository: LocalModelRepository
    private lateinit var loadModelUseCase: LoadModelUseCase

    private lateinit var sessionsFlow: MutableStateFlow<List<ChatSession>>
    private lateinit var localModelsFlow: MutableStateFlow<List<LocalModel>>
    private lateinit var pipelinesFlow: MutableStateFlow<List<PipelineGraph>>
    private lateinit var messagesFlow: MutableStateFlow<List<ChatMessage>>
    private lateinit var savedSessionIdFlow: MutableStateFlow<String?>
    private lateinit var defaultPipelineIdFlow: MutableStateFlow<String?>
    private lateinit var maxContextLengthFlow: MutableStateFlow<Int>
    private lateinit var consolePreferredConsoleTabNameFlow: MutableStateFlow<String>
    private lateinit var orchestratorStateFlow: MutableSharedFlow<AgentOrchestratorState>

    private lateinit var viewModel: ChatHomeViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        agentOrchestratorUseCase = mockk(relaxed = true)
        chatRepository = mockk()
        pipelineRepository = mockk()
        settingsRepository = mockk(relaxed = true)
        getContextWindowUseCase = mockk()
        llmInferenceEngine = mockk(relaxed = true)
        clarificationRepository = mockk(relaxed = true)
        localModelRepository = mockk(relaxed = true)
        loadModelUseCase = mockk()
        coEvery { loadModelUseCase(any()) } returns Result.Success(Unit)

        sessionsFlow = MutableStateFlow(emptyList())
        localModelsFlow = MutableStateFlow(emptyList())
        pipelinesFlow = MutableStateFlow(emptyList())
        messagesFlow = MutableStateFlow(emptyList())
        savedSessionIdFlow = MutableStateFlow(null)
        defaultPipelineIdFlow = MutableStateFlow(null)
        maxContextLengthFlow = MutableStateFlow(4096)
        consolePreferredConsoleTabNameFlow = MutableStateFlow("Logs")
        orchestratorStateFlow = MutableSharedFlow(extraBufferCapacity = 64)

        every { llmInferenceEngine.isInitialized } returns true
        every { localModelRepository.getAllModels() } returns localModelsFlow
        every { chatRepository.getSessionsFlow() } returns sessionsFlow
        every { chatRepository.getDisplayMessagesForSession(any()) } returns messagesFlow
        coEvery { chatRepository.saveSession(any()) } answers {
            val saved = firstArg<ChatSession>()
            sessionsFlow.value = sessionsFlow.value.filterNot { it.id == saved.id } + saved
            Unit
        }
        coEvery { chatRepository.saveMessage(any()) } returns Unit
        every { pipelineRepository.getAllPipelines() } returns pipelinesFlow
        every { settingsRepository.currentChatSessionId } returns savedSessionIdFlow
        every { settingsRepository.defaultPipelineId } returns defaultPipelineIdFlow
        every { settingsRepository.maxContextLength } returns maxContextLengthFlow
        every { settingsRepository.consolePreferredConsoleTabName } returns consolePreferredConsoleTabNameFlow
        coEvery { settingsRepository.setCurrentChatSessionId(any()) } answers {
            savedSessionIdFlow.value = firstArg()
        }
        coEvery { settingsRepository.setConsolePreferredConsoleTabName(any()) } answers {
            consolePreferredConsoleTabNameFlow.value = firstArg()
        }
        coEvery { getContextWindowUseCase(any()) } returns ""
        every { agentOrchestratorUseCase(any(), any(), any()) } returns orchestratorStateFlow.asSharedFlow()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun createViewModel(): ChatHomeViewModel = ChatHomeViewModel(
        agentOrchestratorUseCase,
        chatRepository,
        pipelineRepository,
        settingsRepository,
        getContextWindowUseCase,
        llmInferenceEngine,
        clarificationRepository,
        localModelRepository,
        loadModelUseCase,
    )

    @Test
    fun `given ConsoleLog emissions when sendMessage then VM aggregates mapped lines in order`() =
        runTest(testDispatcher) {
            viewModel = createViewModel()
            advanceUntilIdle()
            viewModel.onComposerValueChange("hello")
            viewModel.sendMessage()
            advanceUntilIdle()

            val first = ConsoleEvent(0L, ConsoleEventType.NodeExecution, "▶ INPUT")
            val second = ConsoleEvent(0L, ConsoleEventType.ToolCall, "calendar.create_event")
            orchestratorStateFlow.emit(AgentOrchestratorState.ConsoleLog(listOf(first)))
            orchestratorStateFlow.emit(AgentOrchestratorState.ConsoleLog(listOf(first, second)))
            advanceUntilIdle()

            val lines = viewModel.consoleLines.value
            assertEquals(2, lines.size)
            assertEquals(ConsoleSource.NODE, lines[0].source)
            assertEquals(ConsoleLevel.Trace, lines[0].level)
            assertEquals("▶ INPUT", lines[0].text)
            assertEquals(ConsoleSource.TOOL, lines[1].source)
            assertEquals("calendar.create_event", lines[1].text)
        }

    @Test
    fun `given PipelineTrace emissions when streamed then traces tab reflects spans`() = runTest(testDispatcher) {
        viewModel = createViewModel()
        advanceUntilIdle()
        viewModel.onComposerValueChange("hi")
        viewModel.sendMessage()
        advanceUntilIdle()

        val step = AgentOrchestratorState.TraceStep(
            nodeName = "LITE_RT",
            outputText = "out",
            durationMs = 100L,
            tokenCount = null,
        )
        orchestratorStateFlow.emit(AgentOrchestratorState.PipelineTrace(listOf(step)))
        advanceUntilIdle()

        val traces = viewModel.consoleTraces.value
        assertEquals(1, traces.size)
        assertEquals("LITE_RT", traces[0].name)
        assertEquals(100L, traces[0].durationMs)
    }

    @Test
    fun `given NodeIO emissions when streamed then vars tab has two rows per node`() = runTest(testDispatcher) {
        viewModel = createViewModel()
        advanceUntilIdle()
        viewModel.onComposerValueChange("hi")
        viewModel.sendMessage()
        advanceUntilIdle()

        orchestratorStateFlow.emit(
            AgentOrchestratorState.NodeIO(
                nodeId = "n1",
                nodeType = "LITE_RT",
                input = "in1",
                output = "out1",
            ),
        )
        advanceUntilIdle()

        val varsAfterFirst = viewModel.consoleVars.value
        assertEquals(2, varsAfterFirst.size)
        assertEquals(CONSOLE_VAR_KEY_INPUT, varsAfterFirst[0].key)
        assertEquals(CONSOLE_VAR_KEY_OUTPUT, varsAfterFirst[1].key)

        // Second emission for the same node id overwrites — still two rows total.
        orchestratorStateFlow.emit(
            AgentOrchestratorState.NodeIO(nodeId = "n1", nodeType = "LITE_RT", input = "in2", output = "out2"),
        )
        advanceUntilIdle()
        val rewritten = viewModel.consoleVars.value
        assertEquals(2, rewritten.size)
        assertEquals("\"in2\"", rewritten[0].valueJson)
        assertEquals("\"out2\"", rewritten[1].valueJson)

        // Distinct nodeId appends another pair.
        orchestratorStateFlow.emit(
            AgentOrchestratorState.NodeIO(nodeId = "n2", nodeType = "TOOL", input = "x", output = "y"),
        )
        advanceUntilIdle()
        assertEquals(4, viewModel.consoleVars.value.size)
    }

    @Test
    fun `given prior console events when sendMessage then VM resets the log baseline`() = runTest(testDispatcher) {
        viewModel = createViewModel()
        advanceUntilIdle()
        viewModel.onComposerValueChange("hi")
        viewModel.sendMessage()
        advanceUntilIdle()
        orchestratorStateFlow.emit(
            AgentOrchestratorState.ConsoleLog(
                listOf(ConsoleEvent(0L, ConsoleEventType.NodeExecution, "▶ INPUT")),
            ),
        )
        advanceUntilIdle()
        assertEquals(1, viewModel.consoleLines.value.size)

        // The first run must terminate before a second `sendMessage` is
        // accepted — otherwise the VM's "already generating" guard turns
        // the second call into a no-op and the baseline is never reset.
        orchestratorStateFlow.emit(AgentOrchestratorState.Completed("done"))
        advanceUntilIdle()

        viewModel.onComposerValueChange("again")
        viewModel.sendMessage()
        advanceUntilIdle()

        // Fresh run = empty log + empty vars + empty traces, regardless of prior buffer.
        assertTrue(viewModel.consoleLines.value.isEmpty())
        assertTrue(viewModel.consoleVars.value.isEmpty())
        assertTrue(viewModel.consoleTraces.value.isEmpty())
    }

    @Test
    fun `given visible lines when confirmConsoleClear then VM hides them but next snapshot survives`() =
        runTest(testDispatcher) {
            viewModel = createViewModel()
            advanceUntilIdle()
            viewModel.onComposerValueChange("hi")
            viewModel.sendMessage()
            advanceUntilIdle()
            val a = ConsoleEvent(0L, ConsoleEventType.NodeExecution, "a")
            val b = ConsoleEvent(0L, ConsoleEventType.NodeExecution, "b")
            orchestratorStateFlow.emit(AgentOrchestratorState.ConsoleLog(listOf(a, b)))
            advanceUntilIdle()

            viewModel.requestConsoleClear()
            assertTrue(viewModel.consoleClearConfirmRequested.value)
            viewModel.confirmConsoleClear()
            advanceUntilIdle()

            assertTrue(viewModel.consoleLines.value.isEmpty())
            assertFalse(viewModel.consoleClearConfirmRequested.value)

            // Next cumulative snapshot still carries [a, b] plus a new event;
            // baseline must trim leading 2 rows so only the new one shows.
            val c = ConsoleEvent(0L, ConsoleEventType.NodeExecution, "c")
            orchestratorStateFlow.emit(AgentOrchestratorState.ConsoleLog(listOf(a, b, c)))
            advanceUntilIdle()
            val visible = viewModel.consoleLines.value
            assertEquals(1, visible.size)
            assertEquals("c", visible[0].text)
        }

    @Test
    fun `given pending tab change when onConsoleTabChange then VM persists and updates flow`() =
        runTest(testDispatcher) {
            viewModel = createViewModel()
            advanceUntilIdle()
            assertEquals(ConsoleTab.Logs, viewModel.consoleTab.value)

            viewModel.onConsoleTabChange(ConsoleTab.Traces)
            advanceUntilIdle()

            assertEquals(ConsoleTab.Traces, viewModel.consoleTab.value)
            coVerify { settingsRepository.setConsolePreferredConsoleTabName("Traces") }
        }

    @Test
    fun `given persisted tab when VM initialises then consoleTab hydrates from settings`() = runTest(testDispatcher) {
        consolePreferredConsoleTabNameFlow.value = "Vars"
        viewModel = createViewModel()
        advanceUntilIdle()

        assertEquals(ConsoleTab.Vars, viewModel.consoleTab.value)
    }
}
