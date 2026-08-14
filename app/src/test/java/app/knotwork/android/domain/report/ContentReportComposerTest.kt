package app.knotwork.android.domain.report

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for [ContentReportComposer].
 *
 * The report is the one piece of user content the app deliberately hands out,
 * so these tests pin down exactly what it carries: the user's words, the model
 * output (bounded and marked when clipped), and a fixed set of build facts —
 * nothing else.
 */
class ContentReportComposerTest {

    @Test
    fun `given a reason when subject is built then it names the category`() {
        val subject = ContentReportComposer.subject(report(reason = ContentReportReason.MISLEADING))

        assertEquals("Content report: misleading output", subject)
    }

    @Test
    fun `given a note when body is built then it carries the note and the category`() {
        val body = ContentReportComposer.body(
            report(reason = ContentReportReason.HATE_OR_HARASSMENT, note = "  it insulted me  "),
        )

        assertTrue("note is missing:\n$body", body.contains("it insulted me"))
        assertTrue("category is missing:\n$body", body.contains("**Category:** hate or harassment"))
    }

    @Test
    fun `given a blank note when body is built then it says no detail was given`() {
        val body = ContentReportComposer.body(report(note = "   "))

        assertTrue("blank note must be stated explicitly:\n$body", body.contains("no additional detail"))
    }

    @Test
    fun `given model output when body is built then every line is block quoted`() {
        val body = ContentReportComposer.body(report(messageText = "first line\nsecond line"))

        assertTrue("first line is not quoted:\n$body", body.contains("> first line"))
        assertTrue("second line is not quoted:\n$body", body.contains("> second line"))
    }

    @Test
    fun `given oversized output when body is built then the quote is capped and the loss is stated`() {
        val oversized = "x".repeat(ContentReportComposer.MAX_QUOTED_CHARS + EXCESS_CHARS)

        val body = ContentReportComposer.body(report(messageText = oversized))

        assertTrue(
            "the omission must be counted, not hidden:\n${body.takeLast(TAIL_CHARS)}",
            body.contains("[truncated — $EXCESS_CHARS more characters]"),
        )
        assertFalse("the full text must not survive the cap", body.contains(oversized))
    }

    @Test
    fun `given output at the cap when body is built then nothing is marked as truncated`() {
        val exact = "x".repeat(ContentReportComposer.MAX_QUOTED_CHARS)

        val body = ContentReportComposer.body(report(messageText = exact))

        assertFalse("a report at the boundary must not claim truncation:\n$body", body.contains("truncated"))
    }

    @Test
    fun `given build metadata when body is built then only the declared facts appear`() {
        val body = ContentReportComposer.body(report())

        assertTrue(body.contains("- App: 0.7.0 (abc1234)"))
        assertTrue(body.contains("- Device: Acme Phone 9 · Android 16"))
        assertTrue(body.contains("- Model: gemma-4-e2b"))
    }

    @Test
    fun `given no known model when body is built then the model line is omitted`() {
        val body = ContentReportComposer.body(report(modelIdentifier = null))

        assertFalse("an unknown model must not print an empty line:\n$body", body.contains("- Model:"))
    }

    /**
     * Builds a report with sensible defaults so each test states only the field
     * it exercises.
     *
     * @return A populated [ContentReport].
     */
    private fun report(
        reason: ContentReportReason = ContentReportReason.HARMFUL_OR_UNSAFE,
        note: String = "the answer told me to mix bleach and ammonia",
        messageText: String = "mix bleach and ammonia",
        modelIdentifier: String? = "gemma-4-e2b",
    ) = ContentReport(
        reason = reason,
        note = note,
        messageText = messageText,
        appVersion = "0.7.0",
        buildIdentifier = "abc1234",
        device = "Acme Phone 9",
        androidVersion = "16",
        modelIdentifier = modelIdentifier,
    )

    private companion object {
        /** How far the oversized fixture overshoots the quote cap. */
        const val EXCESS_CHARS = 250

        /** Slice of the body printed in the failure message. */
        const val TAIL_CHARS = 200
    }
}
