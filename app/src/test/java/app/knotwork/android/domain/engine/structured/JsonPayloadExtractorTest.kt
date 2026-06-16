package app.knotwork.android.domain.engine.structured

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Unit tests for [JsonPayloadExtractor], covering each packaging a local model
 * may wrap a JSON payload in — bare, fenced, and embedded in prose — for both
 * object and array shapes, plus the non-JSON fallback.
 */
class JsonPayloadExtractorTest {

    @Test
    fun `given a bare json object then it is returned unchanged`() {
        assertEquals("""{"a":1}""", JsonPayloadExtractor.extract("""{"a":1}"""))
    }

    @Test
    fun `given a bare json array then it is returned unchanged`() {
        assertEquals("[1,2,3]", JsonPayloadExtractor.extract("[1,2,3]"))
    }

    @Test
    fun `given a fenced json object then the inner body is extracted`() {
        val raw = "```json\n{\"verdict\":\"Pass\"}\n```"
        assertEquals("""{"verdict":"Pass"}""", JsonPayloadExtractor.extract(raw))
    }

    @Test
    fun `given a fenced block without a language tag then the inner body is extracted`() {
        val raw = "```\n[\"a\",\"b\"]\n```"
        assertEquals("""["a","b"]""", JsonPayloadExtractor.extract(raw))
    }

    @Test
    fun `given an object embedded in prose then only the object span is extracted`() {
        val raw = "Sure, here you go: {\"tool\":\"search\"} — hope that helps!"
        assertEquals("""{"tool":"search"}""", JsonPayloadExtractor.extract(raw))
    }

    @Test
    fun `given an array embedded in prose then only the array span is extracted`() {
        val raw = "The subtasks are [\"first\",\"second\"] in order."
        assertEquals("""["first","second"]""", JsonPayloadExtractor.extract(raw))
    }

    @Test
    fun `given both an object and an array then the earliest opening bracket wins`() {
        val raw = "{\"a\":1} and also [2,3]"
        assertEquals("""{"a":1}""", JsonPayloadExtractor.extract(raw))
    }

    @Test
    fun `given a multiline object then the whole span is extracted`() {
        val raw = "prefix\n{\n  \"a\": 1,\n  \"b\": 2\n}\nsuffix"
        assertEquals("{\n  \"a\": 1,\n  \"b\": 2\n}", JsonPayloadExtractor.extract(raw))
    }

    @Test
    fun `given output with no json then the trimmed input is returned`() {
        assertEquals("no json here", JsonPayloadExtractor.extract("  no json here  "))
    }

    @Test
    fun `given an empty fenced block then it falls through to bracket scanning`() {
        // An empty fence carries no payload, so extraction must not return the
        // blank capture group — it falls back to the embedded object instead.
        val raw = "```json``` {\"a\":1}"
        assertEquals("""{"a":1}""", JsonPayloadExtractor.extract(raw))
    }
}
