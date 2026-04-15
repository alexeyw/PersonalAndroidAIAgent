package ai.agent.android.domain.engine.executors

import ai.agent.android.domain.engine.LlmInferenceEngine
import ai.agent.android.domain.models.AgentOrchestratorState
import ai.agent.android.domain.models.NodeExecutionResult
import ai.agent.android.domain.models.NodeModel
import ai.agent.android.domain.models.NodeType
import ai.agent.android.domain.models.AgentTool
import ai.agent.android.domain.repositories.ChatRepository
import ai.agent.android.domain.repositories.MetricsRepository
import ai.agent.android.domain.repositories.SettingsRepository
import ai.agent.android.domain.repositories.ToolRepository
import ai.agent.android.domain.usecases.GetContextWindowUseCase
import ai.agent.android.domain.usecases.LoadModelUseCase
import ai.agent.android.domain.usecases.RetrieveRelevantMemoryUseCase
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class LiteRtNodeExecutorTest {

    private lateinit var llmEngine: LlmInferenceEngine
    private lateinit var toolRepository: ToolRepository
    private lateinit var chatRepository: ChatRepository
    private lateinit var getContextWindowUseCase: GetContextWindowUseCase
    private lateinit var retrieveRelevantMemoryUseCase: RetrieveRelevantMemoryUseCase
    private lateinit var settingsRepository: SettingsRepository
    private lateinit var metricsRepository: MetricsRepository
    private lateinit var loadModelUseCase: LoadModelUseCase
    private lateinit var executor: LiteRtNodeExecutor

    @Before
    fun setup() {
        llmEngine = mockk()
        toolRepository = mockk()
        chatRepository = mockk(relaxed = true)
        getContextWindowUseCase = mockk()
        retrieveRelevantMemoryUseCase = mockk()
        settingsRepository = mockk()
        metricsRepository = mockk(relaxed = true)
        loadModelUseCase = mockk()

        executor = LiteRtNodeExecutor(
            llmEngine,
            toolRepository,
            chatRepository,
            getContextWindowUseCase,
            retrieveRelevantMemoryUseCase,
            settingsRepository,
            metricsRepository,
            loadModelUseCase
        )
    }

    @Test
    fun `execute forms prompt using node systemPrompt`() = runTest {
        val node = NodeModel("1", NodeType.LITE_RT, 0f, 0f, systemPrompt = "Custom Node Prompt")
        
        every { settingsRepository.systemPromptPrefix } returns flowOf("Prefix")
        coEvery { getContextWindowUseCase(any()) } returns "Context Window"
        coEvery { retrieveRelevantMemoryUseCase(any()) } returns emptyList()
        coEvery { loadModelUseCase(any()) } returns ai.agent.android.domain.models.Result.Success(Unit)
        
        val promptSlot = slot<String>()
        every { llmEngine.generateResponseStream(capture(promptSlot)) } returns flowOf("Result")

        executor.execute(node, "input", "session-1", "prompt").toList()

        val capturedPrompt = promptSlot.captured
        assertTrue(capturedPrompt.contains("Custom Node Prompt"))
        assertTrue(capturedPrompt.contains("USER/INPUT: input"))
    }

    @Test
    fun `execute forms prompt using default systemPrompt when node prompt is empty`() = runTest {
        val node = NodeModel("1", NodeType.LITE_RT, 0f, 0f)
        
        every { settingsRepository.systemPromptPrefix } returns flowOf("Prefix")
        coEvery { getContextWindowUseCase(any()) } returns "Context Window"
        coEvery { retrieveRelevantMemoryUseCase(any()) } returns emptyList()
        coEvery { loadModelUseCase(any()) } returns ai.agent.android.domain.models.Result.Success(Unit)
        
        val promptSlot = slot<String>()
        every { llmEngine.generateResponseStream(capture(promptSlot)) } returns flowOf("Result")

        executor.execute(node, "input", "session-1", "prompt").toList()

        val capturedPrompt = promptSlot.captured
        assertTrue(capturedPrompt.contains("You are a helpful AI assistant"))
    }
}
