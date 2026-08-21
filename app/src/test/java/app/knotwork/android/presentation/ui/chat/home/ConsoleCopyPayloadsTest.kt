package app.knotwork.android.presentation.ui.chat.home

import app.knotwork.design.components.console.ConsoleLevel
import app.knotwork.design.components.console.ConsoleLine
import app.knotwork.design.components.console.ConsoleSource
import app.knotwork.design.components.console.ConsoleTab
import app.knotwork.design.components.console.ConsoleTraceSpan
import app.knotwork.design.components.console.ConsoleVarRow
import app.knotwork.design.components.console.SpanStatus
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Unit tests for [ConsoleCopyPayloads].
 *
 * The defect these pin: the Vars and Traces tabs had no copy path at all, and
 * the header `Copy all` built its payload from log lines whatever tab was open —
 * so on those two tabs a visible button put something other than the visible
 * content on the clipboard. Pure formatting, so neither a delegate nor a
 * coroutine scope is needed to hold them.
 */
class ConsoleCopyPayloadsTest {

    private val logs = listOf(
        ConsoleLine("12:00:00.000", ConsoleSource.NODE, ConsoleLevel.Trace, "▶ INPUT"),
        ConsoleLine("12:00:01.000", ConsoleSource.TOOL, ConsoleLevel.Info, "tool"),
    )

    private val vars = listOf(
        ConsoleVarRow(node = "LITE_RT#1", key = "input", valueJson = "prompt text"),
        ConsoleVarRow(node = "LITE_RT#1", key = "output", valueJson = "answer text"),
    )

    private val traces = listOf(
        ConsoleTraceSpan("LITE_RT#1", durationMs = 12L, startedAt = "12:00:00.000", status = SpanStatus.Ok),
    )

    @Test
    fun `given a line when formatting then it renders timestamp source and text`() {
        val line = ConsoleLine("12:00:00.000", ConsoleSource.TOOL, ConsoleLevel.Info, "calendar.create")

        assertEquals("[12:00:00.000] [TOOL] calendar.create", ConsoleCopyPayloads.line(line))
    }

    @Test
    fun `given several lines when formatting then they are joined with newlines`() {
        assertEquals(
            "[12:00:00.000] [NODE] ▶ INPUT\n[12:00:01.000] [TOOL] tool",
            ConsoleCopyPayloads.allLines(logs),
        )
    }

    @Test
    fun `given a variable when formatting then the whole value follows a node and key header`() {
        // A node's assembled prompt is routinely thousands of characters — that
        // length is the reason the action exists, so the payload must not
        // abbreviate it.
        val longValue = "--- Original Task ---\n" + "x".repeat(4_000)
        val row = ConsoleVarRow(node = "LITE_RT#501fab", key = "input", valueJson = longValue)

        assertEquals("LITE_RT#501fab input\n$longValue", ConsoleCopyPayloads.variable(row))
    }

    @Test
    fun `given a span when formatting then it renders time name duration and status`() {
        val span = ConsoleTraceSpan(
            name = "LITE_RT#501fab",
            durationMs = 1_240L,
            startedAt = "12:00:00.000",
            status = SpanStatus.Ok,
        )

        assertEquals("[12:00:00.000] LITE_RT#501fab — 1240 ms (Ok)", ConsoleCopyPayloads.span(span))
    }

    @Test
    fun `given each tab in turn when formatting for the tab then it copies that tab`() {
        // The defect itself: `Copy all` used to build its payload from the log
        // lines whatever tab was open.
        assertEquals(
            "[12:00:00.000] [NODE] ▶ INPUT\n[12:00:01.000] [TOOL] tool",
            ConsoleCopyPayloads.forTab(ConsoleTab.Logs, logs, vars, traces),
        )
        assertEquals(
            "LITE_RT#1 input\nprompt text\n\nLITE_RT#1 output\nanswer text",
            ConsoleCopyPayloads.forTab(ConsoleTab.Vars, logs, vars, traces),
        )
        assertEquals(
            "[12:00:00.000] LITE_RT#1 — 12 ms (Ok)",
            ConsoleCopyPayloads.forTab(ConsoleTab.Traces, logs, vars, traces),
        )
    }
}
