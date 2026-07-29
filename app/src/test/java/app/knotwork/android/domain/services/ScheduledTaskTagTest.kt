package app.knotwork.android.domain.services

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Verifies [ScheduledTaskTag]: the label a scheduled task carries is the only
 * thing the task monitor can say about it (a queued task's input data is not
 * readable), so it has to survive a round trip through a plain tag string and
 * degrade to `null` — never to a wrong label — on anything it does not recognise.
 */
class ScheduledTaskTagTest {

    @Test
    fun `given a periodic task when encoded then it round-trips`() {
        val tag = ScheduledTaskTag.encode(
            kind = ScheduledTaskKind.PERIODIC,
            intervalHours = 6,
            sessionId = "session-1",
            prompt = "write the evening journal entry",
        )

        assertEquals(
            ScheduledTaskLabel(
                kind = ScheduledTaskKind.PERIODIC,
                intervalHours = 6,
                sessionId = "session-1",
                promptPreview = "write the evening journal entry",
            ),
            ScheduledTaskTag.parse(setOf(tag)),
        )
    }

    @Test
    fun `given a one-time task without a session when encoded then the session reads back as null`() {
        val tag = ScheduledTaskTag.encode(ScheduledTaskKind.ONE_TIME, intervalHours = 0, sessionId = null, prompt = "x")

        val label = ScheduledTaskTag.parse(setOf(tag))

        assertEquals(ScheduledTaskKind.ONE_TIME, label?.kind)
        assertEquals(0L, label?.intervalHours)
        // An empty field must not become an empty-string session id that then
        // fails to resolve against any chat.
        assertNull(label?.sessionId)
    }

    @Test
    fun `given a prompt containing the separator when encoded then the preview survives intact`() {
        // The preview is the last field precisely so free-form text cannot
        // desynchronise the parse.
        val prompt = "summarise a|b|c"

        val label = ScheduledTaskTag.parse(
            setOf(ScheduledTaskTag.encode(ScheduledTaskKind.ONE_TIME, 0, "s", prompt)),
        )

        assertEquals(prompt, label?.promptPreview)
    }

    @Test
    fun `given a multi-line prompt when encoded then the preview is a single line`() {
        val label = ScheduledTaskTag.parse(
            setOf(ScheduledTaskTag.encode(ScheduledTaskKind.ONE_TIME, 0, null, "  first\n\tsecond   third  ")),
        )

        assertEquals("first second third", label?.promptPreview)
    }

    @Test
    fun `given a long prompt when encoded then the preview is truncated and marked`() {
        val prompt = "a".repeat(500)

        val preview = ScheduledTaskTag.parse(
            setOf(ScheduledTaskTag.encode(ScheduledTaskKind.PERIODIC, 1, null, prompt)),
        )?.promptPreview

        // A tag is a label, not a copy of the instruction.
        assertEquals(ScheduledTaskTag.PROMPT_PREVIEW_MAX_CHARS + 1, preview?.length)
        assertTrue(preview.orEmpty().endsWith("…"))
    }

    @Test
    fun `given only unrelated tags when parsed then there is no label`() {
        // Every work item also carries the worker class name and the marker.
        val label = ScheduledTaskTag.parse(
            setOf(ScheduledTaskTag.MARKER, WORKER_CLASS_TAG),
        )

        assertNull(label)
    }

    @Test
    fun `given a truncated or future-format tag when parsed then it degrades to no label`() {
        assertNull(ScheduledTaskTag.parse(setOf("kst1|ONE_TIME|0")))
        assertNull(ScheduledTaskTag.parse(setOf("kst1|TELEPORT|0|s|prompt")))
        assertNull(ScheduledTaskTag.parse(setOf("kst1|ONE_TIME|soon|s|prompt")))
        assertNull(ScheduledTaskTag.parse(emptySet()))
    }

    private companion object {
        /**
         * Stand-in for the tag the background runtime adds by itself (the worker
         * class name). Only its presence matters here: an unrelated tag must not
         * be mistaken for a label.
         */
        const val WORKER_CLASS_TAG = "AgentWorker"
    }
}
