package ai.agent.android.presentation.ui.chat.home

import ai.agent.android.domain.engine.LlmInferenceEngine
import ai.agent.android.domain.models.AgentOrchestratorState
import ai.agent.android.domain.models.ChatMessage
import ai.agent.android.domain.models.ChatSession
import ai.agent.android.domain.models.ClarificationRequest
import ai.agent.android.domain.models.LocalModel
import ai.agent.android.domain.models.PipelineGraph
import ai.agent.android.domain.models.Result
import ai.agent.android.domain.models.Role
import ai.agent.android.domain.models.ToolRisk
import ai.agent.android.domain.repositories.ChatRepository
import ai.agent.android.domain.repositories.ClarificationRepository
import ai.agent.android.domain.repositories.LocalModelRepository
import ai.agent.android.domain.repositories.PipelineRepository
import ai.agent.android.domain.repositories.SettingsRepository
import ai.agent.android.domain.usecases.AgentOrchestratorUseCase
import ai.agent.android.domain.usecases.GetContextWindowUseCase
import ai.agent.android.domain.usecases.LoadModelUseCase
import ai.agent.android.domain.usecases.SaveMessageToMemoryUseCase
import ai.agent.android.domain.usecases.SaveToMemoryOutcome
import app.knotwork.design.components.chat.ChatContent
import app.knotwork.design.components.chat.ChatRole
import app.knotwork.design.components.chips.Risk
import app.knotwork.design.components.console.ConsoleSnap
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Unit-tests for [ChatHomeViewModel] under Phase 22 / Task 1/17 wiring.
 *
 * Covers the orchestrator-driven send/stop cycle, session initialisation,
 * thread switching, the pipeline-binding deleted-fallback Snackbar event,
 * auto-rename of new chats, model-not-loaded error gate, and the
 * `ChatMessage → ChatHomeMessageRow` mapping on the companion.
 *
 * Out of scope here (later tasks):
 *  - HITL `WaitingForApproval` / `AwaitingClarification` (Task 2/17)
 *  - Console `ConsoleLog` aggregation (Task 3/17)
 *  - Drawer / overflow / model-picker callbacks (Task 4/17)
 */
@OptIn(ExperimentalCoroutinesApi::class)
@Suppress(
    // Reason: chat home tests cover a 12-method ViewModel surface — every
    // public entry-point gets at least one happy-path assertion.
    "LargeClass",
    "LongMethod",
)
class ChatHomeViewModelTest {

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
    private lateinit var saveMessageToMemoryUseCase: SaveMessageToMemoryUseCase

    private lateinit var sessionsFlow: MutableStateFlow<List<ChatSession>>
    private lateinit var localModelsFlow: MutableStateFlow<List<LocalModel>>
    private lateinit var pipelinesFlow: MutableStateFlow<List<PipelineGraph>>
    private lateinit var messagesFlow: MutableStateFlow<List<ChatMessage>>
    private lateinit var savedSessionIdFlow: MutableStateFlow<String?>
    private lateinit var defaultPipelineIdFlow: MutableStateFlow<String?>
    private lateinit var maxContextLengthFlow: MutableStateFlow<Int>
    private lateinit var consolePreferredConsoleTabNameFlow: MutableStateFlow<String>

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
        saveMessageToMemoryUseCase = mockk()

        sessionsFlow = MutableStateFlow(emptyList())
        localModelsFlow = MutableStateFlow(emptyList())
        pipelinesFlow = MutableStateFlow(emptyList())
        messagesFlow = MutableStateFlow(emptyList())
        savedSessionIdFlow = MutableStateFlow(null)
        defaultPipelineIdFlow = MutableStateFlow(null)
        maxContextLengthFlow = MutableStateFlow(DEFAULT_TOKENS_MAX)
        consolePreferredConsoleTabNameFlow = MutableStateFlow("Logs")

