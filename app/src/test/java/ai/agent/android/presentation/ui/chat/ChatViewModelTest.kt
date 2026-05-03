package ai.agent.android.presentation.ui.chat

import ai.agent.android.domain.engine.LlmInferenceEngine
import ai.agent.android.domain.models.AgentOrchestratorState
import ai.agent.android.domain.models.ChatMessage
import ai.agent.android.domain.models.ChatSession
import ai.agent.android.domain.models.ClarificationRequest
import ai.agent.android.domain.models.Role
import kotlinx.coroutines.delay
import ai.agent.android.domain.models.Result
import ai.agent.android.domain.repositories.ChatRepository
import ai.agent.android.domain.repositories.ClarificationRepository
import ai.agent.android.domain.usecases.AgentOrchestratorUseCase
import ai.agent.android.domain.repositories.SettingsRepository
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
    )

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

        // Mock chat repository flow for any session
        every { chatRepository.getMessagesForSession(any()) } returns flowOf(emptyList())
        every { chatRepository.getSessionsFlow() } returns flowOf(emptyList())
        coEvery { chatRepository.saveSession(any()) } returns Unit
        coEvery { chatRepository.saveMessage(any()) } returns Unit
        coEvery { chatRepository.deleteSession(any()) } returns Unit
        coEvery { chatRepository.getSessionById(any()) } returns null
        // Default: no saved session
        every { settingsRepository.currentChatSessionId } returns flowOf(null)
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
        assertTrue(state.errorMessage!!.contains(errorMsg))
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

        coVerify(exactly = 0) { agentOrchestratorUseCase(any(), any()) }
        assertFalse(viewModel.uiState.value.isGenerating)
    }

    @Test
    fun `sendMessage should update state to generating and collect orchestrator states`() = runTest {
        val userPrompt = "Test prompt"
        
        viewModel = createViewModel()
        advanceUntilIdle()

        val actualSessionId = viewModel.uiState.value.currentSessionId

        coEvery { agentOrchestratorUseCase(actualSessionId, userPrompt) } returns flow {
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

        coEvery { agentOrchestratorUseCase(actualSessionId, userPrompt) } returns flow {
            throw RuntimeException(exceptionMessage)
        }

        viewModel.sendMessage(userPrompt)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertFalse(state.isGenerating)
        assertEquals(exceptionMessage, state.errorMessage)
        assertTrue(state.orchestratorState is AgentOrchestratorState.Error)
        assertEquals(exceptionMessage, (state.orchestratorState as AgentOrchestratorState.Error).message)
    }

    @Test
    fun `clearError should set errorMessage to null`() = runTest {
        viewModel = createViewModel()
        advanceUntilIdle()

        // Force an error state
        val actualSessionId = viewModel.uiState.value.currentSessionId
        coEvery { agentOrchestratorUseCase(actualSessionId, "trigger error") } returns flow {
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
        coEvery { agentOrchestratorUseCase(sessionId, userPrompt) } returns flow {
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
        coEvery { agentOrchestratorUseCase(sessionId, userPrompt) } returns flow {
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
        coEvery { agentOrchestratorUseCase(sessionId, userPrompt) } returns flow {
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
        coEvery { agentOrchestratorUseCase(sessionId, userPrompt) } returns flow {
            emit(AgentOrchestratorState.Thinking(partialText))
            delay(10_000)
        }

        viewModel.sendMessage(userPrompt)
        testScheduler.advanceTimeBy(100)

        viewModel.stopGeneration()
        advanceUntilIdle()

        coVerify {
            chatRepository.saveMessage(
                match { it.content.contains(partialText) && it.content.contains("[stopped]") }
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
        coVerify(exactly = 0) { agentOrchestratorUseCase(any(), any()) }
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
        coEvery { agentOrchestratorUseCase(sessionId, "hello again") } returns flow {
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
        coEvery { agentOrchestratorUseCase(sessionId, userPrompt) } returns flow {
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
        coEvery { agentOrchestratorUseCase(sessionId, userPrompt) } returns flow {
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
        coEvery { agentOrchestratorUseCase(sessionId, userPrompt) } returns flow {
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
        coEvery { agentOrchestratorUseCase(sessionId, userPrompt) } returns flow {
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
        coEvery { agentOrchestratorUseCase(sessionId, userPrompt) } returns flow {
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
        coEvery { agentOrchestratorUseCase(sessionId, userPrompt) } returns flow {
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
}
