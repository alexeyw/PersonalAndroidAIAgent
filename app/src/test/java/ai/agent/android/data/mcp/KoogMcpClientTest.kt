package ai.agent.android.data.mcp

import ai.koog.agents.core.tools.Tool
import ai.koog.agents.core.tools.ToolDescriptor
import ai.koog.agents.core.tools.ToolRegistry
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class KoogMcpClientTest {

    @Test
    fun `getTools maps ToolRegistry to AgentTool`() = runTest {
        val client = KoogMcpClient()
        val mockRegistry = mockk<ToolRegistry>()
        
        val javaField = client.javaClass.getDeclaredField("registry")
        javaField.isAccessible = true
        javaField.set(client, mockRegistry)

        val mockToolDescriptor = mockk<ToolDescriptor>()
        every { mockToolDescriptor.description } returns "test description"
        every { mockToolDescriptor.requiredParameters } returns emptyList()
        every { mockToolDescriptor.optionalParameters } returns emptyList()
        
        val mockTool = mockk<Tool<Any, Any>>()
        every { mockTool.name } returns "testTool"
        every { mockTool.descriptor } returns mockToolDescriptor
        
        every { mockRegistry.tools } returns listOf(mockTool)

        val tools = client.getTools()
        
        assertEquals(1, tools.size)
        assertEquals("testTool", tools[0].name)
        assertEquals("test description", tools[0].description)
    }
}