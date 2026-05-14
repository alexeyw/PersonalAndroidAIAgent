package ai.agent.android.data.tools.local.appfunctions

import ai.agent.android.data.tools.local.SearchTool
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

/**
 * Unit tests for [SearchAppFunction].
 *
 * The wrapper is intentionally thin — its job is to validate the externally-supplied
 * arguments and forward them to [SearchTool.executeSearch]. The tests pin three contracts
 * that the callee-side AppFunctions router relies on:
 *  1. Valid arguments reach [SearchTool] verbatim and the result is returned unchanged.
 *  2. A blank `query` raises [IllegalArgumentException] (which the router maps to
 *     `ERROR_INVALID_ARGUMENT` on the platform path).
 *  3. A blank or omitted `lang` is normalised to the default (`"en"`) so callers can
 *     leave it out.
 */
class SearchAppFunctionTest {

    private val searchTool: SearchTool = mockk()
    private val searchAppFunction = SearchAppFunction(searchTool)

    @Test
    fun `given valid arguments when invoke then delegates to SearchTool and returns result`() = runTest {
        coEvery { searchTool.executeSearch("kotlin", "en") } returns "Kotlin is a programming language."

        val result = searchAppFunction.invoke(query = "kotlin", lang = "en")

        assertEquals("Kotlin is a programming language.", result)
        coVerify(exactly = 1) { searchTool.executeSearch("kotlin", "en") }
    }

    @Test
    fun `given blank query when invoke then throws IllegalArgumentException`() {
        val exception = assertThrows(IllegalArgumentException::class.java) {
            runBlocking { searchAppFunction.invoke(query = "  ", lang = "en") }
        }
        assertEquals("search_tool requires a non-blank 'query' argument", exception.message)
    }

    @Test
    fun `given blank lang when invoke then falls back to default en`() = runTest {
        coEvery { searchTool.executeSearch("kotlin", "en") } returns "ok"

        val result = searchAppFunction.invoke(query = "kotlin", lang = "")

        assertEquals("ok", result)
        coVerify(exactly = 1) { searchTool.executeSearch("kotlin", "en") }
    }

    @Test
    fun `given omitted lang when invoke then uses default en`() = runTest {
        coEvery { searchTool.executeSearch("kotlin", "en") } returns "ok"

        val result = searchAppFunction.invoke(query = "kotlin")

        assertEquals("ok", result)
        coVerify(exactly = 1) { searchTool.executeSearch("kotlin", "en") }
    }
}
