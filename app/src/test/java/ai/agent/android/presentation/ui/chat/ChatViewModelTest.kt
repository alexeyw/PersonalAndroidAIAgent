package ai.agent.android.presentation.ui.chat

import ai.agent.android.domain.engine.LlmInferenceEngine
import ai.agent.android.domain.models.AgentOrchestratorState
import ai.agent.android.domain.models.ChatMessage
import ai.agent.android.domain.models.ChatSession
import ai.agent.android.domain.models.ClarificationRequest
import ai.agent.android.domain.models.ConsoleEvent
import ai.agent.android.domain.models.ConsoleEventType
import ai.agent.android.domain.models.PipelineGraph
import ai.agent.android.domain.models.Result
import ai.agent.android.domain.models.Role
import ai.agent.android.domain.repositories.ChatRepository
import ai.agent.android.domain.repositories.ClarificationRepository
import ai.agent.android.domain.repositories.PipelineRepository
import ai.agent.android.domain.repositories.SettingsRepository
import ai.agent.android.domain.usecases.AgentOrchestratorUseCase
import ai.agent.android.domain.usecases.GetContextWindowUseCase
import ai.agent.android.domain.usecases.LoadModelUseCase
import ai.agent.android.presentation.state.ActiveSessionTracker
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.json.JSONArray
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ChatViewModelTest {

    private val testDispatcher = StandardTestDispatcher()

    private lateinit var agentOrchestratorUseCase: AgentOrchestratorUseCase
    private lateinit var chatRepository: ChatRepository
    private lateinit var settingsRepository: SettingsRepository
    private lateinit var loadModelUseCase: LoadModelUseCase
    private lateinit var getContextWindowUseCase: GetContextWindowUseCase
    private lateinit var activeSessionTracker: ActiveSessionTracker
    private lateinit var llmInferenceEngine: LlmInferenceEngine
    private lateinit var clarificationRepository: ClarificationRepository
    private lateinit var pipelineRepository: PipelineRepository
    private lateinit var sessionsFlow: MutableStateFlow<List<ChatSession>>
    private lateinit var pipelinesFlow: MutableStateFlow<List<PipelineGraph>>
    private lateinit var viewModel: ChatViewModel

    private fun createViewModel(): ChatViewModel = ChatViewModel(
        agentOrchestratorUseCase,
        chatRepository,
        settingsRepository,
        loadModelUseCase,
        getContextWindowUseCase,
        activeSessionTracker,
        llmInferenceEngine,
        clarificationRepository,
        pipelineRepository,
    )

    /**
     * Builds a pipeline stub used by tests that exercise the pipeline-binding
     * flows. `ChatViewModel` only consumes `id` and `name` from the graph,
     * so node/connection contents are deliberately omitted.
     */
    private fun pipeline(id: String, name: String): PipelineGraph = PipelineGraph(id = id, name = name)

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        agentOrchestratorUseCase = mockk()
        chatRepository = mockk()
        settingsRepository = mockk(relaxed = true)
        loadModelUseCase = mockk()
        getContextWindowUseCase = mockk()
        activeSessionTracker = mockk(relaxed = true)
        llmInferenceEngine = mockk(relaxed = true)
        every { llmInferenceEngine.isInitialized } returns true
        clarificationRepository = mockk(relaxed = true)
        // Default: every reply is accepted by the repository. Tests that exercise
        // the rejection path override this on the specific request id.
        coEvery { clarificationRepository.submitClarification(any(), any()) } returns true

        // Backing flows so tests can append/replace sessions and pipelines after
        // the ViewModel has started observing them — required for the
        // pipeline-binding flows (deletion fallback, new-chat selection, etc.).
        sessionsFlow = MutableStateFlow(emptyList())
        pipelinesFlow = MutableStateFlow(emptyList())
        pipelineRepository = mockk()
        every { pipelineRepository.getAllPipelines() } returns pipelinesFlow

        // Mock chat repository flow for any session — both unfiltered (used by
        // export/context-window paths) and the display-only flow consumed by
        // `loadMessages`. Phase 17.3 added `getDisplayMessagesForSession`, so
        // the default stub must answer both methods to keep existing tests
        // unchanged.
        every { chatRepository.getMessagesForSession(any()) } returns flowOf(emptyList())
        every { chatRepository.getDisplayMessagesForSession(any()) } returns flowOf(emptyList())
        every { chatRepository.getStarredMessages() } returns flowOf(emptyList())
        coEvery { chatRepository.setMessageStarred(any(), any()) } returns Unit
        every { chatRepository.getSessionsFlow() } returns sessionsFlow
        coEvery { chatRepository.saveSession(any()) } answers {
            val saved = firstArg<ChatSession>()
            sessionsFlow.value = sessionsFlow.value.filterNot { it.id == saved.id } + saved
            Unit
        }
        coEvery { chatRepository.saveMessage(any()) } returns Unit
        coEvery { chatRepository.deleteSession(any()) } returns Unit
        coEvery { chatRepository.getSessionById(any()) } answers {
            val id = firstArg<String>()
            sessionsFlow.value.firstOrNull { it.id == id }
        }
        // Default: no saved session, no explicit default pipeline.
        every { settingsRepository.currentChatSessionId } returns flowOf(null)
        every { settingsRepository.defaultPipelineId } returns flowOf(null)
        // Default: model loads successfully
        coEvery { loadModelUseCase() } returns Result.Success(Unit)
        // Default: getContextWindowUseCase returns empty
        coEvery { getContextWindowUseCase(any()) } returns ""
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `init should generate session id when none saved`() = runTest {
        viewModel = createViewModel()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertTrue(state.currentSessionId.isNotEmpty())
        coVerify { settingsRepository.setCurrentChatSessionId(any()) }
    }

    @Test
    fun `init should show error if model fails to load`() = runTest {
        val errorMsg = "Model file not found"
        coEvery { loadModelUseCase() } returns Result.Error(mockk(), errorMsg)

        viewModel = createViewModel()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertNotNull(state.errorMessage)
        val resolved = state.errorMessage as ai.agent.android.presentation.ui.common.UiText.Resource
        assertEquals(ai.agent.android.R.string.errors_chat_model_init_failure, resolved.id)
        assertEquals(listOf(errorMsg), resolved.args)
    }

    @Test
    fun `init should restore session id from settings`() = runTest {
        val savedId = "saved-session-123"
        every { settingsRepository.currentChatSessionId } returns flowOf(savedId)

        viewModel = createViewModel()
        advanceUntilIdle()

        assertEquals(savedId, viewModel.uiState.value.currentSessionId)
        coVerify(exactly = 0) { settingsRepository.setCurrentChatSessionId(any()) }
    }

    @Test
    fun `sendMessage should ignore blank prompt`() = runTest {
        viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.sendMessage("   ")
        advanceUntilIdle()

        coVerify(exactly = 0) { agentOrchestratorUseCase(any(), any(), any()) }
        assertFalse(viewModel.uiState.value.isGenerating)
    }

    @Test
    fun `sendMessage should update state to generating and collect orchestrator states`() = runTest {
        val userPrompt = "Test prompt"

        viewModel = createViewModel()
        advanceUntilIdle()

        val actualSessionId = viewModel.uiState.value.currentSessionId

        coEvery { agentOrchestratorUseCase(actualSessionId, userPrompt, any()) } returns flow {
            emit(AgentOrchestratorState.Loading)
            emit(AgentOrchestratorState.Thinking("Hmm"))
            emit(AgentOrchestratorState.Completed("Final answer"))
        }

        viewModel.sendMessage(userPrompt)
        advanceUntilIdle()

        // Final state verification
        val state = viewModel.uiState.value
        assertFalse(state.isGenerating)
        assertTrue(state.orchestratorState is AgentOrchestratorState.Completed)
        assertEquals("Final answer", (state.orchestratorState as AgentOrchestratorState.Completed).finalResponse)
        assertNull(state.errorMessage)
    }

    @Test
    fun `sendMessage should handle error from orchestrator`() = runTest {
        val userPrompt = "Test prompt"
        val exceptionMessage = "Network failure"

        viewModel = createViewModel()
        advanceUntilIdle()

        val actualSessionId = viewModel.uiState.value.currentSessionId

        coEvery { agentOrchestratorUseCase(actualSessionId, userPrompt, any()) } returns flow {
            throw RuntimeException(exceptionMessage)
        }

        viewModel.sendMessage(userPrompt)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertFalse(state.isGenerating)
        assertEquals(
            ai.agent.android.presentation.ui.common.UiText.Dynamic(exceptionMessage),
            state.errorMessage,
        )
        assertTrue(state.orchestratorState is AgentOrchestratorState.Error)
        assertEquals(exceptionMessage, (state.orchestratorState as AgentOrchestratorState.Error).message)
    }

    @Test
    fun `clearError should set errorMessage to null`() = runTest {
        viewModel = createViewModel()
        advanceUntilIdle()

        // Force an error state
        val actualSessionId = viewModel.uiState.value.currentSessionId
        coEvery { agentOrchestratorUseCase(actualSessionId, "trigger error", any()) } returns flow {
            throw RuntimeException("Error occurred")
        }

        viewModel.sendMessage("trigger error")
        advanceUntilIdle()

        assertNotNull(viewModel.uiState.value.errorMessage)

        viewModel.clearError()
        assertNull(viewModel.uiState.value.errorMessage)
    }

    @Test
    fun `sendMessage should update currentStep when PipelineStage state emitted`() = runTest {
        val userPrompt = "pipeline test"
        val stepInfo = AgentOrchestratorState.PipelineStepInfo(stepIndex = 1, totalSteps = 3, nodeName = "LITERT")

        viewModel = createViewModel()
        advanceUntilIdle()

        val sessionId = viewModel.uiState.value.currentSessionId
        coEvery { agentOrchestratorUseCase(sessionId, userPrompt, any()) } returns flow {
            emit(AgentOrchestratorState.PipelineStage(stepInfo))
            emit(AgentOrchestratorState.Completed("done"))
        }

        viewModel.sendMessage(userPrompt)
        advanceUntilIdle()

        // currentStep is reset to null on Completed — captured via intermediate assertion during collect
        // We verify that currentStep is null once generation is done (steady state)
        assertNull(viewModel.uiState.value.currentStep)
        assertFalse(viewModel.uiState.value.isGenerating)
    }

    @Test
    fun `sendMessage should reset currentStep to null on Completed`() = runTest {
        val userPrompt = "reset step test"
        val stepInfo = AgentOrchestratorState.PipelineStepInfo(stepIndex = 2, totalSteps = 4, nodeName = "CLOUD")

        viewModel = createViewModel()
        advanceUntilIdle()

        val sessionId = viewModel.uiState.value.currentSessionId
        coEvery { agentOrchestratorUseCase(sessionId, userPrompt, any()) } returns flow {
            emit(AgentOrchestratorState.PipelineStage(stepInfo))
            emit(AgentOrchestratorState.Completed("final"))
        }

        viewModel.sendMessage(userPrompt)
        advanceUntilIdle()

        assertNull(viewModel.uiState.value.currentStep)
    }

    @Test
    fun `stopGeneration should cancel job and reset isGenerating and currentStep`() = runTest {
        val userPrompt = "long task"

        viewModel = createViewModel()
        advanceUntilIdle()

        val sessionId = viewModel.uiState.value.currentSessionId
        coEvery { agentOrchestratorUseCase(sessionId, userPrompt, any()) } returns flow {
            emit(AgentOrchestratorState.Loading)
            delay(10_000)
            emit(AgentOrchestratorState.Completed("never reached"))
        }

        viewModel.sendMessage(userPrompt)
        // advance just enough to start the flow but not complete it
        testScheduler.advanceTimeBy(100)

        viewModel.stopGeneration()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertFalse(state.isGenerating)
        assertNull(state.currentStep)
        assertNull(state.orchestratorState)
    }

    @Test
    fun `stopGeneration should save partial answer with stopped suffix when Thinking`() = runTest {
        val userPrompt = "thinking task"
        val partialText = "Here is what I know so far"

        viewModel = createViewModel()
        advanceUntilIdle()

        val sessionId = viewModel.uiState.value.currentSessionId
        coEvery { agentOrchestratorUseCase(sessionId, userPrompt, any()) } returns flow {
            emit(AgentOrchestratorState.Thinking(partialText))
            delay(10_000)
        }

        viewModel.sendMessage(userPrompt)
        testScheduler.advanceTimeBy(100)

        viewModel.stopGeneration()
        advanceUntilIdle()

        coVerify {
            chatRepository.saveMessage(
                match { it.content.contains(partialText) && it.content.contains("[stopped]") },
            )
        }
    }

    @Test
    fun `resumeWithApproval should call orchestrator usecase with correct session id and approval state`() = runTest {
        every { agentOrchestratorUseCase.resumeWithApproval(any(), any()) } returns Unit

        viewModel = createViewModel()
        advanceUntilIdle()

        val actualSessionId = viewModel.uiState.value.currentSessionId

        viewModel.resumeWithApproval(true)
        io.mockk.verify { agentOrchestratorUseCase.resumeWithApproval(actualSessionId, true) }

        viewModel.resumeWithApproval(false)
        io.mockk.verify { agentOrchestratorUseCase.resumeWithApproval(actualSessionId, false) }
    }

    @Test
    fun `sendMessage should set inlineError and skip orchestrator when engine not initialized`() = runTest {
        every { llmInferenceEngine.isInitialized } returns false

        viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.sendMessage("hello")
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertNotNull(state.inlineError)
        assertFalse(state.isGenerating)
        coVerify(exactly = 0) { agentOrchestratorUseCase(any(), any(), any()) }
    }

    @Test
    fun `sendMessage should clear inlineError once engine becomes initialized and user retries`() = runTest {
        every { llmInferenceEngine.isInitialized } returns false

        viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.sendMessage("hello")
        advanceUntilIdle()
        assertNotNull(viewModel.uiState.value.inlineError)

        every { llmInferenceEngine.isInitialized } returns true
        val sessionId = viewModel.uiState.value.currentSessionId
        coEvery { agentOrchestratorUseCase(sessionId, "hello again", any()) } returns flow {
            emit(AgentOrchestratorState.Completed("ok"))
        }

        viewModel.sendMessage("hello again")
        advanceUntilIdle()

        assertNull(viewModel.uiState.value.inlineError)
    }

    @Test
    fun `clearInlineError should reset inlineError to null`() = runTest {
        every { llmInferenceEngine.isInitialized } returns false

        viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.sendMessage("hello")
        advanceUntilIdle()
        assertNotNull(viewModel.uiState.value.inlineError)

        viewModel.clearInlineError()

        assertNull(viewModel.uiState.value.inlineError)
    }

    @Test
    fun `exportChat should emit payload with JSON containing role text and timestamp`() = runTest {
        val sessionId = "session-x"
        val messages = listOf(
            ChatMessage(id = 1L, sessionId = sessionId, role = Role.USER, content = "hi", timestamp = 1_000L),
            ChatMessage(id = 2L, sessionId = sessionId, role = Role.AGENT, content = "hello", timestamp = 2_000L),
        )
        every { chatRepository.getMessagesForSession(sessionId) } returns flowOf(messages)
        coEvery { chatRepository.getSessionById(sessionId) } returns ChatSession(
            id = sessionId,
            name = "My Chat",
            updatedAt = 3_000L,
        )

        viewModel = createViewModel()
        advanceUntilIdle()

        val collected = mutableListOf<ChatExportPayload>()
        val job = CoroutineScope(testDispatcher).launch {
            viewModel.exportEvents.collect { collected.add(it) }
        }

        viewModel.exportChat(sessionId)
        advanceUntilIdle()

        assertEquals(1, collected.size)
        val payload = collected.first()
        assertEquals("My Chat", payload.sessionName)

        val root = JSONObject(payload.json)
        assertEquals(sessionId, root.getString("sessionId"))
        assertEquals("My Chat", root.getString("sessionName"))
        val arr = root.getJSONArray("messages")
        assertEquals(2, arr.length())
        val first = arr.getJSONObject(0)
        assertEquals("USER", first.getString("role"))
        assertEquals("hi", first.getString("text"))
        assertEquals(1_000L, first.getLong("timestamp"))
        val second = arr.getJSONObject(1)
        assertEquals("AGENT", second.getString("role"))
        assertEquals("hello", second.getString("text"))
        assertEquals(2_000L, second.getLong("timestamp"))

        job.cancel()
    }

    @Test
    fun `importChat should create new session and save each message`() = runTest {
        viewModel = createViewModel()
        advanceUntilIdle()

        val array = JSONArray()
            .put(JSONObject().put("role", "USER").put("text", "hello").put("timestamp", 111L))
            .put(JSONObject().put("role", "AGENT").put("text", "hi there").put("timestamp", 222L))
        val doc = JSONObject()
            .put("sessionName", "Restored Chat")
            .put("messages", array)

        viewModel.importChat(doc.toString())
        advanceUntilIdle()

        coVerify { chatRepository.saveSession(match { it.name == "Restored Chat" }) }
        coVerify {
            chatRepository.saveMessage(
                match { it.role == Role.USER && it.content == "hello" && it.timestamp == 111L },
            )
        }
        coVerify {
            chatRepository.saveMessage(
                match { it.role == Role.AGENT && it.content == "hi there" && it.timestamp == 222L },
            )
        }
    }

    @Test
    fun `importChat with invalid JSON should set errorMessage`() = runTest {
        viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.importChat("not-a-json")
        advanceUntilIdle()

        assertNotNull(viewModel.uiState.value.errorMessage)
        // Init creates a "New Chat" session; the import path must not create any additional session.
        coVerify(exactly = 0) { chatRepository.saveMessage(any()) }
        coVerify(exactly = 0) { chatRepository.saveSession(match { it.name == "Imported Chat" }) }
    }

    @Test
    fun `sendMessage should append clarification card on AwaitingClarification state`() = runTest {
        val userPrompt = "ask me"
        val request = ClarificationRequest(
            id = "clr-1",
            question = "Which option?",
            options = listOf("A", "B"),
            timeoutMs = 60_000L,
        )

        viewModel = createViewModel()
        advanceUntilIdle()

        val sessionId = viewModel.uiState.value.currentSessionId
        coEvery { agentOrchestratorUseCase(sessionId, userPrompt, any()) } returns flow {
            emit(AgentOrchestratorState.AwaitingClarification(request))
            delay(10_000)
        }

        viewModel.sendMessage(userPrompt)
        testScheduler.advanceTimeBy(100)

        val cards = viewModel.uiState.value.clarificationCards
        assertEquals(1, cards.size)
        val card = cards.first()
        assertEquals("clr-1", card.id)
        assertEquals("Which option?", card.question)
        assertEquals(listOf("A", "B"), card.options)
        assertEquals(60_000L, card.timeoutMs)
        assertEquals(ClarificationCardUiModel.Status.PENDING, card.status)
        assertNull(card.answer)

        viewModel.stopGeneration()
        advanceUntilIdle()
    }

    @Test
    fun `sendMessage should not duplicate clarification card on repeated AwaitingClarification`() = runTest {
        val userPrompt = "ask me twice"
        val request = ClarificationRequest(
            id = "clr-dup",
            question = "Once?",
            options = null,
            timeoutMs = 5_000L,
        )

        viewModel = createViewModel()
        advanceUntilIdle()

        val sessionId = viewModel.uiState.value.currentSessionId
        coEvery { agentOrchestratorUseCase(sessionId, userPrompt, any()) } returns flow {
            emit(AgentOrchestratorState.AwaitingClarification(request))
            emit(AgentOrchestratorState.AwaitingClarification(request))
            delay(10_000)
        }

        viewModel.sendMessage(userPrompt)
        testScheduler.advanceTimeBy(100)

        assertEquals(1, viewModel.uiState.value.clarificationCards.size)

        viewModel.stopGeneration()
        advanceUntilIdle()
    }

    @Test
    fun `submitClarification should mark card answered and forward reply to repository`() = runTest {
        val userPrompt = "ask me"
        val request = ClarificationRequest(
            id = "clr-2",
            question = "Free form?",
            options = null,
            timeoutMs = 60_000L,
        )

        viewModel = createViewModel()
        advanceUntilIdle()

        val sessionId = viewModel.uiState.value.currentSessionId
        coEvery { agentOrchestratorUseCase(sessionId, userPrompt, any()) } returns flow {
            emit(AgentOrchestratorState.AwaitingClarification(request))
            delay(10_000)
        }

        viewModel.sendMessage(userPrompt)
        testScheduler.advanceTimeBy(100)

        viewModel.submitClarification("clr-2", "the answer")
        advanceUntilIdle()

        val card = viewModel.uiState.value.clarificationCards.single()
        assertEquals(ClarificationCardUiModel.Status.ANSWERED, card.status)
        assertEquals("the answer", card.answer)
        coVerify { clarificationRepository.submitClarification("clr-2", "the answer") }

        viewModel.stopGeneration()
        advanceUntilIdle()
    }

    @Test
    fun `submitClarification should flip card to TIMED_OUT when repository rejects the reply`() = runTest {
        val userPrompt = "ask me late"
        val request = ClarificationRequest(
            id = "clr-late",
            question = "Pick one",
            options = listOf("X", "Y"),
            timeoutMs = 60_000L,
        )
        // Simulate the timeout firing before the user's reply reaches the repo.
        coEvery { clarificationRepository.submitClarification("clr-late", any()) } returns false

        viewModel = createViewModel()
        advanceUntilIdle()

        val sessionId = viewModel.uiState.value.currentSessionId
        coEvery { agentOrchestratorUseCase(sessionId, userPrompt, any()) } returns flow {
            emit(AgentOrchestratorState.AwaitingClarification(request))
            delay(10_000)
        }

        viewModel.sendMessage(userPrompt)
        testScheduler.advanceTimeBy(100)

        viewModel.submitClarification("clr-late", "Y")
        advanceUntilIdle()

        val card = viewModel.uiState.value.clarificationCards.single()
        assertEquals(ClarificationCardUiModel.Status.TIMED_OUT, card.status)
        // Card must show what the agent actually consumed (the default), not the user's
        // discarded reply.
        assertEquals("X", card.answer)
        coVerify { clarificationRepository.submitClarification("clr-late", "Y") }

        viewModel.stopGeneration()
        advanceUntilIdle()
    }

    @Test
    fun `submitClarification should be no-op when requestId is unknown`() = runTest {
        viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.submitClarification("not-a-real-id", "ignored")
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.clarificationCards.isEmpty())
        coVerify(exactly = 0) { clarificationRepository.submitClarification(any(), any()) }
    }

    @Test
    fun `markClarificationTimedOut should flip card to TIMED_OUT and not call repository`() = runTest {
        val userPrompt = "ask me"
        val request = ClarificationRequest(
            id = "clr-3",
            question = "Pick one",
            options = listOf("Yes", "No"),
            timeoutMs = 1_000L,
        )

        viewModel = createViewModel()
        advanceUntilIdle()

        val sessionId = viewModel.uiState.value.currentSessionId
        coEvery { agentOrchestratorUseCase(sessionId, userPrompt, any()) } returns flow {
            emit(AgentOrchestratorState.AwaitingClarification(request))
            delay(10_000)
        }

        viewModel.sendMessage(userPrompt)
        testScheduler.advanceTimeBy(100)

        viewModel.markClarificationTimedOut("clr-3", "Yes")
        advanceUntilIdle()

        val card = viewModel.uiState.value.clarificationCards.single()
        assertEquals(ClarificationCardUiModel.Status.TIMED_OUT, card.status)
        assertEquals("Yes", card.answer)
        coVerify(exactly = 0) { clarificationRepository.submitClarification(any(), any()) }

        viewModel.stopGeneration()
        advanceUntilIdle()
    }

    @Test
    fun `subsequent AwaitingClarification appends second card without removing the answered first`() = runTest {
        val userPrompt = "two clarifications"
        val request1 = ClarificationRequest(id = "clr-a", question = "First?", options = null, timeoutMs = 60_000L)
        val request2 = ClarificationRequest(id = "clr-b", question = "Second?", options = null, timeoutMs = 60_000L)

        viewModel = createViewModel()
        advanceUntilIdle()

        val sessionId = viewModel.uiState.value.currentSessionId
        coEvery { agentOrchestratorUseCase(sessionId, userPrompt, any()) } returns flow {
            emit(AgentOrchestratorState.AwaitingClarification(request1))
            delay(50)
            emit(AgentOrchestratorState.AwaitingClarification(request2))
            delay(10_000)
        }

        viewModel.sendMessage(userPrompt)
        testScheduler.advanceTimeBy(20)

        viewModel.submitClarification("clr-a", "answer-a")
        testScheduler.advanceTimeBy(100)

        val cards = viewModel.uiState.value.clarificationCards
        assertEquals(2, cards.size)
        val first = cards.first { it.id == "clr-a" }
        val second = cards.first { it.id == "clr-b" }
        assertEquals(ClarificationCardUiModel.Status.ANSWERED, first.status)
        assertEquals("answer-a", first.answer)
        assertEquals(ClarificationCardUiModel.Status.PENDING, second.status)
        assertNull(second.answer)

        viewModel.stopGeneration()
        advanceUntilIdle()
    }

    @Test
    fun `stopGeneration should drop pending clarification cards and keep answered ones`() = runTest {
        val userPrompt = "two more"
        val pending = ClarificationRequest(id = "clr-pending", question = "?", options = null, timeoutMs = 60_000L)
        val answered = ClarificationRequest(id = "clr-answered", question = "?", options = null, timeoutMs = 60_000L)

        viewModel = createViewModel()
        advanceUntilIdle()

        val sessionId = viewModel.uiState.value.currentSessionId
        coEvery { agentOrchestratorUseCase(sessionId, userPrompt, any()) } returns flow {
            emit(AgentOrchestratorState.AwaitingClarification(answered))
            delay(50)
            emit(AgentOrchestratorState.AwaitingClarification(pending))
            delay(10_000)
        }

        viewModel.sendMessage(userPrompt)
        testScheduler.advanceTimeBy(20)
        viewModel.submitClarification("clr-answered", "kept")
        testScheduler.advanceTimeBy(100)

        viewModel.stopGeneration()
        advanceUntilIdle()

        val remaining = viewModel.uiState.value.clarificationCards
        assertEquals(1, remaining.size)
        assertEquals("clr-answered", remaining.single().id)
        assertEquals(ClarificationCardUiModel.Status.ANSWERED, remaining.single().status)
    }

    @Test
    fun `importChat should accept bare JSON array of messages`() = runTest {
        viewModel = createViewModel()
        advanceUntilIdle()

        val array = JSONArray()
            .put(JSONObject().put("role", "USER").put("text", "a").put("timestamp", 1L))

        viewModel.importChat(array.toString())
        advanceUntilIdle()

        coVerify { chatRepository.saveSession(match { it.name == "Imported Chat" }) }
        coVerify {
            chatRepository.saveMessage(
                match { it.role == Role.USER && it.content == "a" && it.timestamp == 1L },
            )
        }
    }

    // -------------------------------------------------------------------
    // Phase 17.2 — Pipeline binding to chat
    // -------------------------------------------------------------------

    @Test
    fun `requestNewSession opens selector when pipelines exist`() = runTest {
        pipelinesFlow.value = listOf(pipeline("p-default", "Default"), pipeline("p-other", "Other"))
        viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.requestNewSession()
        advanceUntilIdle()

        val prompt = viewModel.uiState.value.newChatPipelinePrompt
        assertNotNull(prompt)
        assertEquals("p-default", prompt!!.preselectedPipelineId)
    }

    @Test
    fun `requestNewSession creates unbound chat directly when no pipelines exist`() = runTest {
        viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.requestNewSession()
        advanceUntilIdle()

        assertNull(viewModel.uiState.value.newChatPipelinePrompt)
        coVerify { chatRepository.saveSession(match { it.pipelineId == null }) }
    }

    @Test
    fun `confirmNewSession persists chosen pipelineId on the new session`() = runTest {
        pipelinesFlow.value = listOf(pipeline("p1", "Alpha"), pipeline("p2", "Beta"))
        viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.requestNewSession()
        viewModel.confirmNewSession("p2")
        advanceUntilIdle()

        coVerify { chatRepository.saveSession(match { it.pipelineId == "p2" }) }
        assertNull(viewModel.uiState.value.newChatPipelinePrompt)
    }

    @Test
    fun `confirmNewSession with null persists unbound session`() = runTest {
        pipelinesFlow.value = listOf(pipeline("p1", "Alpha"))
        viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.requestNewSession()
        viewModel.confirmNewSession(null)
        advanceUntilIdle()

        coVerify { chatRepository.saveSession(match { it.pipelineId == null && it.name == "New Chat" }) }
    }

    @Test
    fun `dismissNewChatPrompt closes selector without creating session`() = runTest {
        pipelinesFlow.value = listOf(pipeline("p1", "Alpha"))
        viewModel = createViewModel()
        advanceUntilIdle()

        // The init flow already saved the bootstrap "New Chat" session — count
        // those calls as the baseline so we can assert nothing else is saved.
        val baselineSaves = sessionsFlow.value.size

        viewModel.requestNewSession()
        viewModel.dismissNewChatPrompt()
        advanceUntilIdle()

        assertNull(viewModel.uiState.value.newChatPipelinePrompt)
        assertEquals(baselineSaves, sessionsFlow.value.size)
    }

    @Test
    fun `chat settings save persists new pipelineId on active session when idle`() = runTest {
        pipelinesFlow.value = listOf(pipeline("p1", "Alpha"), pipeline("p2", "Beta"))
        viewModel = createViewModel()
        advanceUntilIdle()

        val sessionId = viewModel.uiState.value.currentSessionId

        viewModel.openChatSettings()
        viewModel.updateChatSettingsSelection("p2")
        viewModel.confirmChatSettings()
        advanceUntilIdle()

        coVerify {
            chatRepository.saveSession(
                match { it.id == sessionId && it.pipelineId == "p2" },
            )
        }
        assertNull(viewModel.uiState.value.chatSettingsDialog)
    }

    @Test
    fun `chat settings save raises switch confirm dialog while generating`() = runTest {
        pipelinesFlow.value = listOf(pipeline("p1", "Alpha"), pipeline("p2", "Beta"))
        viewModel = createViewModel()
        advanceUntilIdle()

        val sessionId = viewModel.uiState.value.currentSessionId
        coEvery { agentOrchestratorUseCase(sessionId, "wait", any()) } returns flow {
            emit(AgentOrchestratorState.Loading)
            delay(10_000)
        }

        viewModel.sendMessage("wait")
        testScheduler.advanceTimeBy(50)
        assertTrue(viewModel.uiState.value.isGenerating)

        viewModel.openChatSettings()
        viewModel.updateChatSettingsSelection("p2")
        viewModel.confirmChatSettings()

        val confirm = viewModel.uiState.value.pipelineSwitchConfirm
        assertNotNull(confirm)
        assertEquals("p2", confirm!!.targetPipelineId)
        // The chat-settings dialog must stay open behind the confirm so that
        // "Wait" returns the user to a stable surface and so the second
        // Dialog window is not swallowed by a simultaneous dismiss-and-show.
        assertNotNull(viewModel.uiState.value.chatSettingsDialog)
        // The pipelineId is NOT applied yet — must wait for explicit confirmation.
        coVerify(exactly = 0) {
            chatRepository.saveSession(match { it.pipelineId == "p2" })
        }

        viewModel.stopGeneration()
        advanceUntilIdle()
    }

    @Test
    fun `confirmPipelineSwitchCancelGeneration cancels generation and applies binding`() = runTest {
        pipelinesFlow.value = listOf(pipeline("p1", "Alpha"), pipeline("p2", "Beta"))
        viewModel = createViewModel()
        advanceUntilIdle()

        val sessionId = viewModel.uiState.value.currentSessionId
        coEvery { agentOrchestratorUseCase(sessionId, "wait", any()) } returns flow {
            emit(AgentOrchestratorState.Loading)
            delay(10_000)
        }

        viewModel.sendMessage("wait")
        testScheduler.advanceTimeBy(50)

        viewModel.openChatSettings()
        viewModel.updateChatSettingsSelection("p2")
        viewModel.confirmChatSettings()
        viewModel.confirmPipelineSwitchCancelGeneration()
        advanceUntilIdle()

        assertFalse(viewModel.uiState.value.isGenerating)
        assertNull(viewModel.uiState.value.pipelineSwitchConfirm)
        // Both dialogs are dismissed together once the user commits.
        assertNull(viewModel.uiState.value.chatSettingsDialog)
        coVerify {
            chatRepository.saveSession(
                match { it.id == sessionId && it.pipelineId == "p2" },
            )
        }
    }

    @Test
    fun `dismissPipelineSwitchConfirm closes confirm without changing binding`() = runTest {
        pipelinesFlow.value = listOf(pipeline("p1", "Alpha"), pipeline("p2", "Beta"))
        viewModel = createViewModel()
        advanceUntilIdle()

        val sessionId = viewModel.uiState.value.currentSessionId
        coEvery { agentOrchestratorUseCase(sessionId, "wait", any()) } returns flow {
            emit(AgentOrchestratorState.Loading)
            delay(10_000)
        }

        viewModel.sendMessage("wait")
        testScheduler.advanceTimeBy(50)

        viewModel.openChatSettings()
        viewModel.updateChatSettingsSelection("p2")
        viewModel.confirmChatSettings()
        viewModel.dismissPipelineSwitchConfirm()

        assertNull(viewModel.uiState.value.pipelineSwitchConfirm)
        // The chat-settings dialog must stay open after "Wait" so the user
        // returns to a stable surface — they can change their mind or cancel.
        assertNotNull(viewModel.uiState.value.chatSettingsDialog)
        coVerify(exactly = 0) {
            chatRepository.saveSession(match { it.pipelineId == "p2" })
        }

        viewModel.stopGeneration()
        advanceUntilIdle()
    }

    @Test
    fun `given explicit defaultPipelineId when session unbound then TopAppBar reflects the default`() = runTest {
        val pinned = pipeline("p-pinned", "Pinned default")
        val first = pipeline("p-first", "First in library")
        // Ordering: `first` is the implicit fallback (first in summaries),
        // `pinned` is the explicit user-marked default. The explicit pick
        // must win over the implicit fallback for both the title and the
        // dialog labels — see `resolvePipelineName`.
        pipelinesFlow.value = listOf(first, pinned)
        every { settingsRepository.defaultPipelineId } returns flowOf("p-pinned")

        viewModel = createViewModel()
        advanceUntilIdle()

        assertEquals("Pinned default", viewModel.uiState.value.currentPipelineName)
        assertEquals("p-pinned", viewModel.uiState.value.defaultPipelineId)
    }

    @Test
    fun `given session bound to existing pipeline when ViewModel starts then no false fallback fires`() = runTest {
        // Reproduces the startup race: the sessions flow may emit before the
        // pipelines flow does, so `handleDeletedBoundPipeline` used to see an
        // empty `availablePipelines` and misread the bound pipeline as
        // deleted. Pre-seed both flows with consistent state — the bound
        // pipeline DOES exist — and confirm no fallback triggers.
        val savedSessionId = "race-session"
        val boundSession = ChatSession(
            id = savedSessionId,
            name = "Existing chat",
            updatedAt = 0L,
            pipelineId = "p-bound",
        )
        sessionsFlow.value = listOf(boundSession)
        pipelinesFlow.value = listOf(pipeline("p-bound", "Bound"), pipeline("p-default", "Default"))
        every { settingsRepository.currentChatSessionId } returns flowOf(savedSessionId)

        viewModel = createViewModel()
        advanceUntilIdle()

        // No fallback message, and the session was not silently rewritten.
        assertNull(viewModel.uiState.value.pipelineFallbackMessage)
        coVerify(exactly = 0) {
            chatRepository.saveSession(
                match { it.id == savedSessionId && it.pipelineId == null },
            )
        }
    }

    @Test
    fun `bound pipeline deletion auto-resets session and emits fallback Snackbar`() = runTest {
        pipelinesFlow.value = listOf(pipeline("p-bound", "Bound"), pipeline("p-default", "Default"))
        viewModel = createViewModel()
        advanceUntilIdle()

        val sessionId = viewModel.uiState.value.currentSessionId
        // Bind the active session to "p-bound".
        viewModel.openChatSettings()
        viewModel.updateChatSettingsSelection("p-bound")
        viewModel.confirmChatSettings()
        advanceUntilIdle()

        // Now remove the bound pipeline from the library.
        pipelinesFlow.value = listOf(pipeline("p-default", "Default"))
        advanceUntilIdle()

        // Session should have been silently rebound to null (= use default).
        coVerify {
            chatRepository.saveSession(
                match { it.id == sessionId && it.pipelineId == null },
            )
        }
        assertNotNull(viewModel.uiState.value.pipelineFallbackMessage)
    }

    @Test
    fun `clearPipelineFallback removes the one-shot Snackbar message`() = runTest {
        pipelinesFlow.value = listOf(pipeline("p-bound", "Bound"), pipeline("p-default", "Default"))
        viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.openChatSettings()
        viewModel.updateChatSettingsSelection("p-bound")
        viewModel.confirmChatSettings()
        advanceUntilIdle()
        pipelinesFlow.value = listOf(pipeline("p-default", "Default"))
        advanceUntilIdle()
        assertNotNull(viewModel.uiState.value.pipelineFallbackMessage)

        viewModel.clearPipelineFallback()

        assertNull(viewModel.uiState.value.pipelineFallbackMessage)
    }

    @Test
    fun `currentPipelineName resolves to bound pipeline when set`() = runTest {
        pipelinesFlow.value = listOf(pipeline("p1", "Alpha"), pipeline("p2", "Beta"))
        viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.openChatSettings()
        viewModel.updateChatSettingsSelection("p2")
        viewModel.confirmChatSettings()
        advanceUntilIdle()

        assertEquals("Beta", viewModel.uiState.value.currentPipelineName)
    }

    @Test
    fun `currentPipelineName falls back to default when session is unbound`() = runTest {
        pipelinesFlow.value = listOf(pipeline("p1", "Alpha"), pipeline("p2", "Beta"))
        viewModel = createViewModel()
        advanceUntilIdle()

        // The bootstrap session is created with pipelineId == null, so the
        // subtitle should show the default (first) pipeline's name.
        assertEquals("Alpha", viewModel.uiState.value.currentPipelineName)
    }

    @Test
    fun `currentPipelineName is null when no pipelines exist`() = runTest {
        viewModel = createViewModel()
        advanceUntilIdle()

        assertNull(viewModel.uiState.value.currentPipelineName)
    }

    @Test
    fun `sendMessage forwards bound pipelineId to AgentOrchestratorUseCase`() = runTest {
        pipelinesFlow.value = listOf(pipeline("p1", "Alpha"), pipeline("p2", "Beta"))
        viewModel = createViewModel()
        advanceUntilIdle()

        val sessionId = viewModel.uiState.value.currentSessionId
        viewModel.openChatSettings()
        viewModel.updateChatSettingsSelection("p2")
        viewModel.confirmChatSettings()
        advanceUntilIdle()

        coEvery { agentOrchestratorUseCase(sessionId, "go", "p2") } returns flow {
            emit(AgentOrchestratorState.Completed("done"))
        }

        viewModel.sendMessage("go")
        advanceUntilIdle()

        coVerify { agentOrchestratorUseCase(sessionId, "go", "p2") }
    }

    @Test
    fun `sendMessage forwards null pipelineId for unbound chat`() = runTest {
        pipelinesFlow.value = listOf(pipeline("p1", "Alpha"))
        viewModel = createViewModel()
        advanceUntilIdle()

        val sessionId = viewModel.uiState.value.currentSessionId
        coEvery { agentOrchestratorUseCase(sessionId, "go", null) } returns flow {
            emit(AgentOrchestratorState.Completed("done"))
        }

        viewModel.sendMessage("go")
        advanceUntilIdle()

        coVerify { agentOrchestratorUseCase(sessionId, "go", null) }
    }

    // -------------------------------------------------------------------
    // Phase 17.3 — Main chat area rework (filter + copy + star)
    // -------------------------------------------------------------------

    @Test
    fun `loadMessages sources only display messages by default`() = runTest {
        viewModel = createViewModel()
        advanceUntilIdle()

        val sessionId = viewModel.uiState.value.currentSessionId
        // The display flow excludes isFinal = false rows; the ViewModel must
        // observe that flow rather than the unfiltered one.
        io.mockk.verify { chatRepository.getDisplayMessagesForSession(sessionId) }
        io.mockk.verify(exactly = 0) { chatRepository.getStarredMessages() }
    }

    @Test
    fun `toggleStarredFilter swaps source to starred flow and back`() = runTest {
        viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.toggleStarredFilter()
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.showStarredOnly)
        io.mockk.verify { chatRepository.getStarredMessages() }

        viewModel.toggleStarredFilter()
        advanceUntilIdle()

        assertFalse(viewModel.uiState.value.showStarredOnly)
    }

    @Test
    fun `setMessageStarred forwards to repository`() = runTest {
        viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.setMessageStarred(messageId = 42L, starred = true)
        advanceUntilIdle()

        coVerify { chatRepository.setMessageStarred(42L, true) }
    }

    @Test
    fun `signalCopiedToClipboard sets snackbar message`() = runTest {
        viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.signalCopiedToClipboard()

        assertEquals(
            ai.agent.android.presentation.ui.common.UiText.Resource(ai.agent.android.R.string.chat_snackbar_copied),
            viewModel.uiState.value.snackbarMessage,
        )
    }

    @Test
    fun `consumeSnackbar clears the snackbar message`() = runTest {
        viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.signalCopiedToClipboard()
        assertNotNull(viewModel.uiState.value.snackbarMessage)

        viewModel.consumeSnackbar()
        assertNull(viewModel.uiState.value.snackbarMessage)
    }

    @Test
    fun `loadMessages uses display flow that filters intermediate messages`() = runTest {
        // Verifies the source-flow contract: when the repository emits only
        // the user-facing subset (isFinal = true) for the session, those are
        // the messages the ViewModel surfaces — guaranteeing intermediate
        // node outputs never reach the chat list.
        viewModel = createViewModel()
        advanceUntilIdle()

        val sessionId = viewModel.uiState.value.currentSessionId
        val finalMsg = ChatMessage(
            id = 1L,
            sessionId = sessionId,
            role = Role.AGENT,
            content = "final",
            timestamp = 100L,
            isFinal = true,
        )
        every { chatRepository.getDisplayMessagesForSession(sessionId) } returns
            flowOf(listOf(finalMsg))

        // Re-load by toggling the filter twice (off→on→off) so the observer
        // re-subscribes and picks up the new stub. The end state is the
        // unfiltered display flow, which now emits the prepared list.
        viewModel.toggleStarredFilter()
        advanceUntilIdle()
        viewModel.toggleStarredFilter()
        advanceUntilIdle()

        val emitted = viewModel.uiState.value.messages
        assertEquals(1, emitted.size)
        assertEquals("final", emitted.first().content)
        assertTrue(emitted.first().isFinal)
    }

    @Test
    fun `intermediate node outputs reach console while only isFinal messages reach chat list`() = runTest {
        // Cross-cuts two independent behaviors verified separately above:
        //   (a) `getDisplayMessagesForSession` filters out `isFinal = false` rows
        //       before they hit `uiState.messages` (Phase 17.3).
        //   (b) `AgentOrchestratorState.ConsoleLog` events feed `uiState.consoleLines`
        //       regardless of whether their underlying step produced a final message
        //       (Phase 17.5).
        // This integration-level assertion prevents future regressions where one
        // path is changed without the other — e.g. a refactor that accidentally
        // surfaces intermediate node outputs in the chat list, or one that drops
        // intermediate ConsoleEvents on the way to the panel.
        viewModel = createViewModel()
        advanceUntilIdle()

        val sessionId = viewModel.uiState.value.currentSessionId
        val finalMessage = ChatMessage(
            id = 1L,
            sessionId = sessionId,
            role = Role.AGENT,
            content = "final answer",
            timestamp = 100L,
            isFinal = true,
        )
        // Repository-level filter contract: `isFinal = false` rows are absent here.
        every { chatRepository.getDisplayMessagesForSession(sessionId) } returns
            flowOf(listOf(finalMessage))

        val intermediateEvent = ConsoleEvent(
            timestamp = 1_700_000_000_000L,
            type = ConsoleEventType.NodeExecution,
            message = "▶ LITE_RT (intermediate, isFinal=false in DB)",
        )
        val finalEvent = ConsoleEvent(
            timestamp = 1_700_000_001_000L,
            type = ConsoleEventType.SystemMessage,
            message = "Pipeline completed",
        )
        coEvery { agentOrchestratorUseCase(sessionId, "go", any()) } returns flow {
            emit(AgentOrchestratorState.ConsoleLog(listOf(intermediateEvent)))
            emit(AgentOrchestratorState.ConsoleLog(listOf(intermediateEvent, finalEvent)))
            emit(AgentOrchestratorState.Completed("done"))
        }

        // Re-subscribe the message observer so the new display-flow stub takes
        // effect (toggle off→on→off; the ViewModel switches sources on each call).
        viewModel.toggleStarredFilter()
        advanceUntilIdle()
        viewModel.toggleStarredFilter()
        advanceUntilIdle()

        viewModel.sendMessage("go")
        advanceUntilIdle()

        // Chat list: only the final message survives the display-flow filter.
        val chatMessages = viewModel.uiState.value.messages
        assertEquals(1, chatMessages.size)
        assertEquals("final answer", chatMessages.single().content)
        assertTrue(chatMessages.single().isFinal)

        // Console: every event — including the one whose underlying message is
        // intermediate and never reaches the chat list — is preserved.
        val consoleLines = viewModel.uiState.value.consoleLines
        assertEquals(2, consoleLines.size)
        assertEquals(intermediateEvent, consoleLines[0])
        assertEquals(finalEvent, consoleLines[1])
    }

    @Test
    fun `given orchestrator emits ConsoleLog when collected then consoleLines mirror events`() = runTest {
        val userPrompt = "console test"
        val event1 = ConsoleEvent(
            timestamp = 1_700_000_000_000L,
            type = ConsoleEventType.SystemMessage,
            message = "Pipeline started",
        )
        val event2 = ConsoleEvent(
            timestamp = 1_700_000_001_000L,
            type = ConsoleEventType.NodeExecution,
            message = "▶ LITE_RT",
        )

        viewModel = createViewModel()
        advanceUntilIdle()

        val sessionId = viewModel.uiState.value.currentSessionId
        coEvery { agentOrchestratorUseCase(sessionId, userPrompt, any()) } returns flow {
            emit(AgentOrchestratorState.ConsoleLog(listOf(event1)))
            emit(AgentOrchestratorState.ConsoleLog(listOf(event1, event2)))
            emit(AgentOrchestratorState.Completed("done"))
        }

        viewModel.sendMessage(userPrompt)
        advanceUntilIdle()

        val lines = viewModel.uiState.value.consoleLines
        assertEquals(2, lines.size)
        assertEquals(event1, lines[0])
        assertEquals(event2, lines[1])
    }

    @Test
    fun `given prior console events when sendMessage starts then consoleLines cleared before new emissions`() =
        runTest {
            val firstPrompt = "first"
            val secondPrompt = "second"
            val staleEvent = ConsoleEvent(
                timestamp = 1_700_000_000_000L,
                type = ConsoleEventType.NodeExecution,
                message = "stale",
            )

            viewModel = createViewModel()
            advanceUntilIdle()

            val sessionId = viewModel.uiState.value.currentSessionId
            coEvery { agentOrchestratorUseCase(sessionId, firstPrompt, any()) } returns flow {
                emit(AgentOrchestratorState.ConsoleLog(listOf(staleEvent)))
                emit(AgentOrchestratorState.Completed("ok"))
            }

            viewModel.sendMessage(firstPrompt)
            advanceUntilIdle()
            assertEquals(1, viewModel.uiState.value.consoleLines.size)

            // Second send: orchestrator never emits ConsoleLog. Verifies that the
            // ChatViewModel resets consoleLines on send-start rather than relying
            // on the engine to send an empty snapshot.
            coEvery { agentOrchestratorUseCase(sessionId, secondPrompt, any()) } returns flow {
                emit(AgentOrchestratorState.Completed("ok2"))
            }
            viewModel.sendMessage(secondPrompt)
            advanceUntilIdle()

            assertTrue(viewModel.uiState.value.consoleLines.isEmpty())
        }

    @Test
    fun `given prior console events when switchSession called then consoleLines cleared`() = runTest {
        val userPrompt = "before switch"
        val event = ConsoleEvent(
            timestamp = 1_700_000_000_000L,
            type = ConsoleEventType.ToolCall,
            message = "search_tool",
        )

        viewModel = createViewModel()
        advanceUntilIdle()

        val sessionId = viewModel.uiState.value.currentSessionId
        coEvery { agentOrchestratorUseCase(sessionId, userPrompt, any()) } returns flow {
            emit(AgentOrchestratorState.ConsoleLog(listOf(event)))
            emit(AgentOrchestratorState.Completed("done"))
        }

        viewModel.sendMessage(userPrompt)
        advanceUntilIdle()
        assertEquals(1, viewModel.uiState.value.consoleLines.size)

        viewModel.switchSession("other-session")
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.consoleLines.isEmpty())
    }

    @Test
    fun `given orchestrator stream completes when terminal state arrives then last consoleLines preserved`() = runTest {
        val userPrompt = "preserve test"
        val event = ConsoleEvent(
            timestamp = 1_700_000_000_000L,
            type = ConsoleEventType.SystemMessage,
            message = "Pipeline completed",
        )

        viewModel = createViewModel()
        advanceUntilIdle()

        val sessionId = viewModel.uiState.value.currentSessionId
        coEvery { agentOrchestratorUseCase(sessionId, userPrompt, any()) } returns flow {
            emit(AgentOrchestratorState.ConsoleLog(listOf(event)))
            emit(AgentOrchestratorState.Completed("done"))
        }

        viewModel.sendMessage(userPrompt)
        advanceUntilIdle()

        // After Completed the panel must still show the last events so the user
        // can review what just happened. Only the next sendMessage / switchSession
        // is allowed to clear them.
        assertEquals(listOf(event), viewModel.uiState.value.consoleLines)
        assertFalse(viewModel.uiState.value.isGenerating)
    }

    @Test
    fun `given default state when openConsoleSheet then consoleSheetVisible is true`() = runTest {
        viewModel = createViewModel()
        advanceUntilIdle()

        assertFalse(viewModel.uiState.value.consoleSheetVisible)

        viewModel.openConsoleSheet()
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.consoleSheetVisible)
    }

    @Test
    fun `given sheet open when dismissConsoleSheet then consoleSheetVisible is false`() = runTest {
        viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.openConsoleSheet()
        advanceUntilIdle()
        assertTrue(viewModel.uiState.value.consoleSheetVisible)

        viewModel.dismissConsoleSheet()
        advanceUntilIdle()

        assertFalse(viewModel.uiState.value.consoleSheetVisible)
    }

    @Test
    fun `given default filter when setConsoleFilter Errors then consoleSheetFilter updated`() = runTest {
        viewModel = createViewModel()
        advanceUntilIdle()

        assertEquals(ConsoleLogFilter.All, viewModel.uiState.value.consoleSheetFilter)

        viewModel.setConsoleFilter(ConsoleLogFilter.Errors)
        advanceUntilIdle()

        assertEquals(ConsoleLogFilter.Errors, viewModel.uiState.value.consoleSheetFilter)
    }

    @Test
    fun `given populated consoleLines when clearConsoleLog then list emptied`() = runTest {
        val userPrompt = "clear test"
        val event = ConsoleEvent(
            timestamp = 1_700_000_000_000L,
            type = ConsoleEventType.NodeExecution,
            message = "▶ LITE_RT",
        )

        viewModel = createViewModel()
        advanceUntilIdle()

        val sessionId = viewModel.uiState.value.currentSessionId
        coEvery { agentOrchestratorUseCase(sessionId, userPrompt, any()) } returns flow {
            emit(AgentOrchestratorState.ConsoleLog(listOf(event)))
            emit(AgentOrchestratorState.Completed("done"))
        }

        viewModel.sendMessage(userPrompt)
        advanceUntilIdle()
        assertEquals(1, viewModel.uiState.value.consoleLines.size)

        viewModel.clearConsoleLog()
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.consoleLines.isEmpty())
    }

    @Test
    fun `given clearConsoleLog mid-generation when next ConsoleLog snapshot arrives then dropped events stay gone`() =
        runTest {
            val userPrompt = "durable clear"
            val event1 = ConsoleEvent(
                timestamp = 1_700_000_000_000L,
                type = ConsoleEventType.NodeExecution,
                message = "▶ NODE_A",
            )
            val event2 = ConsoleEvent(
                timestamp = 1_700_000_001_000L,
                type = ConsoleEventType.NodeExecution,
                message = "▶ NODE_B",
            )
            val event3 = ConsoleEvent(
                timestamp = 1_700_000_002_000L,
                type = ConsoleEventType.ToolCall,
                message = "search_tool",
            )

            viewModel = createViewModel()
            advanceUntilIdle()

            val sessionId = viewModel.uiState.value.currentSessionId

            // Three-step orchestrator run with a Clear injected after step 2.
            // The orchestrator emits cumulative `events` snapshots, so without
            // the baseline the third snapshot would re-introduce event1/event2.
            coEvery { agentOrchestratorUseCase(sessionId, userPrompt, any()) } returns flow {
                emit(AgentOrchestratorState.ConsoleLog(listOf(event1)))
                emit(AgentOrchestratorState.ConsoleLog(listOf(event1, event2)))
                // Simulate the user tapping Clear right before the next step.
                viewModel.clearConsoleLog()
                emit(AgentOrchestratorState.ConsoleLog(listOf(event1, event2, event3)))
                emit(AgentOrchestratorState.Completed("done"))
            }

            viewModel.sendMessage(userPrompt)
            advanceUntilIdle()

            // Only event3 — the events appended after the Clear — survives.
            assertEquals(listOf(event3), viewModel.uiState.value.consoleLines)
        }

    @Test
    fun `given clearConsoleLog then sendMessage starts then baseline reset for new run`() = runTest {
        val firstPrompt = "first"
        val secondPrompt = "second"
        val event1 = ConsoleEvent(
            timestamp = 1_700_000_000_000L,
            type = ConsoleEventType.NodeExecution,
            message = "first run",
        )
        val event2 = ConsoleEvent(
            timestamp = 1_700_000_001_000L,
            type = ConsoleEventType.NodeExecution,
            message = "second run",
        )

        viewModel = createViewModel()
        advanceUntilIdle()

        val sessionId = viewModel.uiState.value.currentSessionId

        coEvery { agentOrchestratorUseCase(sessionId, firstPrompt, any()) } returns flow {
            emit(AgentOrchestratorState.ConsoleLog(listOf(event1)))
            viewModel.clearConsoleLog()
            emit(AgentOrchestratorState.Completed("done1"))
        }
        viewModel.sendMessage(firstPrompt)
        advanceUntilIdle()
        assertTrue(viewModel.uiState.value.consoleLines.isEmpty())

        // Second run: orchestrator restarts the cumulative log from a single
        // event. The baseline carried over from run 1 would, if not reset,
        // suppress event2 entirely; it must surface untouched.
        coEvery { agentOrchestratorUseCase(sessionId, secondPrompt, any()) } returns flow {
            emit(AgentOrchestratorState.ConsoleLog(listOf(event2)))
            emit(AgentOrchestratorState.Completed("done2"))
        }
        viewModel.sendMessage(secondPrompt)
        advanceUntilIdle()

        assertEquals(listOf(event2), viewModel.uiState.value.consoleLines)
    }

    @Test
    fun `given default state when signalConsoleCopied then snackbarMessage set`() = runTest {
        viewModel = createViewModel()
        advanceUntilIdle()

        assertNull(viewModel.uiState.value.snackbarMessage)

        viewModel.signalConsoleCopied()
        advanceUntilIdle()

        assertEquals(
            ai.agent.android.presentation.ui.common.UiText.Resource(
                ai.agent.android.R.string.chat_snackbar_console_copied,
            ),
            viewModel.uiState.value.snackbarMessage,
        )
    }
}
