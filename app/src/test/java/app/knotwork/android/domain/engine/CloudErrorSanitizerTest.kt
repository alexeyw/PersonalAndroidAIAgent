package app.knotwork.android.domain.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CloudErrorSanitizerTest {

    @Test
    fun `given a Google transport error when sanitized then the api key is gone`() {
        // Verbatim shape measured against a stalled stub: Google authenticates by query
        // parameter, so a plain socket timeout already carries the key.
        val raw = "Error from client: GoogleLLMClient\nMessage: Socket timeout has expired " +
            "[url=https://generativelanguage.googleapis.com/v1beta/models/" +
            "gemini-3-flash-preview:streamGenerateContent?alt=sse&key=AIzaSyREALSECRET123, " +
            "socket_timeout=60000] ms"

        val sanitized = CloudErrorSanitizer.sanitize(raw)

        assertFalse("the key must not survive", sanitized.contains("AIzaSyREALSECRET123"))
        assertTrue("the redaction must be visible", sanitized.contains("key=***"))
        assertTrue("the diagnosis must survive", sanitized.contains("Socket timeout has expired"))
        assertTrue("unrelated params must survive", sanitized.contains("alt=sse"))
    }

    @Test
    fun `given a bearer token when sanitized then only the token is removed`() {
        val sanitized = CloudErrorSanitizer.sanitize("401 Unauthorized (Authorization: Bearer sk-abc123XYZ)")

        assertFalse(sanitized.contains("sk-abc123XYZ"))
        assertTrue(sanitized.contains("Bearer ***"))
        assertTrue(sanitized.contains("401 Unauthorized"))
    }

    @Test
    fun `given assorted secret parameter names when sanitized then each value is masked`() {
        val sanitized = CloudErrorSanitizer.sanitize(
            "failed url=https://x/y?api_key=one&access_token=two&token=three&password=four&page=7",
        )

        listOf("one", "two", "three", "four").forEach {
            assertFalse("leaked $it", sanitized.contains(it))
        }
        assertTrue("non-secret params must survive", sanitized.contains("page=7"))
    }

    @Test
    fun `given a message with no secret when sanitized then it is unchanged`() {
        val raw = "Error from client: DeepSeekLLMClient\nMessage: Socket timeout has expired " +
            "[url=https://api.deepseek.com/chat/completions, socket_timeout=60000] ms"

        assertEquals(raw, CloudErrorSanitizer.sanitize(raw))
    }

    @Test
    fun `given a blank or null message when sanitized then a readable fallback is returned`() {
        assertEquals("Unknown error", CloudErrorSanitizer.sanitize(null))
        assertEquals("Unknown error", CloudErrorSanitizer.sanitize("   "))
    }
}
