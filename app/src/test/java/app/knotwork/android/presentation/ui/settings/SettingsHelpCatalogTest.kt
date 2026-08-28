package app.knotwork.android.presentation.ui.settings

import android.content.Context
import app.knotwork.android.domain.settings.SettingsRegistry
import app.knotwork.android.domain.settings.anchorKey
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

/**
 * The completeness gate for settings help text.
 *
 * Its predecessor, `SettingsSearchCatalogTest`, reads like a gate of this kind
 * and is one on **names only**: `descRes` is nullable and no assertion looks at
 * it, so a row could be registered with no description, or have a stale one
 * deleted, and the build stayed green. That is how one setting came to be
 * explained in four different places in four different wordings — one of them
 * quoting a threshold (`8 s`) that no constant in the code holds.
 *
 * So this gate asserts what that one could not: that every registry row is
 * **decided** — explained, or recorded as deliberately unexplained with a
 * reason — and that every explanation actually resolves to text a reader could
 * use. The verdict is a pure function of the repository, so per the project's
 * static-analysis policy it blocks the build rather than filing a report.
 */
@RunWith(RobolectricTestRunner::class)
class SettingsHelpCatalogTest {

    private val context: Context get() = RuntimeEnvironment.getApplication()

    @Test
    fun `every registry row is decided and no decision is orphaned`() {
        val anchors = SettingsRegistry.allEntries().map { it.anchorKey() }.toSet()
        assertEquals(
            "HELP must decide exactly the registry anchors",
            anchors,
            SettingsHelpCatalog.HELP.keys,
        )
    }

    @Test
    fun `every explained row resolves to non-blank text`() {
        explanations().forEach { (anchor, help) ->
            assertTrue("zero string resource for $anchor", help.res != 0)
            assertTrue("blank help text for $anchor", context.getString(help.res).isNotBlank())
        }
    }

    @Test
    fun `no explanation exceeds the character ceiling`() {
        explanations().forEach { (anchor, help) ->
            val length = context.getString(help.res).length
            assertTrue(
                "help for $anchor is $length characters, over the $MAX_HELP_CHARS ceiling",
                length <= MAX_HELP_CHARS,
            )
        }
    }

    @Test
    fun `no two rows are explained by the same sentence`() {
        val byText = explanations().groupBy { (_, help) -> context.getString(help.res) }
        val shared = byText.filterValues { it.size > 1 }
        assertTrue(
            "the same sentence explains more than one row: ${shared.mapValues { entry ->
                entry.value.map { it.first }
            }}",
            shared.isEmpty(),
        )
    }

    @Test
    fun `no explanation uses the phrasings the copy standard forbids`() {
        explanations().forEach { (anchor, help) ->
            val text = context.getString(help.res).lowercase()
            FORBIDDEN_PHRASES.forEach { phrase ->
                assertTrue("help for $anchor uses the forbidden phrasing \"$phrase\"", !text.contains(phrase))
            }
        }
    }

    @Test
    fun `the controller resolves explained rows and stays silent on the rest`() {
        val controller = SettingsHelpCatalog.controller(context)
        val explained = explanations().first().first
        val unexplained = SettingsHelpCatalog.HELP.entries.first { it.value is SettingHelp.None }.key

        assertEquals(context.getString(explanations().first().second.res), controller.hintFor(explained)?.text)
        assertEquals(null, controller.hintFor(unexplained))
        assertEquals(null, controller.hintFor(null))
    }

    @Test
    fun `only one hint is open at a time`() {
        val controller = SettingsHelpCatalog.controller(context)

        controller.toggle("TEMPERATURE")
        assertEquals("TEMPERATURE", controller.expandedAnchor)

        controller.toggle("TOP_K")
        assertEquals("opening a hint must close the one already open", "TOP_K", controller.expandedAnchor)

        controller.toggle("TOP_K")
        assertEquals("tapping the open hint closes it", null, controller.expandedAnchor)
    }

    /** Every row that carries an explanation, paired with its anchor. */
    private fun explanations(): List<Pair<String, SettingHelp.Text>> = SettingsHelpCatalog.HELP.entries
        .mapNotNull { (anchor, help) -> (help as? SettingHelp.Text)?.let { anchor to it } }

    private companion object {
        /**
         * The hard ceiling, measured rather than chosen by taste: at 200 % font
         * scale a sentence this long fills about half of a 760 dp screen, which
         * is the point past which the row being explained is pushed off the top
         * and the reader loses what the sentence is about.
         */
        const val MAX_HELP_CHARS = 140

        /**
         * Phrasings the copy standard rules out. Each one describes the control
         * rather than what the reader will notice, which is the register that
         * went unread in closed testing.
         */
        val FORBIDDEN_PHRASES = listOf(
            "enables ",
            "disables ",
            "allows you to",
            "this setting",
            "when enabled",
        )
    }
}
