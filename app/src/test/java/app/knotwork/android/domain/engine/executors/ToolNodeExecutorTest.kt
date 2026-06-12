package app.knotwork.android.domain.engine.executors

import app.knotwork.android.domain.engine.LlmInferenceEngine
import app.knotwork.android.domain.models.AgentOrchestratorState
import app.knotwork.android.domain.models.AgentTool
import app.knotwork.android.domain.models.NodeExecutionResult
import app.knotwork.android.domain.models.NodeModel
import app.knotwork.android.domain.models.NodeOutput
import app.knotwork.android.domain.models.NodeType
import app.knotwork.android.domain.models.PendingDecision
import app.knotwork.android.domain.models.PendingInteraction
import app.knotwork.android.domain.models.PendingInteractionKind
import app.knotwork.android.domain.models.Result
import app.knotwork.android.domain.models.ToolApprovalPolicy
import app.knotwork.android.domain.models.ToolExecutionContext
import app.knotwork.android.domain.models.ToolRisk
import app.knotwork.android.domain.repositories.ChatRepository
import app.knotwork.android.domain.repositories.PendingInteractionRepository
import app.knotwork.android.domain.repositories.SettingsRepository
import app.knotwork.android.domain.repositories.ToolRepository
import app.knotwork.android.domain.services.ApprovalNotifier
import app.knotwork.android.domain.usecases.LoadModelUseCase
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
import org.junit.Assert.assertNull
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
    private lateinit var pendingInteractionRepository: PendingInteractionRepository
    private lateinit var executor: ToolNodeExecutor

    @Before
    fun setup() {
        llmEngine = mockk(relaxed = true)
        loadModelUseCase = mockk()
        toolRepository = mockk(relaxed = true)
        settingsRepository = mockk(relaxed = true)
        approvalNotifier = mockk(relaxed = true)
        chatRepository = mockk(relaxed = true)
        pendingInteractionRepository = mockk(relaxed = true)
        coEvery { pendingInteractionRepository.getForRun(any()) } returns null
        coEvery { pendingInteractionRepository.save(any()) } returns true

        executor = ToolNodeExecutor(
            llmEngine = llmEngine,
            loadModelUseCase = loadModelUseCase,
            toolRepository = toolRepository,
            settingsRepository = settingsRepository,
            approvalNotifier = approvalNotifier,
            chatRepository = chatRepository,
            pendingInteractionRepository = pendingInteractionRepository,
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
        coEvery { toolRepository.executeTool(toolName, "arg_value", any()) } returns "Tool Success"

        val states = executor.execute(node, "Do something", "session-1", "").toList().unwrap()

        // Checking last state
        val lastState = states.last() as NodeExecutionResult
        assertEquals("Tool Success", lastState.outputText)
    }

    @Test
    fun `execute passes the session id to the tool through the execution context`() = runTest {
        // schedule_task binds the scheduled run back to the conversation via this
        // context — the id must come from the engine, never from the LLM arguments.
        val toolName = "MyTool"
        val node = NodeModel("1", NodeType.TOOL, 0f, 0f, toolName = toolName)
        coEvery { toolRepository.getAvailableTools() } returns listOf(AgentTool(toolName, "Desc", "Schema"))
        every { llmEngine.generateResponseStream(any()) } returns
            flowOf("""{"tool": "MyTool", "arguments": "arg_value"}""")
        coEvery { toolRepository.executeTool(any(), any(), any()) } returns "ok"

        executor.execute(node, "Do something", "session-77", "").toList()

        coVerify(exactly = 1) {
            toolRepository.executeTool(toolName, "arg_value", ToolExecutionContext(sessionId = "session-77"))
        }
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
        coEvery { toolRepository.executeTool(toolName, "args", any()) } returns "ok"

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
    fun `given DESTRUCTIVE tool and blockDestructiveTools on when execute then emits error result and skips HITL`() =
        runTest {
            every { settingsRepository.toolApprovalPolicy } returns flowOf(ToolApprovalPolicy.SensitiveOrDestructive)
            every { settingsRepository.blockDestructiveTools } returns flowOf(true)
            coEvery { toolRepository.getRisk(any()) } returns ToolRisk.DESTRUCTIVE

            val toolName = "DestTool"
            val node = NodeModel("1", NodeType.TOOL, 0f, 0f, toolName = toolName)
            coEvery { toolRepository.getAvailableTools() } returns listOf(AgentTool(toolName, "Desc", "Schema"))
            every { llmEngine.generateResponseStream(any()) } returns
                flowOf("""{"tool": "DestTool", "arguments": "args"}""")

            val states = executor.execute(node, "Do", "session-1", "").toList().unwrap()

            val finalResult = states.filterIsInstance<NodeExecutionResult>().lastOrNull()
            assertNotNull("Policy-blocked destructive must surface a structured result", finalResult)
            assertNotNull(
                "Policy block must populate `error`, NOT `outputText`, so the orchestrator " +
                    "treats the node as failed and the planner does not retry.",
                finalResult!!.error,
            )
            assertTrue(finalResult.error!!.contains("blocked by Settings", ignoreCase = true))
            assertEquals(null, finalResult.outputText)
            coVerify(exactly = 0) { toolRepository.executeTool(any(), any(), any()) }
            verify(exactly = 0) { approvalNotifier.sendApprovalRequest(any(), any(), any(), any()) }
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
        coVerify(exactly = 0) { toolRepository.executeTool(any(), any(), any()) }
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
        coVerify(exactly = 0) { toolRepository.executeTool(any(), any(), any()) }
        job.cancel()
    }

    @Test
    fun `pendingApprovalFor exposes the suspended request and clears after resolution`() = runTest {
        every { settingsRepository.toolApprovalPolicy } returns flowOf(ToolApprovalPolicy.SensitiveOrDestructive)
        every { settingsRepository.blockDestructiveTools } returns flowOf(false)
        every { settingsRepository.toolCallTimeoutMs } returns flowOf(5_000L)
        coEvery { toolRepository.getRisk(any()) } returns ToolRisk.SENSITIVE

        val toolName = "SensTool"
        val node = NodeModel("1", NodeType.TOOL, 0f, 0f, toolName = toolName)
        coEvery { toolRepository.getAvailableTools() } returns listOf(AgentTool(toolName, "Desc", "Schema"))
        every { llmEngine.generateResponseStream(any()) } returns
            flowOf("""{"tool": "SensTool", "arguments": "args"}""")
        coEvery { toolRepository.executeTool(any(), any(), any()) } returns "ok"

        assertNull(executor.pendingApprovalFor("session-1"))

        val job = launch {
            executor.execute(node, "Do", "session-1", "").collect { }
        }
        runCurrent()

        // Suspended on the approval gate: the snapshot must be addressable.
        val pending = executor.pendingApprovalFor("session-1")
        assertNotNull(pending)
        assertEquals(toolName, pending!!.toolName)
        assertEquals(ToolRisk.SENSITIVE, pending.risk)
        assertNull("Other sessions must not see the request", executor.pendingApprovalFor("session-2"))

        executor.resumeWithApproval("session-1", isApproved = true)
        advanceUntilIdle()

        assertNull("Resolved request must be cleared", executor.pendingApprovalFor("session-1"))
        job.cancel()
    }

    @Test
    fun `pendingApprovalFor clears when the suspended gate is cancelled`() = runTest {
        every { settingsRepository.toolApprovalPolicy } returns flowOf(ToolApprovalPolicy.SensitiveOrDestructive)
        every { settingsRepository.blockDestructiveTools } returns flowOf(false)
        every { settingsRepository.toolCallTimeoutMs } returns flowOf(60_000L)
        coEvery { toolRepository.getRisk(any()) } returns ToolRisk.SENSITIVE

        val toolName = "SensTool"
        val node = NodeModel("1", NodeType.TOOL, 0f, 0f, toolName = toolName)
        coEvery { toolRepository.getAvailableTools() } returns listOf(AgentTool(toolName, "Desc", "Schema"))
        every { llmEngine.generateResponseStream(any()) } returns
            flowOf("""{"tool": "SensTool", "arguments": "args"}""")

        val job = launch {
            executor.execute(node, "Do", "session-1", "").collect { }
        }
        runCurrent()
        assertNotNull(executor.pendingApprovalFor("session-1"))

        // Plain cancellation of the suspended gate (scope teardown, an
        // abandoned editor test run) must not leak the holder — a stale
        // entry would serve a request no coroutine can ever settle.
        job.cancel()
        runCurrent()

        assertNull("Cancelled gate must clear its pending request", executor.pendingApprovalFor("session-1"))
    }

    @Test
    fun `pendingApprovalFor clears after the approval times out`() = runTest {
        every { settingsRepository.toolApprovalPolicy } returns flowOf(ToolApprovalPolicy.SensitiveOrDestructive)
        every { settingsRepository.blockDestructiveTools } returns flowOf(false)
        every { settingsRepository.toolCallTimeoutMs } returns flowOf(1_000L)
        coEvery { toolRepository.getRisk(any()) } returns ToolRisk.SENSITIVE

        val toolName = "SensTool"
        val node = NodeModel("1", NodeType.TOOL, 0f, 0f, toolName = toolName)
        coEvery { toolRepository.getAvailableTools() } returns listOf(AgentTool(toolName, "Desc", "Schema"))
        every { llmEngine.generateResponseStream(any()) } returns
            flowOf("""{"tool": "SensTool", "arguments": "args"}""")

        val job = launch {
            executor.execute(node, "Do", "session-1", "").collect { }
        }
        runCurrent()
        assertNotNull(executor.pendingApprovalFor("session-1"))

        advanceUntilIdle() // virtual time fires the 1s approval timeout

        assertNull("Timed-out request must be cleared", executor.pendingApprovalFor("session-1"))
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
        coEvery { toolRepository.executeTool("ToolB", "arg_b", any()) } returns "Tool B Success"

        val states = executor.execute(node, "Do B", "session-1", "").toList().unwrap()

        val lastState = states.last() as NodeExecutionResult
        assertEquals("Tool B Success", lastState.outputText)
    }

    @Test
    fun `execute treats a blank tool name as auto-select`() = runTest {
        // The editor's "Auto" tool option persists as a null / blank toolName
        // (NodeConfigCodec maps an empty toolId to null). It must behave like
        // the explicit "auto" sentinel — the LLM picks a tool — rather than
        // failing with "missing toolName configuration".
        listOf(null, "", "   ").forEach { blank ->
            val node = NodeModel("1", NodeType.TOOL, 0f, 0f, toolName = blank)
            coEvery { toolRepository.getAvailableTools() } returns listOf(
                AgentTool("ToolA", "DescA", "SchemaA"),
            )
            every { llmEngine.generateResponseStream(any()) } returns
                flowOf("""{"tool": "ToolA", "arguments": "arg_a"}""")
            coEvery { toolRepository.executeTool("ToolA", "arg_a", any()) } returns "Tool A Success"

            val states = executor.execute(node, "Do A", "session-1", "").toList().unwrap()

            val last = states.last() as NodeExecutionResult
            assertEquals(
                "blank toolName <$blank> should auto-select and run the tool",
                "Tool A Success",
                last.outputText,
            )
            assertNull("auto-select must not error for blank toolName <$blank>", last.error)
        }
    }

    // ─── Two-phase background waiting ───────────────────────────────────────

    /** Arms a SENSITIVE single-tool gate with a 100ms live window. */
    private fun armSensitiveGate(toolName: String = "MyTool") {
        every { settingsRepository.toolCallTimeoutMs } returns flowOf(100L)
        coEvery { toolRepository.getRisk(any()) } returns ToolRisk.SENSITIVE
        coEvery { toolRepository.getAvailableTools() } returns listOf(AgentTool(toolName, "Desc", "Schema"))
        every { llmEngine.generateResponseStream(any()) } returns
            flowOf("""{"tool": "$toolName", "arguments": "args"}""")
    }

    @Test
    fun `given live timeout on a persisted run when execute then parks instead of failing`() = runTest {
        armSensitiveGate()
        val node = NodeModel("1", NodeType.TOOL, 0f, 0f, toolName = "MyTool")

        val results = mutableListOf<Any>()
        val job = launch {
            executor.execute(node, "Do", "session-1", "", runId = "run-1").collect { output ->
                when (output) {
                    is NodeOutput.State -> results.add(output.state)
                    is NodeOutput.Result -> results.add(output.result)
                }
            }
        }
        advanceTimeBy(200L)
        advanceUntilIdle()

        // The flow ends with the parked state and NO error / result.
        assertTrue(results.last() is AgentOrchestratorState.SuspendedInBackground)
        assertEquals(
            PendingInteractionKind.APPROVAL,
            (results.last() as AgentOrchestratorState.SuspendedInBackground).kind,
        )
        assertTrue(results.filterIsInstance<NodeExecutionResult>().isEmpty())
        coVerify {
            pendingInteractionRepository.save(
                match {
                    it.runId == "run-1" &&
                        it.kind == PendingInteractionKind.APPROVAL &&
                        it.toolName == "MyTool" &&
                        it.toolArgs == "args" &&
                        it.risk == ToolRisk.SENSITIVE
                },
            )
        }
        verify {
            approvalNotifier.sendPersistentApprovalRequest("run-1", "session-1", "MyTool", "args", ToolRisk.SENSITIVE)
        }
        coVerify(exactly = 0) { toolRepository.executeTool(any(), any(), any()) }
        job.cancel()
    }

    @Test
    fun `given live timeout and park persistence fails when execute then falls back to timeout error`() = runTest {
        armSensitiveGate()
        coEvery { pendingInteractionRepository.save(any()) } returns false
        val node = NodeModel("1", NodeType.TOOL, 0f, 0f, toolName = "MyTool")

        val results = mutableListOf<Any>()
        val job = launch {
            executor.execute(node, "Do", "session-1", "", runId = "run-1").collect { output ->
                when (output) {
                    is NodeOutput.State -> results.add(output.state)
                    is NodeOutput.Result -> results.add(output.result)
                }
            }
        }
        advanceTimeBy(200L)
        advanceUntilIdle()

        val lastResult = results.filterIsInstance<NodeExecutionResult>().lastOrNull()
        assertNotNull(lastResult)
        assertTrue(lastResult!!.error!!.contains("timed out", ignoreCase = true))
        verify(exactly = 0) { approvalNotifier.sendPersistentApprovalRequest(any(), any(), any(), any(), any()) }
        job.cancel()
    }

    @Test
    fun `given recorded APPROVED decision with matching args when execute then runs without a new gate`() = runTest {
        armSensitiveGate()
        coEvery { pendingInteractionRepository.getForRun("run-1") } returns PendingInteraction(
            runId = "run-1",
            sessionId = "session-1",
            kind = PendingInteractionKind.APPROVAL,
            toolName = "MyTool",
            toolArgs = "args",
            risk = ToolRisk.SENSITIVE,
            decision = PendingDecision.APPROVED,
            requestedAt = 0L,
        )
        coEvery { toolRepository.executeTool("MyTool", "args", any()) } returns "ok"
        val node = NodeModel("1", NodeType.TOOL, 0f, 0f, toolName = "MyTool")

        val states = executor.execute(node, "Do", "session-1", "", runId = "run-1").toList().unwrap()

        val result = states.filterIsInstance<NodeExecutionResult>().last()
        assertEquals("ok", result.outputText)
        // No fresh gate was raised and the one-shot record was consumed.
        assertTrue(states.filterIsInstance<AgentOrchestratorState.WaitingForApproval>().isEmpty())
        coVerify { pendingInteractionRepository.delete("run-1") }
        verify(exactly = 0) { approvalNotifier.sendApprovalRequest(any(), any(), any(), any()) }
    }

    @Test
    fun `given recorded DENIED decision with matching args when execute then denies without a new gate`() = runTest {
        armSensitiveGate()
        coEvery { pendingInteractionRepository.getForRun("run-1") } returns PendingInteraction(
            runId = "run-1",
            sessionId = "session-1",
            kind = PendingInteractionKind.APPROVAL,
            toolName = "MyTool",
            toolArgs = "args",
            risk = ToolRisk.SENSITIVE,
            decision = PendingDecision.DENIED,
            requestedAt = 0L,
        )
        val node = NodeModel("1", NodeType.TOOL, 0f, 0f, toolName = "MyTool")

        val states = executor.execute(node, "Do", "session-1", "", runId = "run-1").toList().unwrap()

        val result = states.filterIsInstance<NodeExecutionResult>().last()
        assertEquals("Execution denied by user", result.outputText)
        coVerify(exactly = 0) { toolRepository.executeTool(any(), any(), any()) }
        coVerify { pendingInteractionRepository.delete("run-1") }
    }

    @Test
    fun `given recorded decision but different resolved args when execute then raises a fresh gate`() = runTest {
        armSensitiveGate()
        // TOCTOU guard: the user approved "old-args", but the re-resolved call
        // carries "args" — the stale decision must not authorise it.
        coEvery { pendingInteractionRepository.getForRun("run-1") } returns PendingInteraction(
            runId = "run-1",
            sessionId = "session-1",
            kind = PendingInteractionKind.APPROVAL,
            toolName = "MyTool",
            toolArgs = "old-args",
            risk = ToolRisk.SENSITIVE,
            decision = PendingDecision.APPROVED,
            requestedAt = 0L,
        )
        val node = NodeModel("1", NodeType.TOOL, 0f, 0f, toolName = "MyTool")

        val results = mutableListOf<Any>()
        val job = launch {
            executor.execute(node, "Do", "session-1", "", runId = "run-1").collect { output ->
                when (output) {
                    is NodeOutput.State -> results.add(output.state)
                    is NodeOutput.Result -> results.add(output.result)
                }
            }
        }
        runCurrent()

        // A fresh live gate was raised (stale record consumed, no auto-approve).
        assertTrue(results.filterIsInstance<AgentOrchestratorState.WaitingForApproval>().isNotEmpty())
        coVerify { pendingInteractionRepository.delete("run-1") }
        coVerify(exactly = 0) { toolRepository.executeTool(any(), any(), any()) }
        job.cancel()
    }
}
