package app.knotwork.android.domain.usecases

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for [ParseSharedContentUseCase], covering the text / image / mixed
 * / empty branches and the MIME-type guard against a non-image stream.
 */
class ParseSharedContentUseCaseTest {

    private val useCase = ParseSharedContentUseCase()

    @Test
    fun `given plain text share when invoked then keeps trimmed text and no image`() {
        val result = useCase(mimeType = "text/plain", text = "  hello world  ", streamUri = null)

        assertEquals("hello world", result.text)
        assertNull(result.imageUri)
        assertTrue(!result.isEmpty)
    }

    @Test
    fun `given image share when invoked then keeps image uri and no text`() {
        val result = useCase(mimeType = "image/jpeg", text = null, streamUri = "content://media/42")

        assertNull(result.text)
        assertEquals("content://media/42", result.imageUri)
    }

    @Test
    fun `given image with caption when invoked then keeps both`() {
        val result = useCase(mimeType = "image/png", text = "caption", streamUri = "content://media/7")

        assertEquals("caption", result.text)
        assertEquals("content://media/7", result.imageUri)
    }

    @Test
    fun `given uppercase image mime when invoked then keeps the image`() {
        val result = useCase(mimeType = "IMAGE/JPEG", text = null, streamUri = "content://media/9")

        assertEquals("content://media/9", result.imageUri)
    }

    @Test
    fun `given non-image mime with a stream when invoked then drops the stream`() {
        val result = useCase(mimeType = "application/pdf", text = "doc", streamUri = "content://docs/1")

        assertEquals("doc", result.text)
        assertNull(result.imageUri)
    }

    @Test
    fun `given blank text and null stream when invoked then payload is empty`() {
        val result = useCase(mimeType = "text/plain", text = "   ", streamUri = null)

        assertNull(result.text)
        assertNull(result.imageUri)
        assertTrue(result.isEmpty)
    }

    @Test
    fun `given blank stream uri when invoked then no image`() {
        val result = useCase(mimeType = "image/jpeg", text = null, streamUri = "  ")

        assertNull(result.imageUri)
        assertTrue(result.isEmpty)
    }
}
