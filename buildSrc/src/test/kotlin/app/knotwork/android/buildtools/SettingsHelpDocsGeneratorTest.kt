package app.knotwork.android.buildtools

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for [SettingsHelpDocsGenerator].
 *
 * The cases that matter most here are not the happy path but the two shapes of
 * silent loss: a registry entry the parser fails to see, and a string resource
 * that has gone missing. Both used to render as a quietly shorter or emptier
 * table that the paired verify task then certified as correct.
 */
class SettingsHelpDocsGeneratorTest {

    @Test
    fun `renders one row per registry entry, one-line and multi-line alike`() {
        val rows = SettingsHelpDocsGenerator.buildRows(
            registrySource = REGISTRY,
            helpCatalogSource = HELP_CATALOG,
            searchCatalogSource = SEARCH_CATALOG,
            helpStringsXml = HELP_STRINGS,
            nameStringsXml = listOf(NAME_STRINGS),
        )

        assertEquals(4, rows.size)
        assertEquals(listOf("Temperature", "Top-K", "External providers", "Identity"), rows.map { it.name })
        assertEquals("Generation", rows.first().category)
        assertEquals("Low keeps it predictable; high makes it varied.", rows.first().meaning)
    }

    @Test
    fun `a row with no hint carries its recorded reason instead`() {
        val rows = SettingsHelpDocsGenerator.buildRows(
            registrySource = REGISTRY,
            helpCatalogSource = HELP_CATALOG,
            searchCatalogSource = SEARCH_CATALOG,
            helpStringsXml = HELP_STRINGS,
            nameStringsXml = listOf(NAME_STRINGS),
        )

        val identity = rows.single { it.name == "Identity" }
        assertEquals(null, identity.meaning)
        assertEquals("shows a value, decides nothing", identity.noHintReason)
    }

    @Test
    fun `an unknown no-hint reason fails generation rather than leaking the enum name`() {
        val invented = HELP_CATALOG.replace("NoHint.DISPLAY_ROW", "NoHint.SOME_NEW_REASON")

        val error = runCatching {
            SettingsHelpDocsGenerator.buildRows(
                registrySource = REGISTRY,
                helpCatalogSource = invented,
                searchCatalogSource = SEARCH_CATALOG,
                helpStringsXml = HELP_STRINGS,
                nameStringsXml = listOf(NAME_STRINGS),
            )
        }.exceptionOrNull()

        assertTrue(error is SettingsHelpDocsGenerator.GenerationException)
        assertTrue(error!!.message!!.contains("SOME_NEW_REASON"))
    }

    @Test
    fun `render is idempotent`() {
        val once = render(MARKDOWN)
        val twice = render(once)

        assertEquals(once, twice)
        assertTrue(once.contains("| **Temperature** |"))
    }

    @Test
    fun `drift is false for freshly rendered markdown and true after a hand edit`() {
        val fresh = render(MARKDOWN)
        assertFalse(drift(fresh))

        val tampered = fresh.replace("| **Temperature** | Low keeps", "| **Temperature** | Edited by hand keeps")
        assertTrue(drift(tampered))
    }

    @Test
    fun `a registry entry the parser misses fails the build rather than shortening the table`() {
        // The registry gains a setting; the help catalogue already decides it.
        // A parser that skipped it would render three rows against four
        // decisions — which is precisely the failure this asserts is loud.
        val crippled = REGISTRY.replace("""    setting("TOP_K", ADVANCED, SLIDER),""" + "\n", "")

        val error = runCatching {
            SettingsHelpDocsGenerator.buildRows(
                registrySource = crippled,
                helpCatalogSource = HELP_CATALOG,
                searchCatalogSource = SEARCH_CATALOG,
                helpStringsXml = HELP_STRINGS,
                nameStringsXml = listOf(NAME_STRINGS),
            )
        }.exceptionOrNull()

        assertTrue("expected a GenerationException, got $error", error is SettingsHelpDocsGenerator.GenerationException)
        assertTrue(error!!.message!!.contains("Rendered 3 rows but SettingsHelpCatalog decides 4 settings"))
    }

    @Test
    fun `a missing help string fails generation rather than emitting an empty cell`() {
        val withoutTemperature = HELP_STRINGS.replace(
            """<string name="settings_help_temperature">Low keeps it predictable; high makes it varied.</string>""",
            "",
        )

        val error = runCatching {
            SettingsHelpDocsGenerator.buildRows(
                registrySource = REGISTRY,
                helpCatalogSource = HELP_CATALOG,
                searchCatalogSource = SEARCH_CATALOG,
                helpStringsXml = withoutTemperature,
                nameStringsXml = listOf(NAME_STRINGS),
            )
        }.exceptionOrNull()

        assertTrue(error is SettingsHelpDocsGenerator.GenerationException)
        assertTrue(error!!.message!!.contains("settings_help_temperature"))
    }

