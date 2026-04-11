package ai.agent.android.domain.engine.executors

import ai.agent.android.domain.repositories.ChatRepository
import ai.agent.android.domain.repositories.SettingsRepository
import ai.agent.android.domain.repositories.ToolRepository
import ai.agent.android.domain.services.ApprovalNotifier
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

/**
 * Unit tests for [ToolNodeExecutor].
 */
class ToolNodeExecutorTest {

    private lateinit var toolRepository: ToolRepository
    private lateinit var settingsRepository: SettingsRepository
    private lateinit var approvalNotifier: ApprovalNotifier
    private lateinit var chatRepository: ChatRepository
    private lateinit var executor: ToolNodeExecutor

    @Before
    fun setup() {
        toolRepository = mockk(relaxed = true)
        settingsRepository = mockk(relaxed = true)
        approvalNotifier = mockk(relaxed = true)
        chatRepository = mockk(relaxed = true)
        
        executor = ToolNodeExecutor(
            toolRepository = toolRepository,
            settingsRepository = settingsRepository,
            approvalNotifier = approvalNotifier,
            chatRepository = chatRepository
        )
    }

    @Test
    fun `parseToolArguments returns null when response contains no json block and is not valid json`() {
        val response = "Here is my thinking: I need to use the tool, but I forgot to output JSON."
        assertNull(executor.parseToolArguments(response))
    }

    @Test
    fun `parseToolArguments extracts string arguments from raw json without markdown block`() {
        val response = """{"tool": "search", "arguments": "how to build android app"}"""
        assertEquals("how to build android app", executor.parseToolArguments(response))
    }

    @Test
    fun `parseToolArguments extracts json from conversational text without markdown block`() {
        val response = """I will use the tool now: {"tool": "search", "arguments": "how to build android app"}"""
        assertEquals("how to build android app", executor.parseToolArguments(response))
    }

    @Test
    fun `parseToolArguments returns null when json block is invalid json`() {
        val response = """
            Here is the tool call:
            ```json
            { "tool": "myTool", "arguments": "broken_value 
            ```
        """.trimIndent()
        assertNull(executor.parseToolArguments(response))
    }

    @Test
    fun `parseToolArguments returns null when valid json has no arguments field`() {
        val response = """
            ```json
            {
                "tool": "myTool",
                "other_field": "value"
            }
            ```
        """.trimIndent()
        assertNull(executor.parseToolArguments(response))
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
    fun `parseToolArguments extracts nested json object arguments correctly`() {
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
        val expected = """{"title":"Meeting","time":"10:00 AM"}"""
        val result = executor.parseToolArguments(response)
        
        // org.json formatting might vary, so we can parse it back to compare or just check the presence of keys
        // Since we are running in JVM unit test, org.json might behave slightly differently.
        // Let's compare as string for simplicity since JSONObject.toString() does not guarantee key order, 
        // but for small objects it's often consistent. To be safe we can use JSONObject to verify equality.
        val resultObj = org.json.JSONObject(result)
        assertEquals("Meeting", resultObj.getString("title"))
        assertEquals("10:00 AM", resultObj.getString("time"))
    }

    @Test
    fun `parseToolArguments handles escaped quotes in string arguments`() {
        val response = """
            ```json
            {
                "tool": "echo",
                "arguments": "He said: \"Hello World\""
            }
            ```
        """.trimIndent()
        assertEquals("He said: \"Hello World\"", executor.parseToolArguments(response))
    }

    @Test
    fun `parseToolArguments handles complex json with formatting and whitespace`() {
        val response = """
            Thinking process...
            ```json
            
               {
                   "tool": "complexTool",
                   "arguments":    {
                       "key1": "val1",
                       "key2": [1, 2, 3]
                   }
               }
               
            ```
            Done.
        """.trimIndent()
        val result = executor.parseToolArguments(response)
        val resultObj = org.json.JSONObject(result)
        assertEquals("val1", resultObj.getString("key1"))
        assertEquals(3, resultObj.getJSONArray("key2").length())
    }
}
