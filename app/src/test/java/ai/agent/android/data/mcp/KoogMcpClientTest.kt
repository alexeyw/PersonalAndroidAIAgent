package ai.agent.android.data.mcp

import ai.koog.agents.core.tools.Tool
import ai.koog.agents.core.tools.ToolDescriptor
import ai.koog.agents.core.tools.ToolParameterDescriptor
import ai.koog.agents.core.tools.ToolRegistry
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class KoogMcpClientTest {

    private fun makeClient(tools: List<Tool<*, *>>): KoogMcpClient {
        val client = KoogMcpClient()
        val field = client.javaClass.getDeclaredField("registry")
        field.isAccessible = true
        val mockRegistry = mockk<ToolRegistry>()
        every { mockRegistry.tools } returns tools
        field.set(client, mockRegistry)
        return client
    }

    @Test
    fun `getTools maps ToolRegistry to AgentTool with valid JSON Schema`() = runTest {
        val mockDescriptor = mockk<ToolDescriptor>()
        every { mockDescriptor.description } returns "test description"
        every { mockDescriptor.requiredParameters } returns emptyList()
        every { mockDescriptor.optionalParameters } returns emptyList()

        val mockTool = mockk<Tool<Any, Any>>()
        every { mockTool.name } returns "testTool"
        every { mockTool.descriptor } returns mockDescriptor

        val tools = makeClient(listOf(mockTool)).getTools()

        assertEquals(1, tools.size)
        assertEquals("testTool", tools[0].name)
        assertEquals("test description", tools[0].description)
        val schema = JSONObject(tools[0].parameters)
        assertEquals("object", schema.getString("type"))
    }

    @Test
    fun `getTools builds required array for required parameters`() = runTest {
        val requiredParam = mockk<ToolParameterDescriptor>()
        every { requiredParam.name } returns "query"

        val mockDescriptor = mockk<ToolDescriptor>()
        every { mockDescriptor.description } returns "search tool"
        every { mockDescriptor.requiredParameters } returns listOf(requiredParam)
        every { mockDescriptor.optionalParameters } returns emptyList()

        val mockTool = mockk<Tool<Any, Any>>()
        every { mockTool.name } returns "search"
        every { mockTool.descriptor } returns mockDescriptor

        val tools = makeClient(listOf(mockTool)).getTools()

        val schema = JSONObject(tools[0].parameters)
        assertTrue(schema.has("required"))
        assertEquals("query", schema.getJSONArray("required").getString(0))
        assertTrue(schema.getJSONObject("properties").has("query"))
    }

    @Test
    fun `getTools optional parameters appear in properties but not in required`() = runTest {
        val optionalParam = mockk<ToolParameterDescriptor>()
        every { optionalParam.name } returns "lang"

        val mockDescriptor = mockk<ToolDescriptor>()
        every { mockDescriptor.description } returns "search tool"
        every { mockDescriptor.requiredParameters } returns emptyList()
        every { mockDescriptor.optionalParameters } returns listOf(optionalParam)

        val mockTool = mockk<Tool<Any, Any>>()
        every { mockTool.name } returns "search"
        every { mockTool.descriptor } returns mockDescriptor

        val tools = makeClient(listOf(mockTool)).getTools()

        val schema = JSONObject(tools[0].parameters)
        assertTrue(schema.getJSONObject("properties").has("lang"))
        assertTrue(!schema.has("required"))
    }
}