package app.knotwork.android.domain.prompt

import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Before
import org.junit.Test

class PromptTemplateEngineTest {

    private lateinit var engine: PromptTemplateEngine

    @Before
    fun setUp() {
        engine = PromptTemplateEngine()
    }

    @Test
    fun `given known variable when render then substitutes provider value`() = runTest {
        val provider = providerOf("DATE", "01 May 2026")

        val result = engine.render("Today is \$DATE.", listOf(provider))

        assertEquals("Today is 01 May 2026.", result)
    }

    @Test
    fun `given multiple variables when render then substitutes all`() = runTest {
        val date = providerOf("DATE", "01 May 2026")
        val time = providerOf("TIME", "15:30")
        val tools = providerOf("TOOLS", "search, calendar")

        val result = engine.render(
            "\$DATE \$TIME — tools: \$TOOLS",
            listOf(date, time, tools),
        )

        assertEquals("01 May 2026 15:30 — tools: search, calendar", result)
    }

    @Test
    fun `given repeated variable when render then resolves it for every occurrence`() = runTest {
        val provider = providerOf("DATE", "01 May 2026")

        val result = engine.render("\$DATE / \$DATE", listOf(provider))

        assertEquals("01 May 2026 / 01 May 2026", result)
    }

    @Test
    fun `given unknown variable when render then leaves placeholder unchanged`() = runTest {
        val result = engine.render("Hello \$UNKNOWN world", emptyList())

        assertEquals("Hello \$UNKNOWN world", result)
    }

    @Test
    fun `given provider throws when render then substitutes empty string and continues`() = runTest {
        val failing = mockk<PromptVariableProvider>()
        every { failing.key() } returns "DATE"
        coEvery { failing.resolve() } throws RuntimeException("boom")
        val ok = providerOf("TIME", "15:30")

        val result = engine.render("[\$DATE] [\$TIME]", listOf(failing, ok))

        assertEquals("[] [15:30]", result)
    }

    @Test
    fun `given escaped variable when render then outputs literal dollar sign`() = runTest {
        val provider = providerOf("DATE", "01 May 2026")

        val result = engine.render("Literal: \\\$DATE, real: \$DATE", listOf(provider))

        assertEquals("Literal: \$DATE, real: 01 May 2026", result)
    }

    @Test
    fun `given variable with digits and underscore when render then matches full key`() = runTest {
        val provider = providerOf("MEMORY_SUMMARY", "5 items")

        val result = engine.render("Memory: \$MEMORY_SUMMARY!", listOf(provider))

        assertEquals("Memory: 5 items!", result)
    }

    @Test
    fun `given lowercase token when render then ignores it as not a placeholder`() = runTest {
        val provider = providerOf("DATE", "01 May 2026")

        val result = engine.render("Pay \$50 today \$date please", listOf(provider))

        // `$50` and `$date` do not match the [A-Z_][A-Z0-9_]* key shape.
        assertEquals("Pay \$50 today \$date please", result)
    }

    @Test
    fun `given empty template when render then returns empty string`() = runTest {
        val result = engine.render("", listOf(providerOf("DATE", "01 May 2026")))

        assertEquals("", result)
    }

    @Test
    fun `given template without variables when render then returns template unchanged`() = runTest {
        val template = "Plain prompt without placeholders."

        val result = engine.render(template, listOf(providerOf("DATE", "01 May 2026")))

        assertEquals(template, result)
    }

    @Test
    fun `given provider key throws when render then skips it and renders rest`() = runTest {
        val brokenKey = mockk<PromptVariableProvider>()
        every { brokenKey.key() } throws IllegalStateException("init failed")
        val ok = providerOf("TIME", "15:30")

        val result = engine.render("[\$DATE] [\$TIME]", listOf(brokenKey, ok))

        // Broken provider is silently skipped; $DATE has no provider and stays verbatim,
        // $TIME from the healthy provider is substituted as usual.
        assertEquals("[\$DATE] [15:30]", result)
    }

    @Test
    fun `given duplicate keys when render then last provider wins`() = runTest {
        val first = providerOf("DATE", "first")
        val second = providerOf("DATE", "second")

        val result = engine.render("\$DATE", listOf(first, second))

        assertEquals("second", result)
    }

