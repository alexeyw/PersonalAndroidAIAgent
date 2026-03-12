package ai.agent.android.data.repositories

import ai.agent.android.data.mcp.McpClient
import ai.agent.android.data.mcp.McpClientFactory
import ai.agent.android.data.tools.local.LocalAppFunctionManager
import ai.agent.android.domain.models.AgentTool
import ai.agent.android.domain.repositories.SettingsRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
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
    
    private lateinit var repository: ToolRepositoryImpl

    @Before
    fun setup() {
        every { mcpClientFactory.create() } returns mcpClient
        every { settingsRepository.mcpServerUrls } returns flowOf(setOf("http://localhost:8080"))
        every { settingsRepository.disabledAppFunctions } returns flowOf(emptySet())
        coEvery { localAppFunctionManager.getAvailableFunctions() } returns listOf(AgentTool("get_system_time", "desc", "{}"))
        coEvery { mcpClient.connect(any()) } returns Unit
        coEvery { mcpClient.disconnect() } returns Unit
        
        repository = ToolRepositoryImpl(settingsRepository, mcpClientFactory, localAppFunctionManager)
    }

    @Test
    fun `getAvailableTools returns local and mcp tools`() = runTest {
        val mcpTools = listOf(AgentTool("test_mcp", "desc", "params"))
        coEvery { mcpClient.getTools() } returns mcpTools

        val result = repository.getAvailableTools()

        assertTrue(result.any { it.name == "get_system_time" })
        assertTrue(result.any { it.name == "test_mcp" })
        coVerify(exactly = 1) { mcpClient.getTools() }
    }

    @Test
    fun `executeTool executes mcp tool if not local`() = runTest {
        val name = "test_mcp"
        val args = "{\"key\":\"value\"}"
        val expectedResult = "success"
        
        // Setup mcp client with this tool
        coEvery { mcpClient.getTools() } returns listOf(AgentTool("test_mcp", "desc", "params"))
        coEvery { mcpClient.executeTool(name, args) } returns expectedResult

        val result = repository.executeTool(name, args)

        assertEquals(expectedResult, result)
        coVerify(exactly = 1) { mcpClient.executeTool(name, args) }
    }
}