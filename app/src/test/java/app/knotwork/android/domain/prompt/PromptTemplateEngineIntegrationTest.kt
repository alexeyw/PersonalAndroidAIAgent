package app.knotwork.android.domain.prompt

import app.knotwork.android.data.prompt.DateVariableProvider
import app.knotwork.android.data.prompt.TimeVariableProvider
import app.knotwork.android.data.prompt.ToolsVariableProvider
import app.knotwork.android.domain.models.AgentTool
import app.knotwork.android.domain.repositories.ToolRepository
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Before
import org.junit.Test
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import java.util.Locale

/**
 * Integration tests for [PromptTemplateEngine] wired with the real built-in
 * [app.knotwork.android.domain.prompt.PromptVariableProvider] implementations
 * (`$DATE`, `$TIME`, `$TOOLS`).
 *
 * Unlike the unit tests in `PromptTemplateEngineTest`, these tests exercise the
 * actual provider classes (only their external collaborators — `Clock`, `Locale`,
 * `ToolRepository` — are stubbed) to catch regressions where the engine and the
 * providers drift apart in subtle ways:
 *
 * - `$TOOLS` MUST reflect the current tool list on every render (no caching).
 * - `$DATE` and `$TIME` MUST resolve fresh values on every render so the device
 *   clock and time-zone changes are picked up live.
 * - The combined `Сегодня $DATE, время $TIME. Доступные инструменты: $TOOLS`
 *   template — used by the preset pipeline — MUST render exactly once with
 *   correct substitutions when all three providers are present.
 */
class PromptTemplateEngineIntegrationTest {

    private lateinit var engine: PromptTemplateEngine
    private lateinit var toolRepository: ToolRepository

    @Before
    fun setUp() {
        engine = PromptTemplateEngine()
        toolRepository = mockk()
    }

    @Test
    fun `given changing tools list when render twice then TOOLS reflects each call`() = runTest {
        // ToolsVariableProvider must NOT cache the tools list — disabling a tool or
        // adding an MCP connection between renders has to be visible immediately.
        coEvery { toolRepository.getAvailableTools() } returnsMany listOf(
            listOf(
                AgentTool(name = "search", description = "Searches the web", parameters = "{}"),
            ),
            listOf(
                AgentTool(name = "search", description = "Searches the web", parameters = "{}"),
                AgentTool(name = "calendar", description = "Reads the calendar", parameters = "{}"),
            ),
        )
        val tools = ToolsVariableProvider(toolRepository)

        val firstRender = engine.render("Tools: \$TOOLS", listOf(tools))
        val secondRender = engine.render("Tools: \$TOOLS", listOf(tools))

        assertEquals("Tools: search — Searches the web", firstRender)
        assertEquals(
            "Tools: search — Searches the web\ncalendar — Reads the calendar",
            secondRender,
        )
    }

    @Test
    fun `given empty tools list when render then TOOLS resolves to empty string`() = runTest {
        coEvery { toolRepository.getAvailableTools() } returns emptyList()
        val tools = ToolsVariableProvider(toolRepository)

        val rendered = engine.render("Active tools: [\$TOOLS]", listOf(tools))

        assertEquals("Active tools: []", rendered)
    }

    @Test
    fun `given advancing clock when render twice then DATE and TIME are not cached`() = runTest {
        // Two distinct fixed clocks fed through ArrayDeque ensure that each render
        // re-invokes the clock provider. If PromptTemplateEngine memoised the
        // resolved value (or the providers cached the clock at construction), the
        // second render would echo the first value and the test would fail.
        val dateClocks = ArrayDeque(
            listOf(
                Clock.fixed(Instant.parse("2026-05-01T12:00:00Z"), ZoneId.of("UTC")),
                Clock.fixed(Instant.parse("2026-05-02T12:00:00Z"), ZoneId.of("UTC")),
            ),
        )
        val timeClocks = ArrayDeque(
            listOf(
                Clock.fixed(Instant.parse("2026-05-01T09:07:00Z"), ZoneId.of("UTC")),
                Clock.fixed(Instant.parse("2026-05-01T18:42:00Z"), ZoneId.of("UTC")),
            ),
        )
        val date = DateVariableProvider(
            clockProvider = { dateClocks.removeFirst() },
            localeProvider = { Locale.ENGLISH },
        )
        val time = TimeVariableProvider(clockProvider = { timeClocks.removeFirst() })

        val firstRender = engine.render("\$DATE \$TIME", listOf(date, time))
        val secondRender = engine.render("\$DATE \$TIME", listOf(date, time))

        assertEquals("01 May 2026 09:07", firstRender)
        assertEquals("02 May 2026 18:42", secondRender)
        assertNotEquals(
            "Date/Time renders must not be cached between calls",
            firstRender,
            secondRender,
        )
    }

    @Test
    fun `given preset pipeline template with all three providers when render then substitutes correctly`() = runTest {
        // Mirrors the `Сегодня $DATE, время $TIME. Доступные инструменты: $TOOLS`
        // smoke prompt called out by the Phase 14 acceptance criteria, exercising
        // every built-in provider end to end.
        coEvery { toolRepository.getAvailableTools() } returns listOf(
            AgentTool(name = "search", description = "Searches the web", parameters = "{}"),
            AgentTool(name = "calendar", description = "Reads the calendar", parameters = "{}"),
        )
        val date = DateVariableProvider(
            clockProvider = { Clock.fixed(Instant.parse("2026-05-01T09:07:00Z"), ZoneId.of("UTC")) },
            localeProvider = { Locale.ENGLISH },
        )
        val time = TimeVariableProvider(
            clockProvider = { Clock.fixed(Instant.parse("2026-05-01T09:07:00Z"), ZoneId.of("UTC")) },
        )
        val tools = ToolsVariableProvider(toolRepository)

        val rendered = engine.render(
            "Сегодня \$DATE, время \$TIME. Доступные инструменты: \$TOOLS",
            listOf(date, time, tools),
        )

        assertEquals(
            "Сегодня 01 May 2026, время 09:07. Доступные инструменты: " +
                "search — Searches the web\ncalendar — Reads the calendar",
            rendered,
        )
    }

    @Test
    fun `given preset template when renderSegments then highlights resolved variables`() = runTest {
        // The prompt-preview UI consumes renderSegments to colour resolved variables
        // distinctly. Verify that real providers feed the segment list correctly.
        coEvery { toolRepository.getAvailableTools() } returns listOf(
            AgentTool(name = "ping", description = "Pings a host", parameters = "{}"),
        )
        val date = DateVariableProvider(
            clockProvider = { Clock.fixed(Instant.parse("2026-05-01T00:00:00Z"), ZoneId.of("UTC")) },
            localeProvider = { Locale.ENGLISH },
        )
        val time = TimeVariableProvider(
            clockProvider = { Clock.fixed(Instant.parse("2026-05-01T00:00:00Z"), ZoneId.of("UTC")) },
        )
        val tools = ToolsVariableProvider(toolRepository)

        val segments = engine.renderSegments(
            "[\$DATE] [\$TIME] [\$TOOLS]",
            listOf(date, time, tools),
        )

        assertEquals(
            listOf(
                PromptSegment.Literal("["),
                PromptSegment.Resolved("DATE", "01 May 2026"),
                PromptSegment.Literal("] ["),
                PromptSegment.Resolved("TIME", "00:00"),
                PromptSegment.Literal("] ["),
                PromptSegment.Resolved("TOOLS", "ping — Pings a host"),
                PromptSegment.Literal("]"),
            ),
            segments,
        )
    }
}
