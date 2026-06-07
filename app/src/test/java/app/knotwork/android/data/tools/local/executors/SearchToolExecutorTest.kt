package app.knotwork.android.data.tools.local.executors

import app.knotwork.android.data.tools.local.SearchTool
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.json.JSONException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Before
import org.junit.Test

/**
 * Unit tests for [SearchToolExecutor].
 *
 * Pins the JSON-argument contract for the `search_tool`:
 *  - `query` defaults to empty string via `optString`; blank input short-circuits with
 *    a fixed error message so the network layer is never hit.
 *  - `lang` defaults to "en" when absent.
 * Delegation correctness is verified against a strict [SearchTool] mock.
 */
class SearchToolExecutorTest {

    private lateinit var searchTool: SearchTool
    private lateinit var executor: SearchToolExecutor

    @Before
    fun setup() {
        searchTool = mockk()
        executor = SearchToolExecutor(searchTool)
    }

    @Test
    fun `toolName property returns search_tool`() {
        // Given / When
        val name = executor.toolName

        // Then
        assertEquals(SearchTool.TOOL_NAME, name)
        assertEquals("search_tool", name)
    }

    @Test
    fun `given JSON with query and lang when execute then delegates to SearchTool with parsed args`() = runTest {
        // Given
        val arguments = """{"query":"Jetpack Compose","lang":"de"}"""
        coEvery { searchTool.executeSearch("Jetpack Compose", "de") } returns "Wikipedia: Jetpack Compose…"

        // When
        val result = executor.execute(arguments)

        // Then
        assertEquals("Wikipedia: Jetpack Compose…", result)
        coVerify(exactly = 1) { searchTool.executeSearch("Jetpack Compose", "de") }
    }

    @Test
    fun `given JSON with only query when execute then defaults lang to en`() = runTest {
        // Given
        val arguments = """{"query":"Kotlin"}"""
        coEvery { searchTool.executeSearch("Kotlin", "en") } returns "Wikipedia: Kotlin…"

        // When
        val result = executor.execute(arguments)

        // Then
        assertEquals("Wikipedia: Kotlin…", result)
        coVerify(exactly = 1) { searchTool.executeSearch("Kotlin", "en") }
    }

    @Test
    fun `given JSON with blank query when execute then returns missing-query error without calling SearchTool`() =
        runTest {
            // Given
            val arguments = """{"query":"   ","lang":"en"}"""

            // When
            val result = executor.execute(arguments)

            // Then
            assertEquals("Error: Missing 'query' argument.", result)
            coVerify(exactly = 0) { searchTool.executeSearch(any(), any()) }
        }

    @Test
    fun `given JSON without query field when execute then returns missing-query error`() = runTest {
        // Given — optString returns "" when the field is absent, which trips the blank guard.
        val arguments = """{"lang":"en"}"""

        // When
        val result = executor.execute(arguments)

        // Then
        assertEquals("Error: Missing 'query' argument.", result)
        coVerify(exactly = 0) { searchTool.executeSearch(any(), any()) }
    }

    @Test
    fun `given malformed JSON when execute then throws JSONException`() {
        // Given
        val arguments = "[]not-an-object"

        // When / Then
        assertThrows(JSONException::class.java) {
            runTest { executor.execute(arguments) }
        }
    }

    @Test
    fun `given underlying tool throws when execute then exception propagates`() {
        // Given
        val arguments = """{"query":"Boom","lang":"en"}"""
        coEvery { searchTool.executeSearch(any(), any()) } throws RuntimeException("Wikipedia down")

        // When / Then
        val thrown = assertThrows(RuntimeException::class.java) {
            runTest { executor.execute(arguments) }
        }
        assertEquals("Wikipedia down", thrown.message)
    }
}
