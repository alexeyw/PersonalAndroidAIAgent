package ai.agent.android.presentation.ui.chat.home

import ai.agent.android.domain.models.AgentOrchestratorState
import ai.agent.android.domain.models.ConsoleEvent
import ai.agent.android.domain.models.ConsoleEventType
import app.knotwork.design.components.console.ConsoleLevel
import app.knotwork.design.components.console.ConsoleSource
import app.knotwork.design.components.console.SpanStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.regex.Pattern

/**
 * Unit coverage for the pure-Kotlin mappers in [ChatHomeConsoleMapping].
 *
 * Tests are fast (no Robolectric, no Compose) and total: every
 * [ConsoleEventType] is covered so a future addition triggers a
 * compile-time exhaustiveness break in the production mapper.
 */
class ChatHomeConsoleMappingTest {

    @Test
    fun `given NodeExecution event when mapping then source NODE and level Trace`() {
        val event = ConsoleEvent(
            timestamp = 0L,
            type = ConsoleEventType.NodeExecution,
            message = "▶ LITE_RT",
        )

        val line = event.toConsoleLine()

        assertEquals(ConsoleSource.NODE, line.source)
        assertEquals(ConsoleLevel.Trace, line.level)
        assertEquals("▶ LITE_RT", line.text)
        assertTrue(
            "timestamp must match HH:mm:ss.SSS — was '${line.timestamp}'",
            TIMESTAMP_REGEX.matcher(line.timestamp).matches(),
        )
    }

    @Test
    fun `given ToolCall event when mapping then source TOOL and level Info`() {
        val line = ConsoleEvent(timestamp = 0L, type = ConsoleEventType.ToolCall, message = "calendar.create_event")
            .toConsoleLine()

        assertEquals(ConsoleSource.TOOL, line.source)
        assertEquals(ConsoleLevel.Info, line.level)
    }

    @Test
    fun `given MemoryAccess event when mapping then source RUNTIME and level Info`() {
        val line = ConsoleEvent(timestamp = 0L, type = ConsoleEventType.MemoryAccess, message = "Memory: 3 chunks")
            .toConsoleLine()

        assertEquals(ConsoleSource.RUNTIME, line.source)
        assertEquals(ConsoleLevel.Info, line.level)
    }

    @Test
    fun `given SystemMessage event when mapping then source RUNTIME and level Info`() {
        val line = ConsoleEvent(timestamp = 0L, type = ConsoleEventType.SystemMessage, message = "started")
            .toConsoleLine()

        assertEquals(ConsoleSource.RUNTIME, line.source)
        assertEquals(ConsoleLevel.Info, line.level)
    }

    @Test
    fun `given Error event when mapping then source RUNTIME and level Error`() {
        val line = ConsoleEvent(timestamp = 0L, type = ConsoleEventType.Error, message = "boom")
            .toConsoleLine()

        assertEquals(ConsoleSource.RUNTIME, line.source)
        assertEquals(ConsoleLevel.Error, line.level)
    }

    @Test
    fun `given trace step when mapping then status Ok and duration preserved`() {
        val trace = AgentOrchestratorState.TraceStep(
            nodeName = "LITE_RT",
            outputText = "irrelevant",
            durationMs = 1840L,
            tokenCount = 64,
        )

        val span = traceStepToConsoleSpan(trace, startedAtMs = 0L)

        assertEquals("LITE_RT", span.name)
        assertEquals(1840L, span.durationMs)
        assertEquals(SpanStatus.Ok, span.status)
        assertTrue("startedAt must match HH:mm:ss.SSS", TIMESTAMP_REGEX.matcher(span.startedAt).matches())
    }

    @Test
    fun `given node IO when mapping then returns ordered input output rows with quoted values`() {
        val io = AgentOrchestratorState.NodeIO(
            nodeId = "lite_rt_abcdef",
            nodeType = "LITE_RT",
            input = "user prompt",
            output = "model reply",
        )

        val rows = nodeIoToVarRows(io)

        assertEquals(2, rows.size)
        assertEquals(CONSOLE_VAR_KEY_INPUT, rows[0].key)
        assertEquals("\"user prompt\"", rows[0].valueJson)
        assertEquals("LITE_RT#lite_r", rows[0].node)
        assertEquals(CONSOLE_VAR_KEY_OUTPUT, rows[1].key)
        assertEquals("\"model reply\"", rows[1].valueJson)
    }

    @Test
    fun `given node IO with special chars when mapping then values are JSON-escaped`() {
        val io = AgentOrchestratorState.NodeIO(
            nodeId = "x",
            nodeType = "T",
            input = "line1\nline2",
            output = "with \"quote\"",
        )

        val rows = nodeIoToVarRows(io)

        assertEquals("\"line1\\nline2\"", rows[0].valueJson)
        assertEquals("\"with \\\"quote\\\"\"", rows[1].valueJson)
    }

    private companion object {
        /** `HH:mm:ss.SSS` shape, locale-independent (digits + separators only). */
        val TIMESTAMP_REGEX: Pattern = Pattern.compile("^\\d{2}:\\d{2}:\\d{2}\\.\\d{3}$")
    }
}
