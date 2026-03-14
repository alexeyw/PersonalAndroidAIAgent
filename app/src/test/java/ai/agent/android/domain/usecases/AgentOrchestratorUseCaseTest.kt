package ai.agent.android.domain.usecases

import ai.agent.android.domain.engine.LlmInferenceEngine
import ai.agent.android.domain.models.AgentOrchestratorState
import ai.agent.android.domain.models.AgentTool
import ai.agent.android.domain.repositories.ChatRepository
import ai.agent.android.domain.repositories.MetricsRepository
import ai.agent.android.domain.repositories.SettingsRepository
import ai.agent.android.domain.repositories.ToolRepository
import ai.agent.android.domain.constants.DefaultPrompts
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class AgentOrchestratorUseCaseTest {

    private lateinit var llmEngine: LlmInferenceEngine
    private lateinit var toolRepository: ToolRepository
    private lateinit var chatRepository: ChatRepository
    private lateinit var getContextWindowUseCase: GetContextWindowUseCase
    private lateinit var settingsRepository: SettingsRepository
    private lateinit var metricsRepository: MetricsRepository
    private lateinit var useCase: AgentOrchestratorUseCase

    private val sessionId = "test-session"

    @Before
    fun setup() {
        llmEngine = mockk()
        toolRepository = mockk()
        chatRepository = mockk(relaxed = true)
        getContextWindowUseCase = mockk()
        settingsRepository = mockk()
        metricsRepository = mockk(relaxed = true)
        useCase = AgentOrchestratorUseCase(llmEngine, toolRepository, chatRepository, getContextWindowUseCase, settingsRepository, metricsRepository)

        coEvery { toolRepository.getAvailableTools() } returns listOf(
            AgentTool("test_tool", "A test tool", "{}")
        )
        coEvery { getContextWindowUseCase(sessionId) } returns "USER: hello"
        every { settingsRepository.systemPromptPrefix } returns flowOf(DefaultPrompts.SYSTEM_PROMPT_PREFIX)
        every { settingsRepository.toolUsageInstruction } returns flowOf(DefaultPrompts.TOOL_USAGE_INSTRUCTION)
        every { settingsRepository.requiresUserConfirmation } returns flowOf(false)
    }

    @Test
    fun `direct answer without tool call`() = runTest {
        val userPrompt = "Hello"
        val llmResponse = "Hi there!"

        every { llmEngine.generateResponseStream(any()) } returns flowOf("Hi ", "there!")

        val states = useCase(sessionId, userPrompt).toList()

        assertTrue(states[0] is AgentOrchestratorState.Loading)
        assertTrue(states[1] is AgentOrchestratorState.Thinking)
        assertEquals("Hi ", (states[1] as AgentOrchestratorState.Thinking).partialText)
        assertTrue(states[2] is AgentOrchestratorState.Answering)
        assertEquals("Hi there!", (states[2] as AgentOrchestratorState.Answering).partialText)
        assertTrue(states[3] is AgentOrchestratorState.Completed)
        assertEquals(llmResponse, (states[3] as AgentOrchestratorState.Completed).finalResponse)

        coVerify { chatRepository.saveMessage(match { it.content == userPrompt }) }
        coVerify { chatRepository.saveMessage(match { it.content == llmResponse }) }
    }

    @Test
    fun `tool call followed by answer`() = runTest {
        val userPrompt = "Do something"
        val toolCallResponse = """
            Thought: I should use the test tool.
            ```json
            {
              "tool": "test_tool",
              "arguments": "{\"arg\":\"value\"}"
            }
            ```
        """.trimIndent()
        val finalResponse = "Tool execution completed."

        // Mock two iterations: first returns tool call, second returns final answer
        var iteration = 0
        every { llmEngine.generateResponseStream(any()) } answers {
            if (iteration == 0) {
                iteration++
                flowOf(toolCallResponse)
            } else {
                flowOf(finalResponse)
            }
        }

        coEvery { toolRepository.executeTool("test_tool", "{\"arg\":\"value\"}") } returns "Success"

        val states = useCase(sessionId, userPrompt).toList()

        // Just check that we got an ExecutingTool state, an ObservationResult state, and Completed
        val executingToolState = states.filterIsInstance<AgentOrchestratorState.ExecutingTool>().firstOrNull()
        assertTrue(executingToolState != null)
        assertEquals("test_tool", executingToolState?.toolName)

        val observationState = states.filterIsInstance<AgentOrchestratorState.ObservationResult>().firstOrNull()
        assertTrue(observationState != null)
        assertEquals("Success", observationState?.result)

        val completedState = states.filterIsInstance<AgentOrchestratorState.Completed>().firstOrNull()
        assertTrue(completedState != null)
        assertEquals(finalResponse, completedState?.finalResponse)

        coVerify { toolRepository.executeTool("test_tool", "{\"arg\":\"value\"}") }
    }

    @Test
    fun `reaches max iterations and stops`() = runTest {
        val userPrompt = "Infinite loop test"
        val toolCallResponse = """
            ```json
            {
              "tool": "test_tool",
              "arguments": "{}"
            }
            ```
        """.trimIndent()

        // Always return a tool call
        every { llmEngine.generateResponseStream(any()) } returns flowOf(toolCallResponse)
        coEvery { toolRepository.executeTool(any(), any()) } returns "Looping"

        val states = useCase(sessionId, userPrompt).toList()

        val errors = states.filterIsInstance<AgentOrchestratorState.Error>()
        assertTrue(errors.isNotEmpty())
        assertTrue(errors.first().message.contains("maximum iterations"))
    }

    @Test
    fun `emits RequiresUserConfirmation and stops when setting is true`() = runTest {
        every { settingsRepository.requiresUserConfirmation } returns flowOf(true)

        val userPrompt = "Do something dangerous"
        val toolCallResponse = """
            Thought: I need permission for this.
            ```json
            {
              "tool": "test_tool",
              "arguments": "{\"arg\":\"danger\"}"
            }
            ```
        """.trimIndent()

        every { llmEngine.generateResponseStream(any()) } returns flowOf(toolCallResponse)

        val states = useCase(sessionId, userPrompt).toList()

        val confirmationState = states.filterIsInstance<AgentOrchestratorState.RequiresUserConfirmation>().firstOrNull()
        assertTrue(confirmationState != null)
        assertEquals("test_tool", confirmationState?.toolName)
        assertEquals("{\"arg\":\"danger\"}", confirmationState?.arguments)

        // It should NOT execute the tool or emit ObservationResult
        val executingState = states.filterIsInstance<AgentOrchestratorState.ExecutingTool>().firstOrNull()
        assertTrue(executingState == null)
        
        coVerify(exactly = 0) { toolRepository.executeTool(any(), any()) }
    }
}
