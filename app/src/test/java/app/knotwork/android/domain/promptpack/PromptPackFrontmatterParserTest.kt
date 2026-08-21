package app.knotwork.android.domain.promptpack

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pins the accepted grammar of [PromptPackFrontmatterParser].
 *
 * The parser is a documented subset of YAML rather than a YAML
 * implementation, so its value depends entirely on the boundary being
 * written down and held: every shape below is either accepted with a stated
 * meaning or rejected with a named reason. "Best effort" parsing of a file
 * that instructs a language model is the failure mode this test exists to
 * prevent.
 */
class PromptPackFrontmatterParserTest {

    private fun parsed(document: String): FrontmatterParseResult.Parsed =
        PromptPackFrontmatterParser.parse(document) as FrontmatterParseResult.Parsed

    private fun reason(document: String): FrontmatterParseResult.Reason =
        (PromptPackFrontmatterParser.parse(document) as FrontmatterParseResult.Invalid).reason

    @Test
    fun `given scalar entries when parsed then values are unquoted and trimmed`() {
        val result = parsed(
            """
            ---
            name:   Concise assistant
            description: "A quoted: value"
            other: 'single quoted'
            ---
            Body text.
            """.trimIndent(),
        )

        assertEquals(FrontmatterValue.Scalar("Concise assistant"), result.entries["name"])
        assertEquals(FrontmatterValue.Scalar("A quoted: value"), result.entries["description"])
        assertEquals(FrontmatterValue.Scalar("single quoted"), result.entries["other"])
        assertEquals("Body text.", result.body)
    }

    @Test
    fun `given an inline list when parsed then entries are split and trimmed`() {
        val result = parsed(
            """
            ---
            tags: [concise,  starter , json]
            ---
            Body.
            """.trimIndent(),
        )

        assertEquals(FrontmatterValue.Items(listOf("concise", "starter", "json")), result.entries["tags"])
    }

    @Test
    fun `given a block list when parsed then it produces the same shape as an inline list`() {
        val result = parsed(
            """
            ---
            tags:
              - concise
              - starter
            ---
            Body.
            """.trimIndent(),
        )

        assertEquals(FrontmatterValue.Items(listOf("concise", "starter")), result.entries["tags"])
    }

    @Test
    fun `given a nested map when parsed then only its child key names are recorded`() {
        val result = parsed(
            """
            ---
            metadata:
              author: example-org
              version: "1.0"
            name: After the block
            ---
            Body.
            """.trimIndent(),
        )

        assertEquals(FrontmatterValue.Block(listOf("author", "version")), result.entries["metadata"])
        // The key after the block still parses: the block reader has to stop
        // at the first non-indented line, not swallow the rest of the header.
        assertEquals(FrontmatterValue.Scalar("After the block"), result.entries["name"])
    }

    @Test
    fun `given a whole-line comment when parsed then it is ignored`() {
        val result = parsed(
            """
            ---
            # a comment
            name: Kept
            ---
            Body.
            """.trimIndent(),
        )

        assertEquals(1, result.entries.size)
        assertEquals(FrontmatterValue.Scalar("Kept"), result.entries["name"])
    }

    @Test
    fun `given a hash inside a value when parsed then it stays part of the value`() {
        // Comments are recognised only on their own line, so a prompt that
        // talks about hashtags survives being written into the header.
        val result = parsed(
            """
            ---
            description: use #hashtags freely
            ---
            Body.
            """.trimIndent(),
        )

        assertEquals(FrontmatterValue.Scalar("use #hashtags freely"), result.entries["description"])
    }

    @Test
    fun `given a body containing a delimiter line when parsed then only the header is consumed`() {
        val result = parsed(
            """
            ---
            name: Kept
            ---
            Intro.

            ---

            A horizontal rule above.
            """.trimIndent(),
        )

        assertEquals(FrontmatterValue.Scalar("Kept"), result.entries["name"])
        assertTrue(result.body.contains("---"))
        assertTrue(result.body.startsWith("Intro."))
    }

    @Test
    fun `given a leading byte-order mark when parsed then the delimiter is still recognised`() {
        val result = parsed("\uFEFF---\nname: Kept\n---\nBody.")

        assertEquals(FrontmatterValue.Scalar("Kept"), result.entries["name"])
    }

    @Test
    fun `given no opening delimiter when parsed then the reason is missing delimiter`() {
        assertEquals(
            FrontmatterParseResult.Reason.MISSING_DELIMITER,
            reason("name: Nope\n---\nBody."),
        )
    }

    @Test
    fun `given no closing delimiter when parsed then the reason is unterminated`() {
        assertEquals(
            FrontmatterParseResult.Reason.UNTERMINATED,
            reason("---\nname: Nope\nBody."),
        )
    }

    @Test
    fun `given a line that is not an entry when parsed then the reason is malformed entry`() {
        assertEquals(
            FrontmatterParseResult.Reason.MALFORMED_ENTRY,
            reason("---\nname: Fine\njust some prose\n---\nBody."),
        )
    }

    @Test
    fun `given an illegal key when parsed then the reason is malformed entry`() {
        assertEquals(
            FrontmatterParseResult.Reason.MALFORMED_ENTRY,
            reason("---\n9lives: nope\n---\nBody."),
        )
    }

    @Test
    fun `given a bare key with nothing under it when parsed then the reason is malformed entry`() {
        // A key the author plainly meant to fill in is not an empty value.
        assertEquals(
            FrontmatterParseResult.Reason.MALFORMED_ENTRY,
            reason("---\ntags:\nname: After\n---\nBody."),
        )
    }

    @Test
    fun `given the same key twice when parsed then the reason is duplicate key`() {
        assertEquals(
            FrontmatterParseResult.Reason.DUPLICATE_KEY,
            reason("---\nname: One\nname: Two\n---\nBody."),
        )
    }

    @Test
    fun `given blank lines above the body when parsed then they are dropped but inner blanks are kept`() {
        val result = parsed("---\nname: Kept\n---\n\n\nFirst.\n\nSecond.\n")

        assertEquals("First.\n\nSecond.", result.body)
    }
}
