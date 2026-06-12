package app.knotwork.android.domain.engine.executors

import app.knotwork.android.domain.engine.LlmInferenceEngine
import app.knotwork.android.domain.models.AgentOrchestratorState
import app.knotwork.android.domain.models.AppError
import app.knotwork.android.domain.models.ClarificationOutcome
import app.knotwork.android.domain.models.ClarificationRequest
import app.knotwork.android.domain.models.NodeExecutionResult
import app.knotwork.android.domain.models.NodeModel
import app.knotwork.android.domain.models.NodeType
import app.knotwork.android.domain.models.PendingInteraction
import app.knotwork.android.domain.models.PendingInteractionKind
import app.knotwork.android.domain.models.Result
import app.knotwork.android.domain.repositories.ClarificationRepository
import app.knotwork.android.domain.repositories.PendingInteractionRepository
import app.knotwork.android.domain.services.ClarificationNotifier
import app.knotwork.android.domain.usecases.LoadModelUseCase
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Unit tests for [ClarificationNodeExecutor].
 *
 * The executor's contract:
 * - generates a clarifying question (and optional options) via the local LLM,
 * - emits [AgentOrchestratorState.Thinking] tokens during generation,
 * - emits [AgentOrchestratorState.AwaitingClarification] with a parsed request,
 * - suspends on [ClarificationRepository.requestAnswer] and forwards the answer as
 *   [NodeExecutionResult.outputText].
 *
 * On malformed LLM output the raw text becomes the question (no options).
 */
class ClarificationNodeExecutorTest {

    private lateinit var llmEngine: LlmInferenceEngine
    private lateinit var loadModelUseCase: LoadModelUseCase
    private lateinit var clarificationRepository: ClarificationRepository
    private lateinit var pendingInteractionRepository: PendingInteractionRepository
    private lateinit var clarificationNotifier: ClarificationNotifier
    private lateinit var executor: ClarificationNodeExecutor

    @Before
    fun setup() {
        llmEngine = mockk()
        loadModelUseCase = mockk()
        clarificationRepository = mockk()
        pendingInteractionRepository = mockk(relaxed = true)
        clarificationNotifier = mockk(relaxed = true)
        coEvery { pendingInteractionRepository.getForRun(any()) } returns null
        coEvery { pendingInteractionRepository.save(any()) } returns true
        executor = ClarificationNodeExecutor(
            llmEngine = llmEngine,
            loadModelUseCase = loadModelUseCase,
            clarificationRepository = clarificationRepository,
            pendingInteractionRepository = pendingInteractionRepository,
            clarificationNotifier = clarificationNotifier,
        )
    }

    @Test
    fun `given valid JSON with options when execute then forwards user answer`() = runTest {
        val node = clarificationNode()
        coEvery { loadModelUseCase(any()) } returns Result.Success(Unit)
        every { llmEngine.generateResponseStream(any()) } returns flowOf(
            "{\"question\":\"Pick a color\",\"options\":[\"red\",\"blue\"]}",
        )
        val captured = slot<ClarificationRequest>()
        coEvery { clarificationRepository.requestAnswer(capture(captured)) } returns
            ClarificationOutcome.Answered("red")

        val states = executor.execute(node, "ctx", "session", "prompt").toList().unwrap()

        // Final NodeExecutionResult carries the user's answer.
        val result = states.last() as NodeExecutionResult
        assertEquals("red", result.outputText)
        assertNull(result.error)

        // The intermediate AwaitingClarification carries the parsed request.
        val awaiting = states.filterIsInstance<AgentOrchestratorState.AwaitingClarification>().single()
        assertEquals("Pick a color", awaiting.request.question)
        assertEquals(listOf("red", "blue"), awaiting.request.options)
        assertEquals(60_000L, awaiting.request.timeoutMs)

        // Repository was asked for an answer with the same parsed request.
        assertEquals("Pick a color", captured.captured.question)
        assertEquals(listOf("red", "blue"), captured.captured.options)
    }

    @Test
    fun `given empty options array when execute then options are null free-form`() = runTest {
        val node = clarificationNode()
        coEvery { loadModelUseCase(any()) } returns Result.Success(Unit)
        every { llmEngine.generateResponseStream(any()) } returns flowOf(
            "{\"question\":\"Describe issue\",\"options\":[]}",
        )
        coEvery { clarificationRepository.requestAnswer(any()) } returns ClarificationOutcome.Answered("free text")

        val states = executor.execute(node, "ctx", "session", "prompt").toList().unwrap()

        val awaiting = states.filterIsInstance<AgentOrchestratorState.AwaitingClarification>().single()
        assertEquals("Describe issue", awaiting.request.question)
        assertNull(awaiting.request.options)

        assertEquals("free text", (states.last() as NodeExecutionResult).outputText)
    }

