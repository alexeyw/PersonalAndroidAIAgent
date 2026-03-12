package ai.agent.android.data.mcp

import ai.agent.android.domain.models.AgentTool
import ai.agent.android.data.repositories.ToolRepositoryImpl
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class ToolRepositoryImplTest {

    private val mcpClient: McpClient = mockk()
    private val repository = ToolRepositoryImpl(mcpClient)

    @Test
    fun `getAvailableTools returns tools from client`() = runTest {
        val tools = listOf(AgentTool("test", "desc", "params"))
        coEvery { mcpClient.getTools() } returns tools

        val result = repository.getAvailableTools()

        assertEquals(tools, result)
        coVerify(exactly = 1) { mcpClient.getTools() }
    }

    @Test
    fun `executeTool passes arguments to client`() = runTest {
        val name = "testTool"
        val args = "{\"key\":\"value\"}"
        val expectedResult = "success"
        
        coEvery { mcpClient.executeTool(name, args) } returns expectedResult

        val result = repository.executeTool(name, args)

        assertEquals(expectedResult, result)
        coVerify(exactly = 1) { mcpClient.executeTool(name, args) }
    }
}