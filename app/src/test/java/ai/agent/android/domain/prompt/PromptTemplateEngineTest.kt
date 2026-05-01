package ai.agent.android.domain.prompt

import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
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
    fun `given duplicate keys when render then last provider wins`() = runTest {
        val first = providerOf("DATE", "first")
        val second = providerOf("DATE", "second")

        val result = engine.render("\$DATE", listOf(first, second))

        assertEquals("second", result)
    }

    private fun providerOf(key: String, value: String): PromptVariableProvider =
        mockk<PromptVariableProvider>().also {
            every { it.key() } returns key
            coEvery { it.resolve() } returns value
        }
}
