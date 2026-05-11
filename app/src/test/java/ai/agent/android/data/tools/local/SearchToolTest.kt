package ai.agent.android.data.tools.local

import ai.agent.android.domain.engine.LlmInferenceEngine
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SearchToolTest {

    private val llmEngine = mockk<LlmInferenceEngine>(relaxed = true)
    private val searchTool = SearchTool(llmEngine)

    @Test
    fun `asAgentTool should return correct tool definition`() {
        val tool = searchTool.asAgentTool()
        assertEquals("search_tool", tool.name)
        assertNotNull(tool.description)
        assertTrue(tool.parameters.contains("query"))
        assertTrue(tool.parameters.contains("lang"))
    }

    @Test
    fun `executeSearch should handle empty query gracefully`() = runTest {
        val result = searchTool.executeSearch("", "en")
        assertNotNull(result)
        // Should not crash, will likely return an error string or "No results found"
        assertTrue(result.isNotEmpty())
    }

    // We avoid testing a real network call extensively in unit tests to prevent flaky CI.
    // However, executeSearch is fundamentally IO-based, so checking basic behavior is good.
}
