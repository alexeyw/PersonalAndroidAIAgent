package app.knotwork.android.data.tools.local.executors

import app.knotwork.android.data.tools.local.DelegateTaskTool
import app.knotwork.android.domain.models.CloudProvider
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
 * Unit tests for [DelegateTaskExecutor].
 *
 * The executor is a thin JSON-argument parser that forwards the parsed
 * `taskDescription` and optional `targetModel` to [DelegateTaskTool]. These tests
 * pin its parsing contract (defaults, missing fields, malformed input) and verify
 * that the underlying tool's result/exception is propagated unchanged.
 */
class DelegateTaskExecutorTest {

    private lateinit var delegateTaskTool: DelegateTaskTool
    private lateinit var executor: DelegateTaskExecutor

    @Before
    fun setup() {
        delegateTaskTool = mockk()
        executor = DelegateTaskExecutor(delegateTaskTool)
    }

    @Test
    fun `toolName property returns delegate_task`() {
        // Given / When
        val name = executor.toolName

        // Then
        assertEquals(DelegateTaskExecutor.TOOL_NAME, name)
        assertEquals("delegate_task", name)
    }

    @Test
    fun `given JSON with taskDescription and targetModel when execute then delegates to tool with parsed args`() =
        runTest {
            // Given
            val arguments = """{"taskDescription":"Summarize this PR","targetModel":"openai"}"""
            coEvery { delegateTaskTool.executeDelegation("Summarize this PR", "openai") } returns
                "Success: Task completed"

            // When
            val result = executor.execute(arguments)

            // Then
            assertEquals("Success: Task completed", result)
            coVerify(exactly = 1) { delegateTaskTool.executeDelegation("Summarize this PR", "openai") }
        }

    @Test
    fun `given JSON with only taskDescription when execute then defaults targetModel to anthropic`() = runTest {
        // Given
        val arguments = """{"taskDescription":"Plan migration"}"""
        coEvery { delegateTaskTool.executeDelegation("Plan migration", CloudProvider.ANTHROPIC.id) } returns
            "Success: Task completed"

        // When
        val result = executor.execute(arguments)

        // Then
        assertEquals("Success: Task completed", result)
        coVerify(exactly = 1) { delegateTaskTool.executeDelegation("Plan migration", CloudProvider.ANTHROPIC.id) }
    }

    @Test
    fun `given underlying tool returns error string when execute then returns same error string`() = runTest {
        // Given — executor is a pass-through, even error messages are forwarded verbatim.
        val arguments = """{"taskDescription":"Task","targetModel":"anthropic"}"""
        val errorMessage = "Error: Client for anthropic could not be initialized"
        coEvery { delegateTaskTool.executeDelegation(any(), any()) } returns errorMessage

        // When
        val result = executor.execute(arguments)

        // Then
        assertEquals(errorMessage, result)
    }

    @Test
    fun `given JSON missing taskDescription when execute then throws JSONException`() {
        // Given
        val arguments = """{"targetModel":"anthropic"}"""

        // When / Then
        assertThrows(JSONException::class.java) {
            runTest { executor.execute(arguments) }
        }
    }

    @Test
    fun `given malformed JSON when execute then throws JSONException`() {
        // Given
        val arguments = "not-json"

        // When / Then
        assertThrows(JSONException::class.java) {
            runTest { executor.execute(arguments) }
        }
    }

    @Test
    fun `given underlying tool throws when execute then exception propagates`() {
        // Given
        val arguments = """{"taskDescription":"Task","targetModel":"anthropic"}"""
        coEvery { delegateTaskTool.executeDelegation(any(), any()) } throws RuntimeException("Network error")

        // When / Then
        val thrown = assertThrows(RuntimeException::class.java) {
            runTest { executor.execute(arguments) }
        }
        assertEquals("Network error", thrown.message)
    }
}