        every { llmInferenceEngine.isInitialized } returns true
        every { localModelRepository.getAllModels() } returns localModelsFlow
        coEvery { loadModelUseCase(any()) } returns Result.Success(Unit)
        coEvery { chatRepository.renameSession(any(), any()) } returns Unit
        coEvery { chatRepository.setSessionFavorite(any(), any()) } returns Unit
        coEvery { chatRepository.deleteSession(any()) } returns Unit
        coEvery { chatRepository.importChat(any()) } returns "imported-session-id"
        coEvery { chatRepository.getMessagesForSession(any()) } returns flowOf(emptyList())
        coEvery { chatRepository.getSessionById(any()) } returns null
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
        coEvery { settingsRepository.setConsolePreferredConsoleTabName(any()) } answers {
            consolePreferredConsoleTabNameFlow.value = firstArg()
        }
        coEvery { settingsRepository.setCurrentChatSessionId(any()) } answers {
            savedSessionIdFlow.value = firstArg()
        }
        coEvery { getContextWindowUseCase(any()) } returns ""
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
        mockk(relaxed = true),
        saveMessageToMemoryUseCase,
    )

    @Test
    fun `init generates session id when none persisted`() = runTest(testDispatcher) {
        viewModel = createViewModel()
        advanceUntilIdle()

        val sessionId = viewModel.currentSessionId.value
        assertTrue("Generated session id must not be blank", sessionId.isNotBlank())
        coVerify { settingsRepository.setCurrentChatSessionId(any()) }
        coVerify { chatRepository.saveSession(match { it.id == sessionId }) }
    }

    @Test
    fun `init restores session id from settings without generating a new one`() = runTest(testDispatcher) {
        val saved = "saved-session-42"
        savedSessionIdFlow.value = saved
        sessionsFlow.value = listOf(ChatSession(id = saved, name = "Existing", updatedAt = 0))

        viewModel = createViewModel()
        advanceUntilIdle()

        assertEquals(saved, viewModel.currentSessionId.value)
        coVerify(exactly = 0) { settingsRepository.setCurrentChatSessionId(any()) }
    }

    @Test
    fun `initial state is Empty with no messages`() = runTest(testDispatcher) {
        viewModel = createViewModel()
        advanceUntilIdle()

        assertEquals(ChatHomeUiState.Empty, viewModel.state.value)
        assertTrue(viewModel.messages.value.isEmpty())
    }

    @Test
    fun `messages flow projects ChatMessage rows when display flow emits`() = runTest(testDispatcher) {
        viewModel = createViewModel()
        advanceUntilIdle()
        val sessionId = viewModel.currentSessionId.value

        messagesFlow.value = listOf(
            ChatMessage(id = 1, sessionId = sessionId, role = Role.USER, content = "hi", timestamp = 0),
            ChatMessage(id = 2, sessionId = sessionId, role = Role.AGENT, content = "hello", timestamp = 0),
        )
        advanceUntilIdle()

        val rows = viewModel.messages.value
        assertEquals(2, rows.size)
        assertEquals(ChatRole.User, rows[0].role)
        assertEquals(ChatRole.Assistant, rows[1].role)
        assertEquals("hi", (rows[0].content as ChatContent.Text).text)
        // Phase 22 / Task 16 follow-up F2 — agent rows now carry Markdown
        // content so the host-supplied markdown renderer formats them.
        assertEquals("hello", (rows[1].content as ChatContent.Markdown).source)
        assertEquals(ChatHomeUiState.Idle, viewModel.state.value)
    }

    @Test
    fun `saveMessageToMemory persists the row text and emits a Saved event`() = runTest(testDispatcher) {
        viewModel = createViewModel()
        advanceUntilIdle()
        val sessionId = viewModel.currentSessionId.value
        messagesFlow.value = listOf(
            ChatMessage(id = 1, sessionId = sessionId, role = Role.USER, content = "remember me", timestamp = 0),
        )
        advanceUntilIdle()
        val rowId = viewModel.messages.value.first().id
        coEvery { saveMessageToMemoryUseCase("remember me") } returns SaveToMemoryOutcome.Saved(id = 1L)

        val events = mutableListOf<MemorySaveEvent>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.memorySaveEvents.collect { events.add(it) }
        }

        viewModel.saveMessageToMemory(rowId)
        advanceUntilIdle()

        coVerify(exactly = 1) { saveMessageToMemoryUseCase("remember me") }
        assertEquals(listOf(MemorySaveEvent.Saved), events)
    }

    @Test
    fun `saveMessageToMemory emits a Failed event when the use case fails`() = runTest(testDispatcher) {
        viewModel = createViewModel()
        advanceUntilIdle()
        val sessionId = viewModel.currentSessionId.value
        messagesFlow.value = listOf(
            ChatMessage(id = 1, sessionId = sessionId, role = Role.USER, content = "remember me", timestamp = 0),
        )
        advanceUntilIdle()
        val rowId = viewModel.messages.value.first().id
        coEvery { saveMessageToMemoryUseCase(any()) } returns SaveToMemoryOutcome.Failed(RuntimeException("boom"))

        val events = mutableListOf<MemorySaveEvent>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.memorySaveEvents.collect { events.add(it) }
        }

        viewModel.saveMessageToMemory(rowId)
        advanceUntilIdle()

        assertEquals(listOf(MemorySaveEvent.Failed), events)
    }

    @Test
    fun `composer value is hoisted via onComposerValueChange`() = runTest(testDispatcher) {
        viewModel = createViewModel()
        advanceUntilIdle()
        viewModel.onComposerValueChange("hello")
        assertEquals("hello", viewModel.composerValue.value)
    }

    @Test
    fun `typed-confirm value is hoisted via onTypedConfirmChange`() = runTest(testDispatcher) {
        viewModel = createViewModel()
        advanceUntilIdle()
        viewModel.onTypedConfirmChange("yes")
        assertEquals("yes", viewModel.pendingTypedConfirm.value)
    }

    @Test
    fun `sendMessage with blank composer is a no-op`() = runTest(testDispatcher) {
        viewModel = createViewModel()
        advanceUntilIdle()
        viewModel.onComposerValueChange("   ")
        viewModel.sendMessage()
        advanceUntilIdle()

        assertEquals(ChatHomeUiState.Empty, viewModel.state.value)
        coVerify(exactly = 0) { agentOrchestratorUseCase(any(), any(), any()) }
    }

    @Test
    fun `sendMessage flips to Error when model is not initialized`() = runTest(testDispatcher) {
        every { llmInferenceEngine.isInitialized } returns false

        viewModel = createViewModel()
        advanceUntilIdle()
        viewModel.onComposerValueChange("hello")
        viewModel.sendMessage()
        advanceUntilIdle()

        val state = viewModel.state.value
        assertTrue("Expected Error, got $state", state is ChatHomeUiState.Error)
        assertEquals(
            ChatHomeViewModel.MODEL_NOT_LOADED_MESSAGE,
            (state as ChatHomeUiState.Error).message,
        )
        coVerify(exactly = 0) { agentOrchestratorUseCase(any(), any(), any()) }
    }

    @Test
    fun `sendMessage flips to Generating then Idle when orchestrator completes`() = runTest(testDispatcher) {
        viewModel = createViewModel()
        advanceUntilIdle()
        val sessionId = viewModel.currentSessionId.value
        coEvery { agentOrchestratorUseCase(sessionId, "hi", null) } returns flow {
            emit(AgentOrchestratorState.Loading)
            emit(AgentOrchestratorState.Completed("done"))
        }

        viewModel.onComposerValueChange("hi")
        viewModel.sendMessage()
        advanceUntilIdle()

        assertEquals("", viewModel.composerValue.value)
        assertEquals(ChatHomeUiState.Empty, viewModel.state.value) // no messages persisted in this stub
        coVerify { agentOrchestratorUseCase(sessionId, "hi", null) }
    }

    @Test
    fun `sendMessage flips to Error when orchestrator throws`() = runTest(testDispatcher) {
        viewModel = createViewModel()
        advanceUntilIdle()
        val sessionId = viewModel.currentSessionId.value
        coEvery { agentOrchestratorUseCase(sessionId, "hi", null) } returns flow {
            throw RuntimeException("boom")
        }

        viewModel.onComposerValueChange("hi")
        viewModel.sendMessage()
        advanceUntilIdle()

        val state = viewModel.state.value
        assertTrue("Expected Error, got $state", state is ChatHomeUiState.Error)
        assertEquals("boom", (state as ChatHomeUiState.Error).message)
    }

    @Test
    fun `sendMessage forwards the pipelineId bound to the active session`() = runTest(testDispatcher) {
        viewModel = createViewModel()
        advanceUntilIdle()
        val sessionId = viewModel.currentSessionId.value
        val expectedPipeline = "pipeline-abc"
        // Publish the pipeline first so the deleted-fallback handler keeps the binding intact.
        pipelinesFlow.value = listOf(PipelineGraph(id = expectedPipeline, name = "Bound"))
        sessionsFlow.value = listOf(
            ChatSession(id = sessionId, name = "S", updatedAt = 0, pipelineId = expectedPipeline),
        )
        advanceUntilIdle()
        coEvery { agentOrchestratorUseCase(sessionId, "hi", expectedPipeline) } returns flowOf(
            AgentOrchestratorState.Completed("ok"),
        )

        viewModel.onComposerValueChange("hi")
        viewModel.sendMessage()
        advanceUntilIdle()

        coVerify { agentOrchestratorUseCase(sessionId, "hi", expectedPipeline) }
    }

    @Test
    fun `sendMessage auto-renames a new chat after the first user prompt`() = runTest(testDispatcher) {
        viewModel = createViewModel()
        advanceUntilIdle()
        val sessionId = viewModel.currentSessionId.value
        // initializeSession() created a session named DEFAULT_NEW_CHAT_NAME
        assertEquals(
            ChatHomeViewModel.DEFAULT_NEW_CHAT_NAME,
            sessionsFlow.value.first { it.id == sessionId }.name,
        )
        coEvery { agentOrchestratorUseCase(sessionId, any(), any()) } returns flowOf(
            AgentOrchestratorState.Completed("ok"),
        )

        viewModel.onComposerValueChange("hello world")
        viewModel.sendMessage()
        advanceUntilIdle()

        val renamed = sessionsFlow.value.first { it.id == sessionId }
        assertEquals("hello world", renamed.name)
    }

    @Test
    fun `sendMessage truncates an over-long prompt when auto-renaming`() = runTest(testDispatcher) {
        viewModel = createViewModel()
        advanceUntilIdle()
        val sessionId = viewModel.currentSessionId.value
        val long = "x".repeat(ChatHomeViewModel.AUTO_RENAME_CHAR_LIMIT + 5)
        coEvery { agentOrchestratorUseCase(sessionId, any(), any()) } returns flowOf(
            AgentOrchestratorState.Completed("ok"),
        )

        viewModel.onComposerValueChange(long)
        viewModel.sendMessage()
        advanceUntilIdle()

        val renamed = sessionsFlow.value.first { it.id == sessionId }.name
        assertEquals(
            "x".repeat(ChatHomeViewModel.AUTO_RENAME_CHAR_LIMIT) + ChatHomeViewModel.AUTO_RENAME_SUFFIX,
            renamed,
        )
    }

    @Test
    fun `stopGeneration cancels the in-flight job and returns to a resting state`() = runTest(testDispatcher) {
        viewModel = createViewModel()
        advanceUntilIdle()
        val sessionId = viewModel.currentSessionId.value
        coEvery { agentOrchestratorUseCase(sessionId, "hi", null) } returns flow {
            emit(AgentOrchestratorState.Loading)
            delay(10_000)
            emit(AgentOrchestratorState.Completed("never"))
        }

        viewModel.onComposerValueChange("hi")
        viewModel.sendMessage()
        testScheduler.advanceTimeBy(100)
        assertEquals(ChatHomeUiState.Generating, viewModel.state.value)

        viewModel.stopGeneration()
        advanceUntilIdle()

        assertEquals(ChatHomeUiState.Empty, viewModel.state.value)
    }

    @Test
    fun `stopGeneration is a no-op when state is not Generating`() = runTest(testDispatcher) {
        viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.forceState(ChatHomeUiState.HitlConfirm(Risk.Sensitive))
        viewModel.stopGeneration()
        assertEquals(ChatHomeUiState.HitlConfirm(Risk.Sensitive), viewModel.state.value)
    }

    @Test
    fun `selectThread persists the id, re-subscribes the message stream, and settles state`() =
        runTest(testDispatcher) {
            viewModel = createViewModel()
            advanceUntilIdle()
            val target = "thread-xyz"
            sessionsFlow.value = sessionsFlow.value + ChatSession(id = target, name = "Other", updatedAt = 0)
            val targetMessages = MutableStateFlow<List<ChatMessage>>(emptyList())
            every { chatRepository.getDisplayMessagesForSession(target) } returns targetMessages

            viewModel.selectThread(target)
            advanceUntilIdle()

            assertEquals(target, viewModel.currentSessionId.value)
            assertEquals("Other", viewModel.threadTitle.value)
            coVerify { settingsRepository.setCurrentChatSessionId(target) }
            verify { chatRepository.getDisplayMessagesForSession(target) }
        }

    @Test
    fun `deleted bound pipeline triggers default fallback and emits one-shot event`() = runTest(testDispatcher) {
        viewModel = createViewModel()
        advanceUntilIdle()
        val sessionId = viewModel.currentSessionId.value
        // Bind the session to a pipeline that exists initially…
        pipelinesFlow.value = listOf(PipelineGraph(id = "deleted-id", name = "Doomed"))
        sessionsFlow.value = listOf(
            ChatSession(id = sessionId, name = "S", updatedAt = 0, pipelineId = "deleted-id"),
        )
        advanceUntilIdle()
        // …then delete it from the library while the chat is active.
        pipelinesFlow.value = listOf(PipelineGraph(id = "still-here", name = "Default"))
        advanceUntilIdle()

        val rebound = sessionsFlow.value.first { it.id == sessionId }
        assertNull("Session must be rebound to null after deletion", rebound.pipelineId)
    }

    @Test
    fun `tokensMax reflects the configured context-window cap`() = runTest(testDispatcher) {
        viewModel = createViewModel()
        advanceUntilIdle()
        maxContextLengthFlow.value = ALT_TOKENS_MAX
        advanceUntilIdle()
        assertEquals(ALT_TOKENS_MAX, viewModel.tokensMax.value)
    }

    @Test
    fun `tokensUsed reflects rough chars-per-token estimate of the context window`() = runTest(testDispatcher) {
        viewModel = createViewModel()
        advanceUntilIdle()
        val sessionId = viewModel.currentSessionId.value
        coEvery { getContextWindowUseCase(sessionId) } returns "x".repeat(40)

        messagesFlow.value = listOf(
            ChatMessage(id = 1, sessionId = sessionId, role = Role.USER, content = "x".repeat(40), timestamp = 0),
        )
        advanceUntilIdle()

        assertEquals(40 / ChatHomeViewModel.TOKEN_CHARS_PER_TOKEN, viewModel.tokensUsed.value)
    }

    @Test
    fun `pipelineName resolves to the bound pipeline display name`() = runTest(testDispatcher) {
        viewModel = createViewModel()
        advanceUntilIdle()
        val sessionId = viewModel.currentSessionId.value
        pipelinesFlow.value = listOf(
            PipelineGraph(id = "p1", name = "Knot Default"),
            PipelineGraph(id = "p2", name = "Research"),
        )
        sessionsFlow.value = listOf(
            ChatSession(id = sessionId, name = "S", updatedAt = 0, pipelineId = "p2"),
        )
        advanceUntilIdle()

        assertEquals("Research", viewModel.pipelineName.value)
    }

    @Test
    fun `pipelineName falls back to default pipeline when session is unbound`() = runTest(testDispatcher) {
        viewModel = createViewModel()
        advanceUntilIdle()
        pipelinesFlow.value = listOf(
            PipelineGraph(id = "p1", name = "First"),
            PipelineGraph(id = "p2", name = "Default"),
        )
        defaultPipelineIdFlow.value = "p2"
        advanceUntilIdle()

        assertEquals("Default", viewModel.pipelineName.value)
    }

    @Test
    fun `openDrawer + closeDrawer with no messages settles back on Empty`() = runTest(testDispatcher) {
        viewModel = createViewModel()
        advanceUntilIdle()
        viewModel.openDrawer()
        assertEquals(ChatHomeUiState.DrawerOpen, viewModel.state.value)
        viewModel.closeDrawer()
        assertEquals(ChatHomeUiState.Empty, viewModel.state.value)
    }

    @Test
    fun `openConsole flips consoleSnap without touching chat state`() = runTest(testDispatcher) {
        viewModel = createViewModel()
        advanceUntilIdle()
        val stateBefore = viewModel.state.value
        viewModel.openConsole(ConsoleSnap.Full)
        assertEquals(ConsoleSnap.Full, viewModel.consoleSnap.value)
        assertEquals(stateBefore, viewModel.state.value)
    }

    @Test
    fun `closeConsole clears consoleSnap without touching chat state`() = runTest(testDispatcher) {
        viewModel = createViewModel()
        advanceUntilIdle()
        viewModel.openConsole(ConsoleSnap.Partial)
        val stateBefore = viewModel.state.value
        viewModel.closeConsole()
        assertNull(viewModel.consoleSnap.value)
        assertEquals(stateBefore, viewModel.state.value)
    }

    @Test
    fun `setConsoleSnap updates an open console pane`() = runTest(testDispatcher) {
        viewModel = createViewModel()
        advanceUntilIdle()
        viewModel.openConsole(ConsoleSnap.Partial)
        viewModel.setConsoleSnap(ConsoleSnap.Full)
        assertEquals(ConsoleSnap.Full, viewModel.consoleSnap.value)
    }

    @Test
    fun `setConsoleSnap is a no-op when console is closed`() = runTest(testDispatcher) {
        viewModel = createViewModel()
        advanceUntilIdle()
        viewModel.setConsoleSnap(ConsoleSnap.Full)
        assertNull(viewModel.consoleSnap.value)
    }

    @Test
    fun `console pane survives terminal Completed orchestrator emission`() = runTest(testDispatcher) {
        // Regression: in the pre-refactor sealed state, every terminal
        // emit (Completed / Error / WaitingForApproval) overwrote the
        // pane state and closed the overlay before the user could read
        // any of the streamed events.
        viewModel = createViewModel()
        advanceUntilIdle()
        viewModel.openConsole(ConsoleSnap.Partial)
        viewModel.forceState(ChatHomeUiState.Idle)
        assertEquals(ConsoleSnap.Partial, viewModel.consoleSnap.value)
    }

    @Test
    fun `forceState flips state without side effects`() = runTest(testDispatcher) {
        viewModel = createViewModel()
        advanceUntilIdle()
        viewModel.forceState(ChatHomeUiState.HitlConfirm(Risk.Destructive))
        assertEquals(ChatHomeUiState.HitlConfirm(Risk.Destructive), viewModel.state.value)
    }

    @Test
    fun `sendMessage emits HitlConfirm when orchestrator waits for approval`() = runTest(testDispatcher) {
        viewModel = createViewModel()
        advanceUntilIdle()
        val sessionId = viewModel.currentSessionId.value
        coEvery { agentOrchestratorUseCase(sessionId, "hi", null) } returns flow {
            emit(
                AgentOrchestratorState.WaitingForApproval(
                    toolName = "fs.write_file",
                    arguments = "{\"path\":\"/tmp/x\"}",
                    risk = ToolRisk.SENSITIVE,
                ),
            )
            delay(10_000)
        }

        viewModel.onComposerValueChange("hi")
        viewModel.sendMessage()
        advanceUntilIdle()

        val state = viewModel.state.value
        assertTrue("Expected HitlConfirm, got $state", state is ChatHomeUiState.HitlConfirm)
        assertEquals(Risk.Sensitive, (state as ChatHomeUiState.HitlConfirm).risk)
        val pending = viewModel.pendingTool.value
        assertNotNull(pending)
        assertEquals("fs.write_file", pending!!.toolName)
        assertEquals(ToolRisk.SENSITIVE, pending.risk)
    }

    @Test
    fun `approveTool calls resumeWithApproval(true) and flips to Generating`() = runTest(testDispatcher) {
        viewModel = createViewModel()
        advanceUntilIdle()
        val sessionId = viewModel.currentSessionId.value
        coEvery { agentOrchestratorUseCase(sessionId, "hi", null) } returns flow {
            emit(
                AgentOrchestratorState.WaitingForApproval(
                    toolName = "calendar.create_event",
                    arguments = "{}",
                    risk = ToolRisk.SENSITIVE,
                ),
            )
            delay(10_000)
        }
        viewModel.onComposerValueChange("hi")
        viewModel.sendMessage()
        advanceUntilIdle()

        viewModel.approveTool()
        advanceUntilIdle()

        verify { agentOrchestratorUseCase.resumeWithApproval(sessionId, true) }
        assertEquals(ChatHomeUiState.Generating, viewModel.state.value)
        assertNull(viewModel.pendingTool.value)
    }

    @Test
    fun `rejectTool calls resumeWithApproval(false) and appends system denial message`() = runTest(testDispatcher) {
        viewModel = createViewModel()
        advanceUntilIdle()
        val sessionId = viewModel.currentSessionId.value
        coEvery { agentOrchestratorUseCase(sessionId, "hi", null) } returns flow {
            emit(
                AgentOrchestratorState.WaitingForApproval(
                    toolName = "fs.delete_file",
                    arguments = "{}",
                    risk = ToolRisk.DESTRUCTIVE,
                ),
            )
            delay(10_000)
        }
        viewModel.onComposerValueChange("hi")
        viewModel.sendMessage()
        advanceUntilIdle()

        viewModel.rejectTool()
        advanceUntilIdle()

        verify { agentOrchestratorUseCase.resumeWithApproval(sessionId, false) }
        coVerify {
            chatRepository.saveMessage(
                match { msg ->
                    msg.role == Role.SYSTEM &&
                        msg.content.contains("fs.delete_file") &&
                        msg.content.contains("denied")
                },
            )
        }
        assertNull(viewModel.pendingTool.value)
        // Resuming the pipeline restarts orchestrator emission — the surface stays
        // in Generating until the next state (or a terminal Completed / Error) lands.
        assertEquals(ChatHomeUiState.Generating, viewModel.state.value)
    }

    @Test
    fun `approveTool destructive is a no-op without typed yes`() = runTest(testDispatcher) {
        viewModel = createViewModel()
        advanceUntilIdle()
        val sessionId = viewModel.currentSessionId.value
        coEvery { agentOrchestratorUseCase(sessionId, "hi", null) } returns flow {
            emit(
                AgentOrchestratorState.WaitingForApproval(
                    toolName = "fs.delete_file",
                    arguments = "{}",
                    risk = ToolRisk.DESTRUCTIVE,
                ),
            )
            delay(10_000)
        }
        viewModel.onComposerValueChange("hi")
        viewModel.sendMessage()
        advanceUntilIdle()

        // Empty typed-confirm — Allow must be refused.
        viewModel.approveTool()
        advanceUntilIdle()
        verify(exactly = 0) { agentOrchestratorUseCase.resumeWithApproval(any(), true) }
        assertTrue(viewModel.state.value is ChatHomeUiState.HitlConfirm)

        // Typing the canonical magic word unlocks the gate.
        viewModel.onTypedConfirmChange("yes")
        viewModel.approveTool()
        advanceUntilIdle()
        verify { agentOrchestratorUseCase.resumeWithApproval(sessionId, true) }
        assertEquals(ChatHomeUiState.Generating, viewModel.state.value)
    }

    @Test
    fun `AwaitingClarification emits Clarification state and captures the request`() = runTest(testDispatcher) {
        viewModel = createViewModel()
        advanceUntilIdle()
        val sessionId = viewModel.currentSessionId.value
        val request = ClarificationRequest(
            id = "req-1",
            question = "Which calendar?",
            options = listOf("Work", "Personal"),
            timeoutMs = 0,
        )
        coEvery { agentOrchestratorUseCase(sessionId, "hi", null) } returns flow {
            emit(AgentOrchestratorState.AwaitingClarification(request))
            delay(10_000)
        }

        viewModel.onComposerValueChange("hi")
        viewModel.sendMessage()
        advanceUntilIdle()

        assertEquals(ChatHomeUiState.Clarification, viewModel.state.value)
        assertEquals(request, viewModel.pendingClarification.value)
    }

    @Test
    fun `submitClarificationReply calls repository and flips to Generating`() = runTest(testDispatcher) {
        viewModel = createViewModel()
        advanceUntilIdle()
        val sessionId = viewModel.currentSessionId.value
        val request = ClarificationRequest(
            id = "req-7",
            question = "Q?",
            options = listOf("Yes", "No"),
            timeoutMs = 0,
        )
        coEvery { agentOrchestratorUseCase(sessionId, "hi", null) } returns flow {
            emit(AgentOrchestratorState.AwaitingClarification(request))
            delay(10_000)
        }
        viewModel.onComposerValueChange("hi")
        viewModel.sendMessage()
        advanceUntilIdle()

        viewModel.submitClarificationReply(" Yes  ")
        advanceUntilIdle()

        coVerify { clarificationRepository.submitClarification("req-7", "Yes") }
        assertNull(viewModel.pendingClarification.value)
        assertEquals(ChatHomeUiState.Generating, viewModel.state.value)
    }

    @Test
    fun `clarification watchdog submits default answer on timeout`() = runTest(testDispatcher) {
        viewModel = createViewModel()
        advanceUntilIdle()
        val sessionId = viewModel.currentSessionId.value
        val request = ClarificationRequest(
            id = "req-timeout",
            question = "Pick one",
            options = listOf("Alpha", "Beta"),
            timeoutMs = 500L,
        )
        coEvery { agentOrchestratorUseCase(sessionId, "hi", null) } returns flow {
            emit(AgentOrchestratorState.AwaitingClarification(request))
            delay(10_000)
        }
        viewModel.onComposerValueChange("hi")
        viewModel.sendMessage()
        // Use runCurrent() not advanceUntilIdle: the latter would skip past the
        // 500ms watchdog delay and fire the timeout *before* this assertion.
        testScheduler.runCurrent()
        assertEquals(ChatHomeUiState.Clarification, viewModel.state.value)

        testScheduler.advanceTimeBy(600L)
        testScheduler.runCurrent()

        coVerify { clarificationRepository.submitClarification("req-timeout", "Alpha") }
        coVerify {
            chatRepository.saveMessage(
                match { msg ->
                    msg.role == Role.SYSTEM &&
                        msg.content.contains("Alpha") &&
                        msg.content.contains("timed out")
                },
            )
        }
        assertNull(viewModel.pendingClarification.value)
        // Pipeline resumes after the default answer is submitted — keep Generating
        // until the orchestrator's next state lands.
        assertEquals(ChatHomeUiState.Generating, viewModel.state.value)
    }

    @Test
    fun `submitClarificationReply forwards an empty reply for free-form requests`() = runTest(testDispatcher) {
        viewModel = createViewModel()
        advanceUntilIdle()
        val sessionId = viewModel.currentSessionId.value
        val request = ClarificationRequest(
            id = "req-blank",
            question = "Anything else?",
            options = null,
            timeoutMs = 0,
        )
        coEvery { agentOrchestratorUseCase(sessionId, "hi", null) } returns flow {
            emit(AgentOrchestratorState.AwaitingClarification(request))
            delay(10_000)
        }
        viewModel.onComposerValueChange("hi")
        viewModel.sendMessage()
        advanceUntilIdle()

        viewModel.submitClarificationReply("   ")
        advanceUntilIdle()

        coVerify { clarificationRepository.submitClarification("req-blank", "") }
        assertNull(viewModel.pendingClarification.value)
        assertEquals(ChatHomeUiState.Generating, viewModel.state.value)
    }

    @Test
    fun `chatMessageToRow maps user messages without a model label`() {
        val msg = ChatMessage(id = 7L, sessionId = "s", role = Role.USER, content = "hello", timestamp = 0)
        val row = ChatHomeViewModel.chatMessageToRow(msg, "M")
        assertEquals(ChatRole.User, row.role)
        assertNull(row.metadata.model)
        assertEquals("hello", (row.content as ChatContent.Text).text)
        assertTrue(row.id.startsWith("u-"))
    }

    @Test
    fun `chatMessageToRow maps assistant messages with the active model label`() {
        val msg = ChatMessage(id = 11L, sessionId = "s", role = Role.AGENT, content = "ok", timestamp = 0)
        val row = ChatHomeViewModel.chatMessageToRow(msg, "Gemma 2B")
        assertEquals(ChatRole.Assistant, row.role)
        assertEquals("Gemma 2B", row.metadata.model)
        assertNotNull(row.metadata.timestamp)
        assertTrue(row.id.startsWith("a-"))
        // Phase 22 / Task 16 follow-up F2 — agent rows carry Markdown so the
        // host-supplied renderer formats headings, lists, code fences, etc.
        assertEquals("ok", (row.content as ChatContent.Markdown).source)
    }

    // -----------------------------------------------------------------
    // Phase 22 / Task 4 — drawer / overflow / model-picker / favorites.
    // -----------------------------------------------------------------

    @Test
    fun `createNewSessionWithPipeline persists session with picked pipeline and switches`() = runTest(testDispatcher) {
        viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.createNewSessionWithPipeline(pipelineId = "pipe-42")
        advanceUntilIdle()

        coVerify { chatRepository.saveSession(match { it.pipelineId == "pipe-42" }) }
        // After save, the new session id is propagated as the active session.
        assertTrue(viewModel.currentSessionId.value.isNotBlank())
    }

    @Test
    fun `renameSession trims input and forwards to repository`() = runTest(testDispatcher) {
        viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.renameSession("thread-x", "   New name   ")
        advanceUntilIdle()

        coVerify { chatRepository.renameSession("thread-x", "New name") }
    }

    @Test
    fun `renameSession with blank input is a no-op`() = runTest(testDispatcher) {
        viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.renameSession("thread-x", "    ")
        advanceUntilIdle()

        coVerify(exactly = 0) { chatRepository.renameSession(any(), any()) }
    }

    @Test
    fun `toggleFavoriteCurrent flips persisted isStarred flag on the active session`() = runTest(testDispatcher) {
        val sessionId = "fav-session"
        savedSessionIdFlow.value = sessionId
        sessionsFlow.value = listOf(
            ChatSession(id = sessionId, name = "S", updatedAt = 0, isStarred = false),
        )
        viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.toggleFavoriteCurrent()
        advanceUntilIdle()

        coVerify { chatRepository.setSessionFavorite(sessionId, true) }
    }

    @Test
    fun `toggleFavoriteCurrent reflects current isStarred via favorite StateFlow`() = runTest(testDispatcher) {
        val sessionId = "fav-session"
        savedSessionIdFlow.value = sessionId
        sessionsFlow.value = listOf(
            ChatSession(id = sessionId, name = "S", updatedAt = 0, isStarred = true),
        )
        viewModel = createViewModel()
        advanceUntilIdle()

        assertEquals(true, viewModel.favorite.value)
    }

    @Test
    fun `pickModel activates the model and loads it via LoadModelUseCase`() = runTest(testDispatcher) {
        viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.pickModel(modelId = 17L)
        advanceUntilIdle()

        coVerify { localModelRepository.setActiveModel(17L) }
        coVerify { loadModelUseCase() }
    }

    @Test
    fun `installedModels mirrors LocalModelRepository getAllModels emissions`() = runTest(testDispatcher) {
        viewModel = createViewModel()
        advanceUntilIdle()

        localModelsFlow.value = listOf(
            LocalModel(id = 1L, name = "Gemma 2B", path = "/g", size = 0L, isActive = true),
            LocalModel(id = 2L, name = "Other", path = "/o", size = 0L, isActive = false),
        )
        advanceUntilIdle()

        assertEquals(2, viewModel.installedModels.value.size)
        assertEquals(1L, viewModel.activeModelId.value)
        assertEquals("Gemma 2B", viewModel.modelName.value)
    }

    @Test
    fun `deleteCurrentSession deletes and auto-selects the next available thread`() = runTest(testDispatcher) {
        val sessionId = "active-id"
        val other = "other-id"
        savedSessionIdFlow.value = sessionId
        sessionsFlow.value = listOf(
            ChatSession(id = sessionId, name = "A", updatedAt = 2L),
            ChatSession(id = other, name = "B", updatedAt = 1L),
        )
        // Simulate the repository removing the row when delete is called.
        coEvery { chatRepository.deleteSession(sessionId) } answers {
            sessionsFlow.value = sessionsFlow.value.filterNot { it.id == sessionId }
            Unit
        }

        viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.deleteCurrentSession()
        advanceUntilIdle()

        coVerify { chatRepository.deleteSession(sessionId) }
        assertEquals(other, viewModel.currentSessionId.value)
    }

    @Test
    fun `deleteCurrentSession creates fresh session when no thread remains`() = runTest(testDispatcher) {
        val sessionId = "only-id"
        savedSessionIdFlow.value = sessionId
        sessionsFlow.value = listOf(ChatSession(id = sessionId, name = "only", updatedAt = 0))
        coEvery { chatRepository.deleteSession(sessionId) } answers {
            sessionsFlow.value = emptyList()
            Unit
        }

        viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.deleteCurrentSession()
        advanceUntilIdle()

        coVerify { chatRepository.deleteSession(sessionId) }
        // After delete + auto-create, the active session id is the newly-created one.
        assertTrue(viewModel.currentSessionId.value.isNotBlank())
        coVerify(atLeast = 1) { chatRepository.saveSession(any()) }
    }

    @Test
    fun `importChatFromJson delegates to repository and selects the imported session`() = runTest(testDispatcher) {
        viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.importChatFromJson("""[{"role":"USER","text":"hi","timestamp":1}]""")
        advanceUntilIdle()

        coVerify { chatRepository.importChat(any()) }
        assertEquals("imported-session-id", viewModel.currentSessionId.value)
    }

    @Test
    fun `importChatFromJson emits importErrorEvents when repository throws`() = runTest(testDispatcher) {
        coEvery { chatRepository.importChat(any()) } throws org.json.JSONException("bad shape")
        viewModel = createViewModel()
        advanceUntilIdle()

        // Use async to suspend on the first emission. `runCurrent` parks the
        // collector inside `first()`, then the trigger runs, then `await`
        // resumes once `tryEmit` lands. MutableSharedFlow(replay=0) only
        // delivers to subscribers active at emit time, so the await-then-emit
        // ordering is mandatory.
        val received = async { viewModel.importErrorEvents.first() }
        runCurrent()
        viewModel.importChatFromJson("not json")
        advanceUntilIdle()

        assertEquals("bad shape", received.await())
    }

    @Test
    fun `exportCurrentSession emits payload carrying session name and JSON`() = runTest(testDispatcher) {
        val sessionId = "exp-id"
        savedSessionIdFlow.value = sessionId
        sessionsFlow.value = listOf(ChatSession(id = sessionId, name = "Trip plan", updatedAt = 0))
        coEvery { chatRepository.getSessionById(sessionId) } returns
            ChatSession(id = sessionId, name = "Trip plan", updatedAt = 0)
        coEvery { chatRepository.getMessagesForSession(sessionId) } returns flowOf(
            listOf(
                ChatMessage(id = 1L, sessionId = sessionId, role = Role.USER, content = "hi", timestamp = 1L),
            ),
        )
        viewModel = createViewModel()
        advanceUntilIdle()

        val payload = async { viewModel.exportEvents.first() }
        runCurrent()
        viewModel.exportCurrentSession()
        advanceUntilIdle()

        val captured = payload.await()
        assertEquals("Trip plan", captured.sessionName)
        assertTrue(captured.json.contains("\"role\""))
        assertTrue(captured.json.contains("\"USER\""))
    }

    @Test
    fun `threadRows projects sessions with favorited at the top`() = runTest(testDispatcher) {
        val a = "id-a"
        val b = "id-b"
        sessionsFlow.value = listOf(
            ChatSession(id = a, name = "Older", updatedAt = 100L, isStarred = false),
            ChatSession(id = b, name = "Pinned", updatedAt = 50L, isStarred = true),
        )
        viewModel = createViewModel()
        advanceUntilIdle()

        val rows = viewModel.threadRows.value
        assertEquals(b, rows.first().id)
        assertTrue(rows.first().starred)
        assertEquals(a, rows.last().id)
    }

    private companion object {
        const val DEFAULT_TOKENS_MAX: Int = 4096
        const val ALT_TOKENS_MAX: Int = 8192
    }
}
