package ai.agent.android.domain.engine.executors

import ai.agent.android.domain.engine.LlmInferenceEngine
import ai.agent.android.domain.models.AgentOrchestratorState
import ai.agent.android.domain.models.AgentTool
import ai.agent.android.domain.models.NodeExecutionResult
import ai.agent.android.domain.models.NodeModel
import ai.agent.android.domain.models.NodeOutput
import ai.agent.android.domain.models.NodeType
import ai.agent.android.domain.models.Result
import ai.agent.android.domain.models.ToolApprovalPolicy
import ai.agent.android.domain.models.ToolRisk
import ai.agent.android.domain.repositories.ChatRepository
import ai.agent.android.domain.repositories.SettingsRepository
import ai.agent.android.domain.repositories.ToolRepository
import ai.agent.android.domain.services.ApprovalNotifier
import ai.agent.android.domain.usecases.LoadModelUseCase
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
        every { settingsRepository.toolApprovalPolicy } returns flowOf(ToolApprovalPolicy.SensitiveOrDestructive)
        every { settingsRepository.blockDestructiveTools } returns flowOf(false)
        every { settingsRepository.toolCallTimeoutMs } returns flowOf(60_000L)
        // Default risk for tests that don't care about the HITL gate. Individual
        // tests override `getRisk(...)` to drive the gate explicitly.
        coEvery { toolRepository.getRisk(any()) } returns ToolRisk.READ_ONLY
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
        val lastState = states.last() as NodeExecutionResult
        assertEquals("Tool Success", lastState.outputText)
    }

    @Test
    fun `given approval times out when waiting for user response then emits timeout error`() = runTest {
        every { settingsRepository.toolCallTimeoutMs } returns flowOf(100L)
        coEvery { toolRepository.getRisk(any()) } returns ToolRisk.SENSITIVE

        val toolName = "MyTool"
        val node = NodeModel("1", NodeType.TOOL, 0f, 0f, toolName = toolName)
        coEvery { toolRepository.getAvailableTools() } returns listOf(AgentTool(toolName, "Desc", "Schema"))
        every { llmEngine.generateResponseStream(any()) } returns flowOf("""{"tool": "MyTool", "arguments": "args"}""")

        val results = mutableListOf<Any>()
        val job = launch {
            executor.execute(node, "Do something", "session-1", "").collect { output ->
                when (output) {
                    is NodeOutput.State -> results.add(output.state)
                    is NodeOutput.Result -> results.add(output.result)
                }
            }
        }

        advanceTimeBy(200L)
        advanceUntilIdle()

        val lastResult = results.filterIsInstance<NodeExecutionResult>().lastOrNull()
        assertNotNull("Expected NodeExecutionResult with error", lastResult)
        assertNotNull("Expected error field to be set", lastResult?.error)
        assertTrue(lastResult!!.error!!.contains("timed out", ignoreCase = true))
        job.cancel()
    }

    @Test
    fun `given READ_ONLY tool and global override off when execute then no approval emitted`() = runTest {
        every { settingsRepository.toolApprovalPolicy } returns flowOf(ToolApprovalPolicy.SensitiveOrDestructive)
        every { settingsRepository.blockDestructiveTools } returns flowOf(false)
        coEvery { toolRepository.getRisk(any()) } returns ToolRisk.READ_ONLY

        val toolName = "ReadTool"
        val node = NodeModel("1", NodeType.TOOL, 0f, 0f, toolName = toolName)
        coEvery { toolRepository.getAvailableTools() } returns listOf(AgentTool(toolName, "Desc", "Schema"))
        every { llmEngine.generateResponseStream(any()) } returns
            flowOf("""{"tool": "ReadTool", "arguments": "args"}""")
        coEvery { toolRepository.executeTool(toolName, "args") } returns "ok"

        val states = executor.execute(node, "Read", "session-1", "").toList().unwrap()

        assertFalse(
            "READ_ONLY tool with global override OFF must not emit WaitingForApproval",
            states.any { it is AgentOrchestratorState.WaitingForApproval },
        )
        verify(exactly = 0) { approvalNotifier.sendApprovalRequest(any(), any(), any(), any()) }
        val last = states.last() as NodeExecutionResult
        assertEquals("ok", last.outputText)
    }

    @Test
    fun `given READ_ONLY tool and global override on when execute then approval emitted with READ_ONLY risk`() =
        runTest {
            every { settingsRepository.toolApprovalPolicy } returns flowOf(ToolApprovalPolicy.AllCalls)
            every { settingsRepository.blockDestructiveTools } returns flowOf(false)
            every { settingsRepository.toolCallTimeoutMs } returns flowOf(100L)
            coEvery { toolRepository.getRisk(any()) } returns ToolRisk.READ_ONLY

            val toolName = "ReadTool"
            val node = NodeModel("1", NodeType.TOOL, 0f, 0f, toolName = toolName)
            coEvery { toolRepository.getAvailableTools() } returns listOf(AgentTool(toolName, "Desc", "Schema"))
            every { llmEngine.generateResponseStream(any()) } returns
                flowOf("""{"tool": "ReadTool", "arguments": "args"}""")

            val results = mutableListOf<Any>()
            val job = launch {
                executor.execute(node, "Read", "session-1", "").collect { output ->
                    when (output) {
                        is NodeOutput.State -> results.add(output.state)
                        is NodeOutput.Result -> results.add(output.result)
                    }
                }
            }
            advanceTimeBy(200L)
            advanceUntilIdle()

            val waiting = results.filterIsInstance<AgentOrchestratorState.WaitingForApproval>().firstOrNull()
            assertNotNull("Global override must force HITL prompt for READ_ONLY", waiting)
            assertEquals(ToolRisk.READ_ONLY, waiting!!.risk)
            verify(exactly = 1) {
                approvalNotifier.sendApprovalRequest("session-1", toolName, "args", ToolRisk.READ_ONLY)
            }
            job.cancel()
        }

    @Test
    fun `given SENSITIVE tool and global override off when execute then approval emitted with SENSITIVE risk`() =
        runTest {
            every { settingsRepository.toolApprovalPolicy } returns flowOf(ToolApprovalPolicy.SensitiveOrDestructive)
            every { settingsRepository.blockDestructiveTools } returns flowOf(false)
            every { settingsRepository.toolCallTimeoutMs } returns flowOf(100L)
            coEvery { toolRepository.getRisk(any()) } returns ToolRisk.SENSITIVE

            val toolName = "SensTool"
            val node = NodeModel("1", NodeType.TOOL, 0f, 0f, toolName = toolName)
            coEvery { toolRepository.getAvailableTools() } returns listOf(AgentTool(toolName, "Desc", "Schema"))
            every { llmEngine.generateResponseStream(any()) } returns
                flowOf("""{"tool": "SensTool", "arguments": "args"}""")

            val results = mutableListOf<Any>()
            val job = launch {
                executor.execute(node, "Do", "session-1", "").collect { output ->
                    when (output) {
                        is NodeOutput.State -> results.add(output.state)
                        is NodeOutput.Result -> results.add(output.result)
                    }
                }
            }
            advanceTimeBy(200L)
            advanceUntilIdle()

            val waiting = results.filterIsInstance<AgentOrchestratorState.WaitingForApproval>().firstOrNull()
            assertNotNull("SENSITIVE tools must always trigger HITL prompt", waiting)
            assertEquals(ToolRisk.SENSITIVE, waiting!!.risk)
            verify(exactly = 1) {
                approvalNotifier.sendApprovalRequest("session-1", toolName, "args", ToolRisk.SENSITIVE)
            }
            job.cancel()
        }

    @Test
    fun `given DESTRUCTIVE tool and global override off when execute then approval emitted with DESTRUCTIVE risk`() =
        runTest {
            every { settingsRepository.toolApprovalPolicy } returns flowOf(ToolApprovalPolicy.SensitiveOrDestructive)
            every { settingsRepository.blockDestructiveTools } returns flowOf(false)
            every { settingsRepository.toolCallTimeoutMs } returns flowOf(100L)
            coEvery { toolRepository.getRisk(any()) } returns ToolRisk.DESTRUCTIVE

            val toolName = "DestTool"
            val node = NodeModel("1", NodeType.TOOL, 0f, 0f, toolName = toolName)
            coEvery { toolRepository.getAvailableTools() } returns listOf(AgentTool(toolName, "Desc", "Schema"))
            every { llmEngine.generateResponseStream(any()) } returns
                flowOf("""{"tool": "DestTool", "arguments": "args"}""")

            val results = mutableListOf<Any>()
            val job = launch {
                executor.execute(node, "Do", "session-1", "").collect { output ->
                    when (output) {
                        is NodeOutput.State -> results.add(output.state)
                        is NodeOutput.Result -> results.add(output.result)
                    }
                }
            }
            advanceTimeBy(200L)
            advanceUntilIdle()

            val waiting = results.filterIsInstance<AgentOrchestratorState.WaitingForApproval>().firstOrNull()
            assertNotNull("DESTRUCTIVE tools must always trigger HITL prompt", waiting)
            assertEquals(ToolRisk.DESTRUCTIVE, waiting!!.risk)
            verify(exactly = 1) {
                approvalNotifier.sendApprovalRequest("session-1", toolName, "args", ToolRisk.DESTRUCTIVE)
            }
            job.cancel()
        }

    @Test
    fun `given getRisk throws when execute then emits structured error result`() = runTest {
        coEvery { toolRepository.getRisk(any()) } throws IllegalArgumentException("Unknown tool: HallucinatedTool")

        val toolName = "HallucinatedTool"
        val node = NodeModel("1", NodeType.TOOL, 0f, 0f, toolName = toolName)
        coEvery { toolRepository.getAvailableTools() } returns listOf(AgentTool(toolName, "Desc", "Schema"))
        every { llmEngine.generateResponseStream(any()) } returns
            flowOf("""{"tool": "HallucinatedTool", "arguments": "args"}""")

        val states = executor.execute(node, "Do", "session-1", "").toList().unwrap()

        val finalResult = states.filterIsInstance<NodeExecutionResult>().lastOrNull()
        assertNotNull(finalResult)
        assertNotNull("getRisk failure must surface as a structured error", finalResult!!.error)
        assertTrue(finalResult.error!!.contains("Risk lookup failed", ignoreCase = true))
        coVerify(exactly = 0) { toolRepository.executeTool(any(), any()) }
        verify(exactly = 0) { approvalNotifier.sendApprovalRequest(any(), any(), any(), any()) }
    }

    @Test
    fun `given SENSITIVE tool and user denies when execute then emits execution denied observation`() = runTest {
        every { settingsRepository.toolApprovalPolicy } returns flowOf(ToolApprovalPolicy.SensitiveOrDestructive)
        every { settingsRepository.blockDestructiveTools } returns flowOf(false)
        every { settingsRepository.toolCallTimeoutMs } returns flowOf(5_000L)
        coEvery { toolRepository.getRisk(any()) } returns ToolRisk.SENSITIVE

        val toolName = "SensTool"
        val node = NodeModel("1", NodeType.TOOL, 0f, 0f, toolName = toolName)
        coEvery { toolRepository.getAvailableTools() } returns listOf(AgentTool(toolName, "Desc", "Schema"))
        every { llmEngine.generateResponseStream(any()) } returns
            flowOf("""{"tool": "SensTool", "arguments": "args"}""")

        val results = mutableListOf<Any>()
        val job = launch {
            executor.execute(node, "Do", "session-1", "").collect { output ->
                when (output) {
                    is NodeOutput.State -> results.add(output.state)
                    is NodeOutput.Result -> results.add(output.result)
                }
            }
        }
        // Flush pending tasks WITHOUT advancing virtual time so the executor
        // suspends inside withTimeout(...) without firing the 5s timeout.
        runCurrent()
        executor.resumeWithApproval("session-1", isApproved = false)
        advanceUntilIdle()

        val finalResult = results.filterIsInstance<NodeExecutionResult>().lastOrNull()
        assertNotNull(finalResult)
        assertEquals("Execution denied by user", finalResult!!.outputText)
        coVerify(exactly = 0) { toolRepository.executeTool(any(), any()) }
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

        val lastState = states.last() as NodeExecutionResult
        assertEquals("Tool B Success", lastState.outputText)
    }
}