    @Test
    fun `a display name living in another values file is still resolved`() {
        val split = NAME_STRINGS.replace(
            """<string name="name_providers">External providers</string>""",
            "",
        )
        val other = """<resources><string name="name_providers">External providers</string></resources>"""

        val rows = SettingsHelpDocsGenerator.buildRows(
            registrySource = REGISTRY,
            helpCatalogSource = HELP_CATALOG,
            searchCatalogSource = SEARCH_CATALOG,
            helpStringsXml = HELP_STRINGS,
            nameStringsXml = listOf(split, other),
        )

        assertEquals("External providers", rows.single { it.name == "External providers" }.name)
    }

    @Test
    fun `a category with no known display title fails generation`() {
        // The heading is looked up, not derived from the Kotlin name, because
        // the Pipelines category was deliberately renamed in the app and
        // humanising the variable would republish the title that rename removed.
        val renamed = REGISTRY.replace("MODELS_ENTRIES", "SOMETHING_NEW_ENTRIES")

        val error = runCatching {
            SettingsHelpDocsGenerator.buildRows(
                registrySource = renamed,
                helpCatalogSource = HELP_CATALOG,
                searchCatalogSource = SEARCH_CATALOG,
                helpStringsXml = HELP_STRINGS,
                nameStringsXml = listOf(NAME_STRINGS),
            )
        }.exceptionOrNull()

        assertTrue(error is SettingsHelpDocsGenerator.GenerationException)
        assertTrue(error!!.message!!.contains("SOMETHING_NEW"))
    }

    @Test
    fun `missing markers fail loudly`() {
        val error = runCatching { render("# Guide\n\nNo markers here.\n") }.exceptionOrNull()

        assertTrue(error is SettingsHelpDocsGenerator.GenerationException)
        assertTrue(error!!.message!!.contains("Markers"))
    }

    private fun render(markdown: String): String = SettingsHelpDocsGenerator.render(
        markdown = markdown,
        registrySource = REGISTRY,
        helpCatalogSource = HELP_CATALOG,
        searchCatalogSource = SEARCH_CATALOG,
        helpStringsXml = HELP_STRINGS,
        nameStringsXml = listOf(NAME_STRINGS),
    )

    private fun drift(markdown: String): Boolean = SettingsHelpDocsGenerator.drift(
        markdown = markdown,
        registrySource = REGISTRY,
        helpCatalogSource = HELP_CATALOG,
        searchCatalogSource = SEARCH_CATALOG,
        helpStringsXml = HELP_STRINGS,
        nameStringsXml = listOf(NAME_STRINGS),
    )

    private companion object {
        // Deliberately mixes the one-line and multi-line entry shapes, and a
        // `link(...)` and `row(...)` whose anchors are derived rather than
        // written: the real registry contains all four.
        val REGISTRY = """
            private val GENERATION_ENTRIES = listOf(
                setting(
                    "TEMPERATURE",
                    ADVANCED,
                    SLIDER,
                    syn = listOf("sampling", "creativity"),
                ),
                setting("TOP_K", ADVANCED, SLIDER),
            )

            private val MODELS_ENTRIES = listOf(
                link(BASIC, "Provider list", syn = listOf("cloud")),
            )

            private val ABOUT_ENTRIES = listOf(
                row(BASIC, IDENTITY, syn = listOf("device")),
            )
        """.trimIndent() + "\n"

        val HELP_CATALOG = """
            object SettingsHelpCatalog {
                val HELP: Map<String, SettingHelp> = mapOf(
                    "TEMPERATURE" to text(R.string.settings_help_temperature),
                    "TOP_K" to text(R.string.settings_help_top_k),
                    "LINK_PROVIDER_LIST" to none(NoHint.LINK_ROW),
                    "IDENTITY" to none(NoHint.DISPLAY_ROW),
                )
            }
        """.trimIndent()

        val SEARCH_CATALOG = """
            object SettingsSearchCatalog {
                private const val CRASH_REPORTING_ANCHOR = "IDENTITY"
                val SEARCH_STRINGS: Map<String, SettingsSearchStrings> = mapOf(
                    "TEMPERATURE" to strings(R.string.name_temperature, R.string.desc_temperature),
                    "TOP_K" to strings(R.string.name_top_k, R.string.desc_top_k),
                    "LINK_PROVIDER_LIST" to strings(R.string.name_providers, R.string.desc_providers),
                    CRASH_REPORTING_ANCHOR to strings(
                        R.string.name_identity,
                        R.string.desc_identity,
                    ),
                )
            }
        """.trimIndent()

        val HELP_STRINGS = """
            <resources>
                <string name="settings_help_temperature">Low keeps it predictable; high makes it varied.</string>
                <string name="settings_help_top_k">How many candidate words stay in play.</string>
            </resources>
        """.trimIndent()

        val NAME_STRINGS = """
            <resources>
                <string name="name_temperature">Temperature</string>
                <string name="name_top_k">Top-K</string>
                <string name="name_providers">External providers</string>
                <string name="name_identity">Identity</string>
            </resources>
        """.trimIndent()

        val MARKDOWN = """
            # User Guide

            ## Settings

            <!-- AUTO-GEN:SETTINGS_HELP -->
            <!-- /AUTO-GEN:SETTINGS_HELP -->

            ### Generation
        """.trimIndent() + "\n"
    }
}
