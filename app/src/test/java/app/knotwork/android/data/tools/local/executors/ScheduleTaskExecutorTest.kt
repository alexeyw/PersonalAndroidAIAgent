package app.knotwork.android.data.tools.local.executors

import app.knotwork.android.domain.usecases.ScheduleTaskUseCase
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import org.json.JSONException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Before
import org.junit.Test

/**
 * Unit tests for [ScheduleTaskExecutor].
 *
 * Pins the JSON-argument contract for the `schedule_task` tool:
 *  - required `prompt`,
 *  - optional `intervalHours` (defaults to 0 — one-shot),
 *  - optional `delayMinutes` (defaults to 0 — execute ASAP),
 * and verifies that the parsed values are forwarded to [ScheduleTaskUseCase] without
 * mutation, and that the use case's return value (or exception) is propagated.
 */
class ScheduleTaskExecutorTest {

    private lateinit var scheduleTaskUseCase: ScheduleTaskUseCase
    private lateinit var executor: ScheduleTaskExecutor

    @Before
    fun setup() {
        scheduleTaskUseCase = mockk()
        executor = ScheduleTaskExecutor(scheduleTaskUseCase)
    }

    @Test
    fun `toolName property returns schedule_task`() {
        // Given / When
        val name = executor.toolName

        // Then
        assertEquals(ScheduleTaskExecutor.TOOL_NAME, name)
        assertEquals("schedule_task", name)
    }

    @Test
    fun `given JSON with all fields when execute then forwards values to use case`() = runTest {
        // Given
        val arguments = """{"prompt":"Remind me to drink water","intervalHours":4,"delayMinutes":15}"""
        every { scheduleTaskUseCase("Remind me to drink water", 4L, 15L) } returns
            "Success: scheduled (id=abc)"

        // When
        val result = executor.execute(arguments)

        // Then
        assertEquals("Success: scheduled (id=abc)", result)
        verify(exactly = 1) { scheduleTaskUseCase("Remind me to drink water", 4L, 15L) }
    }

    @Test
    fun `given JSON with only prompt when execute then defaults intervalHours and delayMinutes to zero`() = runTest {
        // Given
        val arguments = """{"prompt":"One-shot task"}"""
        every { scheduleTaskUseCase("One-shot task", 0L, 0L) } returns "Success: scheduled (id=once)"

        // When
        val result = executor.execute(arguments)

        // Then
        assertEquals("Success: scheduled (id=once)", result)
        verify(exactly = 1) { scheduleTaskUseCase("One-shot task", 0L, 0L) }
    }

    @Test
    fun `given JSON missing prompt when execute then throws JSONException`() {
        // Given
        val arguments = """{"intervalHours":1}"""

        // When / Then
        assertThrows(JSONException::class.java) {
            runTest { executor.execute(arguments) }
        }
    }

    @Test
    fun `given malformed JSON when execute then throws JSONException`() {
        // Given
        val arguments = "{not json"

        // When / Then
        assertThrows(JSONException::class.java) {
            runTest { executor.execute(arguments) }
        }
    }

    @Test
    fun `given use case throws when execute then exception propagates`() {
        // Given
        val arguments = """{"prompt":"Boom"}"""
        every { scheduleTaskUseCase(any(), any(), any()) } throws IllegalStateException("WorkManager unavailable")

        // When / Then
        val thrown = assertThrows(IllegalStateException::class.java) {
            runTest { executor.execute(arguments) }
        }
        assertEquals("WorkManager unavailable", thrown.message)
    }
}
