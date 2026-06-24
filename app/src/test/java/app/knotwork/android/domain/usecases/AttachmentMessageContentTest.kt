package app.knotwork.android.domain.usecases

import app.knotwork.android.domain.constants.DefaultPrompts
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Unit tests for [AttachmentMessageContent], the shared image-only message
 * contract used by both the composer and the share target.
 */
class AttachmentMessageContentTest {

    @Test
    fun `given a caption when resolved then prompt is the caption and display is null`() {
        val result = AttachmentMessageContent.resolve("look at this")

        assertEquals("look at this", result.prompt)
        assertNull(result.displayContent)
    }

    @Test
    fun `given an empty caption when resolved then uses the image-only instruction and empty display`() {
        val result = AttachmentMessageContent.resolve("")

        assertEquals(DefaultPrompts.IMAGE_ONLY_DEFAULT_INSTRUCTION, result.prompt)
        assertEquals("", result.displayContent)
    }
}
