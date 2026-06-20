package app.knotwork.android.domain.engine.executors

import app.knotwork.android.domain.engine.LlmInferenceEngine
import app.knotwork.android.domain.models.ExecutionScope
import app.knotwork.android.domain.models.ModelPerformanceSample
import app.knotwork.android.domain.models.NodeModel
import app.knotwork.android.domain.models.NodeOutput
import app.knotwork.android.domain.models.NodeType
import app.knotwork.android.domain.models.Result
import app.knotwork.android.domain.repositories.ChatRepository
import app.knotwork.android.domain.repositories.MetricsRepository
import app.knotwork.android.domain.repositories.ModelPerformanceRepository
import app.knotwork.android.domain.repositories.SettingsRepository
import app.knotwork.android.domain.repositories.ToolRepository
import app.knotwork.android.domain.services.NativeMemorySampler
import app.knotwork.android.domain.usecases.LoadModelUseCase
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class LiteRtNodeExecutorTest {

    private lateinit var llmEngine: LlmInferenceEngine
    private lateinit var toolRepository: ToolRepository
    private lateinit var chatRepository: ChatRepository
    private lateinit var settingsRepository: SettingsRepository
    private lateinit var metricsRepository: MetricsRepository
    private lateinit var modelPerformanceRepository: ModelPerformanceRepository
    private lateinit var nativeMemorySampler: NativeMemorySampler
    private lateinit var loadModelUseCase: LoadModelUseCase
    private lateinit var executor: LiteRtNodeExecutor

    @Before
    fun setup() {
        llmEngine = mockk()
        toolRepository = mockk()
        chatRepository = mockk(relaxed = true)
        settingsRepository = mockk()
        metricsRepository = mockk(relaxed = true)
        modelPerformanceRepository = mockk(relaxed = true)
        nativeMemorySampler = mockk(relaxed = true)
        loadModelUseCase = mockk()

        // The executor reads the engine's concrete loaded path after load to key
        // the performance sample.
        every { llmEngine.currentModelPath } returns "/models/active.litertlm"

        executor = LiteRtNodeExecutor(
            llmEngine,
            toolRepository,
            chatRepository,
            settingsRepository,
            metricsRepository,
            modelPerformanceRepository,
            nativeMemorySampler,
            loadModelUseCase,
        )
    }

    @Test
    fun `execute forms prompt using node systemPrompt`() = runTest {
        val node = NodeModel("1", NodeType.LITE_RT, 0f, 0f, systemPrompt = "Custom Node Prompt")

        every { settingsRepository.systemPromptPrefix } returns flowOf("Prefix")
        coEvery { loadModelUseCase(any()) } returns Result.Success(Unit)

        val promptSlot = slot<String>()
        every { llmEngine.generateResponseStream(capture(promptSlot)) } returns flowOf("Result")

        executor.execute(node, "input", "session-1", "prompt").toList()

        val capturedPrompt = promptSlot.captured
        assertTrue(capturedPrompt.contains("Custom Node Prompt"))
        // The executor no longer wraps inputText in "USER/INPUT: " — it now passes the
        // already-assembled context produced upstream by NodeContextBuilder verbatim.
        assertTrue(capturedPrompt.contains("input"))
        assertTrue(capturedPrompt.endsWith("AGENT: "))
    }

    @Test
    fun `execute forms prompt using default systemPrompt when node prompt is empty`() = runTest {
        val node = NodeModel("1", NodeType.LITE_RT, 0f, 0f)

        every { settingsRepository.systemPromptPrefix } returns flowOf("Prefix")
        coEvery { loadModelUseCase(any()) } returns Result.Success(Unit)

        val promptSlot = slot<String>()
        every { llmEngine.generateResponseStream(capture(promptSlot)) } returns flowOf("Result")

        executor.execute(node, "input", "session-1", "prompt").toList()

        val capturedPrompt = promptSlot.captured
        assertTrue(capturedPrompt.contains("You are a helpful AI assistant"))
    }

    @Test
    fun `execute does not refetch chat history or long-term memory inside the node`() = runTest {
        // Defect 1 regression guard: the engine assembles the context upstream and the
        // executor must consume `inputText` verbatim. The legacy executor refetched
        // `getContextWindowUseCase` and `retrieveRelevantMemoryUseCase`, doubly inflating
        // the prompt and silently overriding NodeContextConfig.
        val node = NodeModel("1", NodeType.LITE_RT, 0f, 0f, systemPrompt = "Sys")
        every { settingsRepository.systemPromptPrefix } returns flowOf("")
        coEvery { loadModelUseCase(any()) } returns Result.Success(Unit)

        val promptSlot = slot<String>()
        every { llmEngine.generateResponseStream(capture(promptSlot)) } returns flowOf("ok")

        val assembledContext = "--- Original Task ---\nthe task\n\n--- Previous Node Output ---\nupstream"
        executor.execute(node, assembledContext, "session-1", "the task").toList()

        val capturedPrompt = promptSlot.captured
        // The executor must forward the assembled context verbatim and must not re-inject
        // "RELEVANT LONG-TERM MEMORIES:" headers or any chat history block of its own.
        assertTrue(capturedPrompt.contains(assembledContext))
        assertFalse(capturedPrompt.contains("RELEVANT LONG-TERM MEMORIES:"))
    }

    @Test
    fun `execute records tokenCount equal to number of stream emissions`() = runTest {
        // Defect 7 regression guard: each streamed token from LiteRT is one model token,
        // not a string of arbitrary length. Counting by `+= 1` keeps the metric truthful.
        val node = NodeModel("1", NodeType.LITE_RT, 0f, 0f)
        every { settingsRepository.systemPromptPrefix } returns flowOf("")
        coEvery { loadModelUseCase(any()) } returns Result.Success(Unit)

        // Emit five tokens of varying lengths — the count must equal 5, not the sum of lengths.
        every { llmEngine.generateResponseStream(any()) } returns flowOf("a", "bb", "ccc", "dddd", "eeeee")

        val outputs = executor.execute(node, "input", "session-1", "prompt").toList()

        val result = outputs.filterIsInstance<NodeOutput.Result>().single().result
        assertEquals(5, result.tokenCount)
    }

    @Test
    fun `given scope carries an image path when execute then engine and loader run in vision mode`() = runTest {
        val node = NodeModel("1", NodeType.LITE_RT, 0f, 0f, modelPath = "model-path")
        every { settingsRepository.systemPromptPrefix } returns flowOf("")
        coEvery { loadModelUseCase(any(), any()) } returns Result.Success(Unit)
        every { llmEngine.generateResponseStream(any(), any(), any()) } returns flowOf("ok")

        executor.execute(
            node = node,
            inputText = "input",
            sessionId = "session-1",
            originalPrompt = "prompt",
            scope = ExecutionScope(imagePath = "/abs/photo.jpg"),
        ).toList()

        // The image path the engine chose for this node must reach both the model
        // loader (so it initialises in vision mode) and the generation call.
        coVerify { loadModelUseCase("model-path", requireVision = true) }
        verify { llmEngine.generateResponseStream(any(), "/abs/photo.jpg", any()) }
    }

    @Test
    fun `given no image path when execute then engine and loader run in text mode`() = runTest {
        val node = NodeModel("1", NodeType.LITE_RT, 0f, 0f, modelPath = "model-path")
        every { settingsRepository.systemPromptPrefix } returns flowOf("")
        coEvery { loadModelUseCase(any(), any()) } returns Result.Success(Unit)
        every { llmEngine.generateResponseStream(any(), any(), any()) } returns flowOf("ok")

        // Default scope → no image: a text node must never request vision nor pass
        // an image to the engine.
        executor.execute(node, "input", "session-1", "prompt").toList()

        coVerify { loadModelUseCase("model-path", requireVision = false) }
        verify { llmEngine.generateResponseStream(any(), null, any()) }
    }

    @Test
    fun `execute records a performance sample keyed by the loaded model path`() = runTest {
        val node = NodeModel("1", NodeType.LITE_RT, 0f, 0f)
        every { settingsRepository.systemPromptPrefix } returns flowOf("")
        coEvery { loadModelUseCase(any()) } returns Result.Success(Unit)
        every { llmEngine.generateResponseStream(any()) } returns flowOf("a", "b", "c")

        executor.execute(node, "input", "session-1", "prompt").toList()

        val sampleSlot = slot<ModelPerformanceSample>()
        coVerify { modelPerformanceRepository.record(capture(sampleSlot)) }
        val sample = sampleSlot.captured
        assertEquals("/models/active.litertlm", sample.modelPath)
        assertEquals(3, sample.tokenCount)
        assertFalse(sample.isBenchmark)
    }

    @Test
    fun `execute does not record a sample when the generation produced no tokens`() = runTest {
        val node = NodeModel("1", NodeType.LITE_RT, 0f, 0f)
        every { settingsRepository.systemPromptPrefix } returns flowOf("")
        coEvery { loadModelUseCase(any()) } returns Result.Success(Unit)
        // Empty stream → zero tokens → no useful timing → skip recording.
        every { llmEngine.generateResponseStream(any()) } returns flowOf()

        executor.execute(node, "input", "session-1", "prompt").toList()

        coVerify(exactly = 0) { modelPerformanceRepository.record(any()) }
    }

    @Test
    fun `execute does not record a sample when the engine reports no loaded path`() = runTest {
        val node = NodeModel("1", NodeType.LITE_RT, 0f, 0f)
        every { settingsRepository.systemPromptPrefix } returns flowOf("")
        coEvery { loadModelUseCase(any()) } returns Result.Success(Unit)
        every { llmEngine.generateResponseStream(any()) } returns flowOf("token")
        every { llmEngine.currentModelPath } returns null

        executor.execute(node, "input", "session-1", "prompt").toList()

        coVerify(exactly = 0) { modelPerformanceRepository.record(any()) }
    }

    @Test
    fun `execute terminates with a NodeOutput Result and emits at least one State`() = runTest {
        val node = NodeModel("1", NodeType.LITE_RT, 0f, 0f)
        every { settingsRepository.systemPromptPrefix } returns flowOf("")
        coEvery { loadModelUseCase(any()) } returns Result.Success(Unit)
        every { llmEngine.generateResponseStream(any()) } returns flowOf("token")

        val outputs = executor.execute(node, "input", "session-1", "prompt").toList()

        // The terminal element must always be a NodeOutput.Result so the engine can
        // transition to the next node deterministically.
        assertTrue(outputs.last() is NodeOutput.Result)
        assertTrue(outputs.any { it is NodeOutput.State })
    }
}