    @Test
    fun `given fenced JSON block when execute then parses inner object`() = runTest {
        val node = clarificationNode()
        coEvery { loadModelUseCase(any()) } returns Result.Success(Unit)
        every { llmEngine.generateResponseStream(any()) } returns flowOf(
            "Sure, here is the JSON:\n```json\n{\"question\":\"Confirm?\",\"options\":[\"yes\"]}\n```\n",
        )
        coEvery { clarificationRepository.requestAnswer(any()) } returns ClarificationOutcome.Answered("yes")

        val states = executor.execute(node, "ctx", "session", "prompt").toList().unwrap()

        val awaiting = states.filterIsInstance<AgentOrchestratorState.AwaitingClarification>().single()
        assertEquals("Confirm?", awaiting.request.question)
        assertEquals(listOf("yes"), awaiting.request.options)
    }

    @Test
    fun `given malformed JSON when execute then raw text becomes question`() = runTest {
        val node = clarificationNode()
        coEvery { loadModelUseCase(any()) } returns Result.Success(Unit)
        every { llmEngine.generateResponseStream(any()) } returns flowOf(
            "Could not produce JSON. Please confirm.",
        )
        coEvery { clarificationRepository.requestAnswer(any()) } returns ClarificationOutcome.Answered("")

        val states = executor.execute(node, "ctx", "session", "prompt").toList().unwrap()

        val awaiting = states.filterIsInstance<AgentOrchestratorState.AwaitingClarification>().single()
        assertEquals("Could not produce JSON. Please confirm.", awaiting.request.question)
        assertNull(awaiting.request.options)
    }

    @Test
    fun `given custom clarificationTimeoutMs when execute then request carries it`() = runTest {
        val node = clarificationNode(timeoutMs = 12_345L)
        coEvery { loadModelUseCase(any()) } returns Result.Success(Unit)
        every { llmEngine.generateResponseStream(any()) } returns flowOf(
            "{\"question\":\"q\",\"options\":[]}",
        )
        coEvery { clarificationRepository.requestAnswer(any()) } returns ClarificationOutcome.Answered("a")

        val states = executor.execute(node, "ctx", "session", "prompt").toList().unwrap()

        val awaiting = states.filterIsInstance<AgentOrchestratorState.AwaitingClarification>().single()
        assertEquals(12_345L, awaiting.request.timeoutMs)
    }

    @Test
    fun `given model load error when execute then emits Error and result with error`() = runTest {
        val node = clarificationNode()
        val systemError = object : AppError.System {}
        coEvery { loadModelUseCase(any()) } returns Result.Error(
            error = systemError,
            throwable = RuntimeException("boom"),
        )

        val states = executor.execute(node, "ctx", "session", "prompt").toList().unwrap()

        val errorState = states.filterIsInstance<AgentOrchestratorState.Error>().single()
        assertTrue(errorState.message.contains("Error loading local model"))
        val result = states.last() as NodeExecutionResult
        assertNotNull(result.error)
    }

    @Test
    fun `given LLM stream throws when execute then emits Error and result with error`() = runTest {
        val node = clarificationNode()
        coEvery { loadModelUseCase(any()) } returns Result.Success(Unit)
        every { llmEngine.generateResponseStream(any()) } throws RuntimeException("inference failure")

        val states = executor.execute(node, "ctx", "session", "prompt").toList().unwrap()

        val errorState = states.filterIsInstance<AgentOrchestratorState.Error>().single()
        assertTrue(errorState.message.contains("inference failure"))
        val result = states.last() as NodeExecutionResult
        assertEquals("inference failure", result.error)
    }

    @Test(expected = CancellationException::class)
    fun `given LLM stream cancelled when execute then re-throws CancellationException`() = runTest {
        // Catching `Exception` would swallow CancellationException and break structured
        // concurrency: the parent coroutine would never observe the cancel. The executor
        // must rethrow so collectors propagate the cancel up the chain.
        val node = clarificationNode()
        coEvery { loadModelUseCase(any()) } returns Result.Success(Unit)
        every { llmEngine.generateResponseStream(any()) } returns flow {
            throw CancellationException("cancelled mid-stream")
        }

        executor.execute(node, "ctx", "session", "prompt").toList()
    }

    @Test
    fun `given valid JSON when execute then emits Thinking before AwaitingClarification`() = runTest {
        val node = clarificationNode()
        coEvery { loadModelUseCase(any()) } returns Result.Success(Unit)
        every { llmEngine.generateResponseStream(any()) } returns flowOf(
            "{\"question\":\"q\",\"options\":[\"a\"]}",
        )
        coEvery { clarificationRepository.requestAnswer(any()) } returns ClarificationOutcome.Answered("a")

        val states = executor.execute(node, "ctx", "session", "prompt").toList().unwrap()

        val thinkingIdx = states.indexOfFirst { it is AgentOrchestratorState.Thinking }
        val awaitingIdx = states.indexOfFirst { it is AgentOrchestratorState.AwaitingClarification }
        assertTrue("Expected Thinking emitted", thinkingIdx >= 0)
        assertTrue("Expected AwaitingClarification emitted", awaitingIdx >= 0)
        assertTrue("Thinking must precede AwaitingClarification", thinkingIdx < awaitingIdx)
    }

    private fun clarificationNode(timeoutMs: Long? = null) = NodeModel(
        id = "node-1",
        type = NodeType.CLARIFICATION,
        x = 0f,
        y = 0f,
        systemPrompt = "Ask the user for clarification.",
        clarificationTimeoutMs = timeoutMs,
    )

