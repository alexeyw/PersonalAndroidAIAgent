package ai.agent.android.data.repositories

import ai.agent.android.data.mcp.McpClient
import ai.agent.android.data.mcp.McpClientFactory
import ai.agent.android.data.tools.local.LocalAppFunctionManager
import ai.agent.android.domain.models.AgentTool
import ai.agent.android.domain.repositories.LocalToolExecutor
import ai.agent.android.domain.repositories.SettingsRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class ToolRepositoryImplTest {

    private val settingsRepository: SettingsRepository = mockk()
    private val mcpClientFactory: McpClientFactory = mockk()
    private val mcpClient: McpClient = mockk()
    private val localAppFunctionManager: LocalAppFunctionManager = mockk()
    private val apiKeyRepository: ai.agent.android.domain.repositories.ApiKeyRepository = mockk()
    private val searchTool: ai.agent.android.data.tools.local.SearchTool = mockk(relaxed = true)
    private val scheduleTaskExecutor: LocalToolExecutor = mockk(relaxed = true)
    private val searchToolExecutor: LocalToolExecutor = mockk(relaxed = true)

    private lateinit var repository: ToolRepositoryImpl

    @Before
    fun setup() {
        every { mcpClientFactory.create() } returns mcpClient
        every { settingsRepository.mcpServerUrls } returns flowOf(setOf("http://localhost:8080"))
        every { settingsRepository.disabledAppFunctions } returns flowOf(emptySet())
        coEvery { localAppFunctionManager.getAvailableFunctions() } returns
            listOf(AgentTool("get_system_time", "desc", "{}"))
        coEvery { mcpClient.connect(any()) } returns Unit
        coEvery { mcpClient.disconnect() } returns Unit

        every { apiKeyRepository.getOpenAIKey() } returns flowOf(null)
        every { apiKeyRepository.getAnthropicKey() } returns flowOf(null)
        every { apiKeyRepository.getGoogleKey() } returns flowOf(null)
        every { apiKeyRepository.getDeepSeekKey() } returns flowOf(null)
        every { apiKeyRepository.getOllamaBaseUrl() } returns flowOf(null)

        every { searchTool.asAgentTool() } returns AgentTool("search_tool", "desc", "{}")
        every { scheduleTaskExecutor.toolName } returns "schedule_task"
        every { searchToolExecutor.toolName } returns "search_tool"

        // Hilt would normally inject this map; in unit tests we build it explicitly so we
        // can verify which executor (if any) gets dispatched to for a given tool name.
        val executorMap = mapOf(
            "schedule_task" to scheduleTaskExecutor,
            "search_tool" to searchToolExecutor,
        )

        repository = ToolRepositoryImpl(
            settingsRepository,
            mcpClientFactory,
            localAppFunctionManager,
            apiKeyRepository,
            searchTool,
            executorMap,
        )
    }

    @Test
    fun `getAvailableTools returns built-in and mcp tools but excludes raw AppFunctions`() = runTest {
        // AppFunctions discovered via LocalAppFunctionManager (e.g. `get_system_time`)
        // are intentionally NOT advertised until end-to-end execution is wired up —
        // see ToolRepositoryImpl.getAllLocalTools KDoc. Built-in tools (`schedule_task`,
        // `search_tool`) and MCP tools must still come through.
        val mcpTools = listOf(AgentTool("test_mcp", "desc", "params"))
        coEvery { mcpClient.getTools() } returns mcpTools

        val result = repository.getAvailableTools()

        assertTrue(result.any { it.name == "schedule_task" })
        assertTrue(result.any { it.name == "search_tool" })
        assertTrue(result.any { it.name == "test_mcp" })
        // The raw AppFunction must NOT leak into the advertised catalogue.
        assertTrue(result.none { it.name == "get_system_time" })
        coVerify(exactly = 1) { mcpClient.getTools() }
    }

    @Test
    fun `executeTool executes mcp tool if not local`() = runTest {
        val name = "test_mcp"
        val args = "{\"key\":\"value\"}"
        val expectedResult = "success"

        coEvery { mcpClient.getTools() } returns listOf(AgentTool("test_mcp", "desc", "params"))
        coEvery { mcpClient.executeTool(name, args) } returns expectedResult

        val result = repository.executeTool(name, args)

        assertEquals(expectedResult, result)
        coVerify(exactly = 1) { mcpClient.executeTool(name, args) }
    }

    @Test
    fun `executeTool throws with tool name interpolated when tool is disabled`() = runTest {
        // Use a built-in tool name here — AppFunctions are not advertised by
        // ToolRepositoryImpl any more, so the disabled-check path is reachable only via
        // built-ins (or, in the future, AppFunctions once execution is wired up).
        val toolName = "schedule_task"
        every { settingsRepository.disabledAppFunctions } returns flowOf(setOf(toolName))
        coEvery { mcpClient.getTools() } returns emptyList()

        val exception = runCatching { repository.executeTool(toolName, "{}") }
            .exceptionOrNull()

        assertTrue(exception is IllegalArgumentException)
        assertTrue(exception!!.message!!.contains(toolName))
        assertTrue(exception.message!!.contains("disabled"))
    }

    @Test
    fun `executeTool throws with tool name interpolated when tool not found`() = runTest {
        val toolName = "nonexistent_tool"
        coEvery { mcpClient.getTools() } returns emptyList()

        val exception = runCatching { repository.executeTool(toolName, "{}") }
            .exceptionOrNull()

        assertTrue(exception is IllegalArgumentException)
        assertTrue(exception!!.message!!.contains(toolName))
    }

    @Test
    fun `executeTool dispatches to LocalToolExecutor registered under the tool name`() = runTest {
        // Defect 4 regression guard: a registered tool name must hit its executor and
        // forward the JSON arguments verbatim.
        coEvery { scheduleTaskExecutor.execute("{\"prompt\":\"do X\"}") } returns "scheduled"
        coEvery { mcpClient.getTools() } returns emptyList()

        val result = repository.executeTool("schedule_task", "{\"prompt\":\"do X\"}")

        assertEquals("scheduled", result)
        coVerify(exactly = 1) { scheduleTaskExecutor.execute("{\"prompt\":\"do X\"}") }
    }

    @Test
    fun `executeTool throws when local tool name is registered but has no executor`() = runTest {
        // Defect 4 regression guard for the silent-stub bug: if a tool is advertised as
        // a built-in but no LocalToolExecutor is wired up for it, the repository must
        // fail fast instead of returning a fake "Local tool executed: …" success string.
        // We use `delegate_task` because it is advertised only when at least one cloud
        // API key is configured, and the test setup deliberately omits it from the
        // executor map.
        every { apiKeyRepository.getAnthropicKey() } returns flowOf("anthropic-test-key")
        coEvery { mcpClient.getTools() } returns emptyList()
        val toolName = "delegate_task"

        val exception = runCatching { repository.executeTool(toolName, "{}") }
            .exceptionOrNull()

        assertTrue("Expected IllegalArgumentException, got $exception", exception is IllegalArgumentException)
        assertTrue(exception!!.message!!.contains(toolName))
        assertTrue(exception.message!!.contains("no executor registered"))
    }

    @Test
    fun `concurrent getAvailableTools and executeTool does not throw`() = runTest {
        val mcpTools = listOf(AgentTool("test_mcp", "desc", "params"))
        coEvery { mcpClient.getTools() } returns mcpTools
        coEvery { mcpClient.executeTool(any(), any()) } returns "success"

        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) {
            val jobs = (1..100).map {
                launch {
                    if (it % 2 == 0) {
                        repository.getAvailableTools()
                    } else {
                        repository.executeTool("test_mcp", "{}")
                    }
                }
            }
            jobs.forEach { it.join() }
        }

        assertTrue(true) // Should reach here without exceptions
    }
}
