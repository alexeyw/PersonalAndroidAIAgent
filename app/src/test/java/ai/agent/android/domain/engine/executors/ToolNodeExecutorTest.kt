package ai.agent.android.domain.engine.executors

import ai.agent.android.domain.engine.LlmInferenceEngine
import ai.agent.android.domain.models.AgentTool
import ai.agent.android.domain.models.NodeModel
import ai.agent.android.domain.models.NodeType
import ai.agent.android.domain.models.Result
import ai.agent.android.domain.repositories.ChatRepository
import ai.agent.android.domain.repositories.SettingsRepository
import ai.agent.android.domain.repositories.ToolRepository
import ai.agent.android.domain.services.ApprovalNotifier
import ai.agent.android.domain.usecases.LoadModelUseCase
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Unit tests for [ToolNodeExecutor].
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ToolNodeExecutorTest {

    private lateinit var llmEngine: LlmInferenceEngine
    private lateinit var loadModelUseCase: LoadModelUseCase
    private lateinit var toolRepository: ToolRepository
    private lateinit var settingsRepository: SettingsRepository
    private lateinit var approvalNotifier: ApprovalNotifier
    private lateinit var chatRepository: ChatRepository
    private lateinit var executor: ToolNodeExecutor

    @Before
    fun setup() {
        llmEngine = mockk(relaxed = true)
        loadModelUseCase = mockk()
        toolRepository = mockk(relaxed = true)
        settingsRepository = mockk(relaxed = true)
        approvalNotifier = mockk(relaxed = true)
        chatRepository = mockk(relaxed = true)

        executor = ToolNodeExecutor(
            llmEngine = llmEngine,
            loadModelUseCase = loadModelUseCase,
            toolRepository = toolRepository,
            settingsRepository = settingsRepository,
            approvalNotifier = approvalNotifier,
            chatRepository = chatRepository,
        )

        coEvery { loadModelUseCase(any()) } returns Result.Success(Unit)
        every { settingsRepository.requiresUserConfirmation } returns flowOf(false)
        every { settingsRepository.toolCallTimeoutMs } returns flowOf(60_000L)
    }

    @Test
    fun `parseToolSelection returns pair for valid JSON`() {
        val response = """{"tool": "search", "arguments": "how to build android app"}"""
        val parsed = executor.parseToolSelection(response)
        assertEquals("search", parsed?.first)
        assertEquals("how to build android app", parsed?.second)
    }

    @Test
    fun `parseToolArguments extracts string arguments correctly`() {
        val response = """
            ```json
            {
                "tool": "search",
                "arguments": "how to build android app"
            }
            ```
        """.trimIndent()
        assertEquals("how to build android app", executor.parseToolArguments(response))
    }

    @Test
    fun `parseToolSelection extracts nested json object arguments correctly`() {
        val response = """
            I will use the tool now.
            ```json
            {
                "tool": "create_event",
                "arguments": {
                    "title": "Meeting",
                    "time": "10:00 AM"
                }
            }
            ```
            Hope this works.
        """.trimIndent()
        val result = executor.parseToolSelection(response)
        assertEquals("create_event", result?.first)
        val resultObj = org.json.JSONObject(result?.second!!)
        assertEquals("Meeting", resultObj.getString("title"))
        assertEquals("10:00 AM", resultObj.getString("time"))
    }

    @Test
    fun `execute uses LLM to generate arguments for specific tool`() = runTest {
        val toolName = "MyTool"
        val node = NodeModel("1", NodeType.TOOL, 0f, 0f, toolName = toolName)
        coEvery { toolRepository.getAvailableTools() } returns listOf(AgentTool(toolName, "Desc", "Schema"))
        every { llmEngine.generateResponseStream(any()) } returns
            flowOf("""{"tool": "MyTool", "arguments": "arg_value"}""")
        coEvery { toolRepository.executeTool(toolName, "arg_value") } returns "Tool Success"

        val states = executor.execute(node, "Do something", "session-1", "").toList().unwrap()

        // Checking last state
        val lastState = states.last() as ai.agent.android.domain.models.NodeExecutionResult
        assertEquals("Tool Success", lastState.outputText)
    }

    @Test
    fun `given approval times out when waiting for user response then emits timeout error`() = runTest {
        every { settingsRepository.requiresUserConfirmation } returns flowOf(true)
        every { settingsRepository.toolCallTimeoutMs } returns flowOf(100L)

        val toolName = "MyTool"
        val node = NodeModel("1", NodeType.TOOL, 0f, 0f, toolName = toolName)
        coEvery { toolRepository.getAvailableTools() } returns listOf(AgentTool(toolName, "Desc", "Schema"))
        every { llmEngine.generateResponseStream(any()) } returns flowOf("""{"tool": "MyTool", "arguments": "args"}""")

        val results = mutableListOf<Any>()
        val job = launch {
            executor.execute(node, "Do something", "session-1", "").collect { output ->
                when (output) {
                    is ai.agent.android.domain.models.NodeOutput.State -> results.add(output.state)
                    is ai.agent.android.domain.models.NodeOutput.Result -> results.add(output.result)
                }
            }
        }

        advanceTimeBy(200L)
        advanceUntilIdle()

        val lastResult = results.filterIsInstance<ai.agent.android.domain.models.NodeExecutionResult>().lastOrNull()
        assertNotNull("Expected NodeExecutionResult with error", lastResult)
        assertNotNull("Expected error field to be set", lastResult?.error)
        assertTrue(lastResult!!.error!!.contains("timed out", ignoreCase = true))
        job.cancel()
    }

    @Test
    fun `execute uses LLM for auto mode to select tool and generate arguments`() = runTest {
        val node = NodeModel("1", NodeType.TOOL, 0f, 0f, toolName = "auto")
        coEvery { toolRepository.getAvailableTools() } returns listOf(
            AgentTool("ToolA", "DescA", "SchemaA"),
            AgentTool("ToolB", "DescB", "SchemaB"),
        )
        every { llmEngine.generateResponseStream(any()) } returns flowOf("""{"tool": "ToolB", "arguments": "arg_b"}""")
        coEvery { toolRepository.executeTool("ToolB", "arg_b") } returns "Tool B Success"

        val states = executor.execute(node, "Do B", "session-1", "").toList().unwrap()

        val lastState = states.last() as ai.agent.android.domain.models.NodeExecutionResult
        assertEquals("Tool B Success", lastState.outputText)
    }
}
