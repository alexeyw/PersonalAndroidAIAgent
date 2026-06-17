package app.knotwork.android.domain.engine

import app.knotwork.android.domain.models.ChatHistorySummary
import app.knotwork.android.domain.models.ChatMessage
import app.knotwork.android.domain.models.Role
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for [ChatHistoryWindowPlanner] — the pure planner that decides how
 * a session's chat history is split into a summarised prefix and a verbatim
 * live window.
 */
class ChatHistoryWindowPlannerTest {

    private val planner = ChatHistoryWindowPlanner()

    /** Builds [count] messages each of [contentLength] characters. */
    private fun messages(count: Int, contentLength: Int = 40): List<ChatMessage> = (0 until count).map { index ->
        ChatMessage(
            id = index.toLong(),
            sessionId = "s",
            role = if (index % 2 == 0) Role.USER else Role.AGENT,
            content = "x".repeat(contentLength),
            timestamp = index.toLong(),
        )
    }

    @Test
    fun `given empty history when plan then empty view`() {
        val view = planner.plan(
            messages = emptyList(),
            summary = null,
            compressionEnabled = true,
            thresholdTokens = 1,
            liveWindowSize = 10,
        )

        assertEquals(ChatHistoryView.EMPTY, view)
    }

    @Test
    fun `given compression disabled when plan then full history and no summary`() {
        val all = messages(count = 50)

        val view = planner.plan(
            messages = all,
            summary = ChatHistorySummary("s", "old summary", coveredMessageCount = 20, updatedAt = 0L),
            compressionEnabled = false,
            thresholdTokens = 1,
            liveWindowSize = 10,
        )

        assertNull(view.earlierSummary)
        assertEquals(all, view.liveWindow)
        assertEquals(0, view.droppedUncoveredCount)
        assertFalse(view.truncatedWithoutSummary)
    }

    @Test
    fun `given history within budget when plan then full history and no summary`() {
        // 5 messages * 40 chars / 4 = 50 tokens, threshold is far higher.
        val all = messages(count = 5)

        val view = planner.plan(
            messages = all,
            summary = null,
            compressionEnabled = true,
            thresholdTokens = 10_000,
            liveWindowSize = 3,
        )

        assertNull(view.earlierSummary)
        assertEquals(all, view.liveWindow)
        assertFalse(view.truncatedWithoutSummary)
    }

    @Test
    fun `given over budget with no summary when plan then truncates to live window`() {
        val all = messages(count = 30)

        val view = planner.plan(
            messages = all,
            summary = null,
            compressionEnabled = true,
            thresholdTokens = 10,
            liveWindowSize = 10,
        )

        assertNull(view.earlierSummary)
        assertEquals(10, view.liveWindow.size)
        // The live window is the most recent 10 messages.
        assertEquals(all.takeLast(10), view.liveWindow)
        assertEquals(20, view.droppedUncoveredCount)
        assertTrue(view.truncatedWithoutSummary)
    }

    @Test
    fun `given over budget with fresh summary when plan then summary plus live window and no gap`() {
        val all = messages(count = 30)
        // Fresh: the summary covers exactly everything older than the window.
        val summary = ChatHistorySummary("s", "earlier summary", coveredMessageCount = 20, updatedAt = 0L)

        val view = planner.plan(
            messages = all,
            summary = summary,
            compressionEnabled = true,
            thresholdTokens = 10,
            liveWindowSize = 10,
        )

        assertEquals("earlier summary", view.earlierSummary)
        assertEquals(all.takeLast(10), view.liveWindow)
        assertEquals(0, view.droppedUncoveredCount)
        assertFalse(view.truncatedWithoutSummary)
    }

    @Test
    fun `given over budget with stale summary when plan then drops uncovered tail and keeps window`() {
        val all = messages(count = 30)
        // Stale: the summary only covers the first 15 of the 20 pre-window
        // messages; 5 have aged out since and are not yet folded in.
        val summary = ChatHistorySummary("s", "earlier summary", coveredMessageCount = 15, updatedAt = 0L)

        val view = planner.plan(
            messages = all,
            summary = summary,
            compressionEnabled = true,
            thresholdTokens = 10,
            liveWindowSize = 10,
        )

        assertEquals("earlier summary", view.earlierSummary)
        assertEquals(all.takeLast(10), view.liveWindow)
        // 30 - 15 covered = 15 remainder, keep last 10 → 5 dropped.
        assertEquals(5, view.droppedUncoveredCount)
        // Not flagged as truncatedWithoutSummary because a summary is present.
        assertFalse(view.truncatedWithoutSummary)
    }

    @Test
    fun `given blank summary text when over budget then treated as no summary`() {
        val all = messages(count = 30)
        val summary = ChatHistorySummary("s", "   ", coveredMessageCount = 20, updatedAt = 0L)

        val view = planner.plan(
            messages = all,
            summary = summary,
            compressionEnabled = true,
            thresholdTokens = 10,
            liveWindowSize = 10,
        )

        assertNull(view.earlierSummary)
        // The covered prefix still shifts the remainder, but with no summary text
        // the uncovered tail is dropped and flagged as truncated.
        assertEquals(all.takeLast(10), view.liveWindow)
        assertTrue(view.truncatedWithoutSummary)
    }

    @Test
    fun `given stale cursor beyond history size when plan then clamps without crashing`() {
        val all = messages(count = 12)
        val summary = ChatHistorySummary("s", "earlier summary", coveredMessageCount = 99, updatedAt = 0L)

        val view = planner.plan(
            messages = all,
            summary = summary,
            compressionEnabled = true,
            thresholdTokens = 1,
            liveWindowSize = 10,
        )

        // Covered clamps to 12 → remainder empty → empty live window, no crash.
        assertEquals("earlier summary", view.earlierSummary)
        assertTrue(view.liveWindow.isEmpty())
        assertEquals(0, view.droppedUncoveredCount)
    }

    @Test
    fun `approxTokenCount sums content length over chars per token`() {
        val all = messages(count = 4, contentLength = 40)
        // 4 * 40 = 160 chars / 4 = 40 tokens.
        assertEquals(40, ChatHistoryWindowPlanner.approxTokenCount(all))
    }
}
