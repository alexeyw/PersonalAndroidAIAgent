package ai.agent.android.presentation.ui.chat.home

import ai.agent.android.domain.engine.LlmInferenceEngine
import ai.agent.android.domain.models.AgentOrchestratorState
import ai.agent.android.domain.models.ChatMessage
import ai.agent.android.domain.models.ChatSession
import ai.agent.android.domain.models.ClarificationRequest
import ai.agent.android.domain.models.PipelineGraph
import ai.agent.android.domain.models.Role
import ai.agent.android.domain.models.ToolRisk
import ai.agent.android.domain.repositories.ChatRepository
import ai.agent.android.domain.repositories.ClarificationRepository
import ai.agent.android.domain.repositories.PipelineRepository
import ai.agent.android.domain.repositories.SettingsRepository
import ai.agent.android.domain.usecases.AgentOrchestratorUseCase
import ai.agent.android.domain.usecases.GetContextWindowUseCase
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
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
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

    private lateinit var sessionsFlow: MutableStateFlow<List<ChatSession>>
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

        sessionsFlow = MutableStateFlow(emptyList())
        pipelinesFlow = MutableStateFlow(emptyList())
        messagesFlow = MutableStateFlow(emptyList())
        savedSessionIdFlow = MutableStateFlow(null)
        defaultPipelineIdFlow = MutableStateFlow(null)
        maxContextLengthFlow = MutableStateFlow(DEFAULT_TOKENS_MAX)
        consolePreferredConsoleTabNameFlow = MutableStateFlow("Logs")

        every { llmInferenceEngine.isInitialized } returns true
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
        assertEquals("hello", (rows[1].content as ChatContent.Text).text)
        assertEquals(ChatHomeUiState.Idle, viewModel.state.value)
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
            ChatHomeViewModel.LOAD_MODEL_FIRST_MESSAGE,
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
    fun `openConsole sets ConsoleExpanded with the supplied snap`() = runTest(testDispatcher) {
        viewModel = createViewModel()
        advanceUntilIdle()
        viewModel.openConsole(ConsoleSnap.Full)
        assertEquals(ChatHomeUiState.ConsoleExpanded(ConsoleSnap.Full), viewModel.state.value)
    }

    @Test
    fun `closeConsole settles on Empty when there are no messages`() = runTest(testDispatcher) {
        viewModel = createViewModel()
        advanceUntilIdle()
        viewModel.openConsole(ConsoleSnap.Partial)
        viewModel.closeConsole()
        assertEquals(ChatHomeUiState.Empty, viewModel.state.value)
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
    }

    private companion object {
        const val DEFAULT_TOKENS_MAX: Int = 4096
        const val ALT_TOKENS_MAX: Int = 8192
    }
}