    // ─── Two-phase background waiting ───────────────────────────────────────

    @Test
    fun `given live timeout on a persisted run when execute then parks instead of defaulting`() = runTest {
        val node = clarificationNode()
        coEvery { loadModelUseCase(any()) } returns Result.Success(Unit)
        every { llmEngine.generateResponseStream(any()) } returns flowOf(
            "{\"question\":\"Pick a color\",\"options\":[\"red\",\"blue\"]}",
        )
        coEvery { clarificationRepository.requestAnswer(any()) } returns ClarificationOutcome.TimedOut

        val states = executor.execute(node, "ctx", "session", "prompt", runId = "run-1").toList().unwrap()

        // The flow ends with the parked state and NO result.
        val last = states.last() as AgentOrchestratorState.SuspendedInBackground
        assertEquals(PendingInteractionKind.CLARIFICATION, last.kind)
        assertTrue(states.filterIsInstance<NodeExecutionResult>().isEmpty())
        coVerify {
            pendingInteractionRepository.save(
                match {
                    it.runId == "run-1" &&
                        it.kind == PendingInteractionKind.CLARIFICATION &&
                        it.question == "Pick a color" &&
                        it.options == listOf("red", "blue")
                },
            )
        }
        verify { clarificationNotifier.sendPersistentClarificationRequest("run-1", "session", "Pick a color") }
    }

    @Test
    fun `given live timeout on a non-persisted run when execute then falls back to default answer`() = runTest {
        val node = clarificationNode()
        coEvery { loadModelUseCase(any()) } returns Result.Success(Unit)
        every { llmEngine.generateResponseStream(any()) } returns flowOf(
            "{\"question\":\"Pick a color\",\"options\":[\"red\",\"blue\"]}",
        )
        coEvery { clarificationRepository.requestAnswer(any()) } returns ClarificationOutcome.TimedOut

        val states = executor.execute(node, "ctx", "session", "prompt").toList().unwrap()

        val result = states.filterIsInstance<NodeExecutionResult>().last()
        assertEquals("red", result.outputText)
        coVerify(exactly = 0) { pendingInteractionRepository.save(any()) }
    }

    @Test
    fun `given live timeout and park persistence fails when execute then falls back to default answer`() = runTest {
        val node = clarificationNode()
        coEvery { loadModelUseCase(any()) } returns Result.Success(Unit)
        every { llmEngine.generateResponseStream(any()) } returns flowOf(
            "{\"question\":\"Pick a color\",\"options\":[\"red\",\"blue\"]}",
        )
        coEvery { clarificationRepository.requestAnswer(any()) } returns ClarificationOutcome.TimedOut
        coEvery { pendingInteractionRepository.save(any()) } returns false

        val states = executor.execute(node, "ctx", "session", "prompt", runId = "run-1").toList().unwrap()

        val result = states.filterIsInstance<NodeExecutionResult>().last()
        assertEquals("red", result.outputText)
        verify(exactly = 0) { clarificationNotifier.sendPersistentClarificationRequest(any(), any(), any()) }
    }

    @Test
    fun `given parked record with recorded answer when execute then returns it without inference`() = runTest {
        val node = clarificationNode()
        coEvery { pendingInteractionRepository.getForRun("run-1") } returns PendingInteraction(
            runId = "run-1",
            sessionId = "session",
            kind = PendingInteractionKind.CLARIFICATION,
            question = "Pick a color",
            options = listOf("red", "blue"),
            answer = "blue",
            requestedAt = 0L,
        )

        val states = executor.execute(node, "ctx", "session", "prompt", runId = "run-1").toList().unwrap()

        val result = states.filterIsInstance<NodeExecutionResult>().last()
        assertEquals("blue", result.outputText)
        // One-shot consumption, and the LLM was never touched.
        coVerify { pendingInteractionRepository.delete("run-1") }
        verify(exactly = 0) { llmEngine.generateResponseStream(any()) }
    }

    @Test
    fun `given parked record without an answer when execute then asks a fresh question`() = runTest {
        val node = clarificationNode()
        coEvery { pendingInteractionRepository.getForRun("run-1") } returns PendingInteraction(
            runId = "run-1",
            sessionId = "session",
            kind = PendingInteractionKind.CLARIFICATION,
            question = "Pick a color",
            requestedAt = 0L,
        )
        coEvery { loadModelUseCase(any()) } returns Result.Success(Unit)
        every { llmEngine.generateResponseStream(any()) } returns flowOf(
            "{\"question\":\"Pick a color\",\"options\":[\"red\"]}",
        )
        coEvery { clarificationRepository.requestAnswer(any()) } returns ClarificationOutcome.Answered("red")

        val states = executor.execute(node, "ctx", "session", "prompt", runId = "run-1").toList().unwrap()

        val result = states.filterIsInstance<NodeExecutionResult>().last()
        assertEquals("red", result.outputText)
        coVerify { pendingInteractionRepository.delete("run-1") }
    }
}
