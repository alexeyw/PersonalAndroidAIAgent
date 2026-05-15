package ai.agent.android.data.tools.local.appfunctions

import ai.agent.android.data.tools.local.SearchTool
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for [AppFunctionRouter].
 *
 * The router is the JVM-testable seam of the callee-side AppFunctions service —
 * `AgentAppFunctionService` is excluded from coverage because it depends on
 * platform-only types (`AppFunctionService`, `GenericDocument`). The router covers the
 * actual routing logic, so its tests pin every branch the production path takes:
 *
 *  - Known identifier with valid args → [DispatchOutcome.Success].
 *  - Known identifier with blank/missing required arg → [DispatchOutcome.InvalidArgument].
 *  - Unknown identifier → [DispatchOutcome.FunctionNotFound].
 *  - Wrapper raising an unchecked exception → [DispatchOutcome.InternalError] (mapped to
 *    `ERROR_APP_UNKNOWN_ERROR` by the service shim).
 */
class AppFunctionRouterTest {

    private val searchTool: SearchTool = mockk()
    private val searchAppFunction = SearchAppFunction(searchTool)
    private val router = AppFunctionRouter(searchAppFunction)

    @Test
    fun `given search_tool with query when route then returns Success with SearchTool result`() = runTest {
        coEvery { searchTool.executeSearch("kotlin", "en") } returns "extract"

        val outcome = router.route("search_tool") { name ->
            mapOf("query" to "kotlin", "lang" to "en")[name]
        }

        assertEquals(DispatchOutcome.Success("extract"), outcome)
        coVerify(exactly = 1) { searchTool.executeSearch("kotlin", "en") }
    }

    @Test
    fun `given search_tool without lang when route then defaults to en`() = runTest {
        coEvery { searchTool.executeSearch("kotlin", "en") } returns "extract"

        val outcome = router.route("search_tool") { name -> if (name == "query") "kotlin" else null }

        assertEquals(DispatchOutcome.Success("extract"), outcome)
        coVerify(exactly = 1) { searchTool.executeSearch("kotlin", "en") }
    }

    @Test
    fun `given search_tool with blank lang when route then falls back to en`() = runTest {
        coEvery { searchTool.executeSearch("kotlin", "en") } returns "extract"

        val outcome = router.route("search_tool") { name ->
            mapOf("query" to "kotlin", "lang" to " ")[name]
        }

        assertEquals(DispatchOutcome.Success("extract"), outcome)
        coVerify(exactly = 1) { searchTool.executeSearch("kotlin", "en") }
    }

    @Test
    fun `given search_tool with missing query when route then returns InvalidArgument`() = runTest {
        val outcome = router.route("search_tool") { null }

        assertTrue(outcome is DispatchOutcome.InvalidArgument)
        assertEquals(
            "search_tool requires a non-blank 'query' argument",
            (outcome as DispatchOutcome.InvalidArgument).message,
        )
    }

    @Test
    fun `given search_tool with blank query when route then returns InvalidArgument`() = runTest {
        val outcome = router.route("search_tool") { name -> if (name == "query") "  " else null }

        assertTrue(outcome is DispatchOutcome.InvalidArgument)
    }

    @Test
    fun `given unknown identifier when route then returns FunctionNotFound`() = runTest {
        val outcome = router.route("does_not_exist") { null }

        assertEquals(DispatchOutcome.FunctionNotFound("does_not_exist"), outcome)
    }

    @Test
    fun `given wrapper throws when route then returns InternalError`() = runTest {
        val boom = RuntimeException("boom")
        coEvery { searchTool.executeSearch(any(), any()) } throws boom

        val outcome = router.route("search_tool") { name -> if (name == "query") "kotlin" else null }

        assertTrue(outcome is DispatchOutcome.InternalError)
        assertEquals(boom, (outcome as DispatchOutcome.InternalError).cause)
    }

    @Test
    fun `given wrapper throws CancellationException when route then it propagates`() {
        val cancellation = CancellationException("client cancelled")
        coEvery { searchTool.executeSearch(any(), any()) } throws cancellation

        // Use plain runBlocking — `runTest` swallows CancellationException as a normal
        // structured-concurrency control signal, but our contract is that the cancellation
        // must surface to the caller of `route` so the service shim can map it to
        // ERROR_CANCELLED. runBlocking re-throws cancellations from its body verbatim.
        val thrown = assertThrows(CancellationException::class.java) {
            runBlocking {
                router.route("search_tool") { name -> if (name == "query") "kotlin" else null }
            }
        }
        assertEquals("client cancelled", thrown.message)
    }
}
