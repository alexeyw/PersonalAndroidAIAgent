package app.knotwork.android.domain.engine.executors

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Unit tests for [ToolCallParser], the non-repair tool-call parser used by
 * [SkillNodeExecutor].
 */
class ToolCallParserTest {

    @Test
    fun `parseToolSelection returns pair for valid JSON with string arguments`() {
        val response = """{"tool": "search", "arguments": "how to build android app"}"""

        val parsed = ToolCallParser.parseToolSelection(response)

        assertEquals("search", parsed?.first)
        // A string `arguments` primitive is unwrapped to its raw content (no quotes).
        assertEquals("how to build android app", parsed?.second)
    }

    @Test
    fun `parseToolSelection unwraps a fenced block`() {
        val response = """
            ```json
            {
                "tool": "search",
                "arguments": "how to build android app"
            }
            ```
        """.trimIndent()

        val parsed = ToolCallParser.parseToolSelection(response)

        assertEquals("search", parsed?.first)
        assertEquals("how to build android app", parsed?.second)
    }

    @Test
    fun `parseToolSelection extracts a nested json object argument from surrounding prose`() {
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

        val result = ToolCallParser.parseToolSelection(response)

        assertEquals("create_event", result?.first)
        val resultObj = JSONObject(result?.second!!)
        assertEquals("Meeting", resultObj.getString("title"))
        assertEquals("10:00 AM", resultObj.getString("time"))
    }

    @Test
    fun `parseToolSelection returns null when no tool call object is present`() {
        assertNull(ToolCallParser.parseToolSelection("there is no JSON here at all"))
    }

    @Test
    fun `parseToolSelection returns null when the tool field is missing`() {
        // `tool` is required by the ToolCall schema — a bare arguments object is not a selection.
        assertNull(ToolCallParser.parseToolSelection("""{"arguments": {"q": "x"}}"""))
    }
}