    @Test
    fun `given empty template when renderSegments then returns empty list`() = runTest {
        val result = engine.renderSegments("", listOf(providerOf("DATE", "01 May 2026")))

        assertEquals(emptyList<PromptSegment>(), result)
    }

    @Test
    fun `given plain template when renderSegments then returns single literal`() = runTest {
        val result = engine.renderSegments("Plain text", emptyList())

        assertEquals(listOf(PromptSegment.Literal("Plain text")), result)
    }

    @Test
    fun `given known variable when renderSegments then emits literal and resolved`() = runTest {
        val provider = providerOf("DATE", "01 May 2026")

        val result = engine.renderSegments("Today is \$DATE.", listOf(provider))

        assertEquals(
            listOf(
                PromptSegment.Literal("Today is "),
                PromptSegment.Resolved("DATE", "01 May 2026"),
                PromptSegment.Literal("."),
            ),
            result,
        )
    }

    @Test
    fun `given unknown variable when renderSegments then emits Unknown segment`() = runTest {
        val result = engine.renderSegments("Hello \$UNKNOWN!", emptyList())

        assertEquals(
            listOf(
                PromptSegment.Literal("Hello "),
                PromptSegment.Unknown("UNKNOWN"),
                PromptSegment.Literal("!"),
            ),
            result,
        )
    }

    @Test
    fun `given mixed template when renderSegments then preserves order`() = runTest {
        val date = providerOf("DATE", "01 May 2026")

        val result = engine.renderSegments(
            "[\$DATE] and [\$MISSING] and [\$DATE]",
            listOf(date),
        )

        assertEquals(
            listOf(
                PromptSegment.Literal("["),
                PromptSegment.Resolved("DATE", "01 May 2026"),
                PromptSegment.Literal("] and ["),
                PromptSegment.Unknown("MISSING"),
                PromptSegment.Literal("] and ["),
                PromptSegment.Resolved("DATE", "01 May 2026"),
                PromptSegment.Literal("]"),
            ),
            result,
        )
    }

    @Test
    fun `given escaped variable when renderSegments then folds dollar into literal`() = runTest {
        val provider = providerOf("DATE", "01 May 2026")

        val result = engine.renderSegments("Lit \\\$DATE then \$DATE", listOf(provider))

        assertEquals(
            listOf(
                PromptSegment.Literal("Lit \$DATE then "),
                PromptSegment.Resolved("DATE", "01 May 2026"),
            ),
            result,
        )
    }

    @Test
    fun `given provider throws when renderSegments then resolves to empty value`() = runTest {
        val failing = mockk<PromptVariableProvider>()
        every { failing.key() } returns "DATE"
        coEvery { failing.resolve() } throws RuntimeException("boom")

        val result = engine.renderSegments("[\$DATE]", listOf(failing))

        assertEquals(
            listOf(
                PromptSegment.Literal("["),
                PromptSegment.Resolved("DATE", ""),
                PromptSegment.Literal("]"),
            ),
            result,
        )
    }

    @Test
    fun `given resolve throws CancellationException when render then rethrows`() = runTest {
        val cancelling = mockk<PromptVariableProvider>()
        every { cancelling.key() } returns "DATE"
        coEvery { cancelling.resolve() } throws CancellationException("cancelled")

        assertThrows(CancellationException::class.java) {
            kotlinx.coroutines.runBlocking {
                engine.render("[\$DATE]", listOf(cancelling))
            }
        }
    }

    @Test
    fun `given key throws CancellationException when render then rethrows`() = runTest {
        val cancelling = mockk<PromptVariableProvider>()
        every { cancelling.key() } throws CancellationException("cancelled")

        assertThrows(CancellationException::class.java) {
            kotlinx.coroutines.runBlocking {
                engine.render("[\$DATE]", listOf(cancelling))
            }
        }
    }

    private fun providerOf(key: String, value: String): PromptVariableProvider = mockk<PromptVariableProvider>().also {
        every { it.key() } returns key
        coEvery { it.resolve() } returns value
    }
}
