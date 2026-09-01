package app.knotwork.design.components.topbar

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.knotwork.design.screens.automation.ExternalAutomationJournalCallbacks
import app.knotwork.design.screens.automation.ExternalAutomationJournalContent
import app.knotwork.design.screens.automation.ExternalAutomationJournalStrings
import app.knotwork.design.screens.automation.ExternalAutomationPreview
import app.knotwork.design.screens.triggers.TriggersCallbacks
import app.knotwork.design.screens.triggers.TriggersContent
import app.knotwork.design.screens.triggers.TriggersPreview
import app.knotwork.design.screens.triggers.TriggersStrings
import app.knotwork.design.theme.KnotworkTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * Behaviour of the journal-export actions on both journal surfaces — the parts a
 * Roborazzi baseline cannot show.
 *
 * A snapshot proves the two glyphs are drawn in the top bar. It cannot prove that
 * a screen reader can reach them, that they meet the touch minimum, or — the one
 * that matters most here — that each does what its own label says. This phase has
 * repeatedly found controls that saved, exported and looked alive while reaching
 * nothing, so a new pair of them arrives with the wiring asserted rather than
 * assumed.
 *
 * Both screens are exercised from the same test because the whole point of
 * [JournalExportActions] is that they behave identically; a guard that checked
 * only one would let the other drift.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [36], qualifiers = "w360dp-h760dp-xhdpi")
class JournalExportActionsBehaviourTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private val triggerStrings = TriggersStrings()
    private val journalStrings = ExternalAutomationJournalStrings()

    @Test
    fun `the triggers list exposes both export actions and routes each to its own callback`() {
        var shares = 0
        var saves = 0
        composeTestRule.setContent {
            KnotworkTheme {
                TriggersContent(
                    state = TriggersPreview.populated(),
                    callbacks = TriggersCallbacks(
                        onShareJournal = { shares++ },
                        onSaveJournal = { saves++ },
                    ),
                )
            }
        }

        composeTestRule.onNodeWithContentDescription(triggerStrings.exportShareCd).performClick()
        // Asserted as a pair on every click: the two actions sit side by side with
        // near-identical labels, and a crossed wiring would leave both looking
        // perfectly alive while sending the journal to the wrong destination.
        assertEquals("share must invoke onShareJournal", 1, shares)
        assertEquals("share must not invoke onSaveJournal", 0, saves)

        composeTestRule.onNodeWithContentDescription(triggerStrings.exportSaveCd).performClick()
        assertEquals("save must invoke onSaveJournal", 1, saves)
        assertEquals("save must not invoke onShareJournal again", 1, shares)
    }

    @Test
    fun `the request journal exposes both export actions and routes each to its own callback`() {
        var shares = 0
        var saves = 0
        composeTestRule.setContent {
            KnotworkTheme {
                ExternalAutomationJournalContent(
                    state = ExternalAutomationPreview.populated(),
                    callbacks = ExternalAutomationJournalCallbacks(
                        onShareJournal = { shares++ },
                        onSaveJournal = { saves++ },
                    ),
                )
            }
        }

        composeTestRule.onNodeWithContentDescription(journalStrings.exportShareCd).performClick()
        assertEquals("share must invoke onShareJournal", 1, shares)
        assertEquals("share must not invoke onSaveJournal", 0, saves)

        composeTestRule.onNodeWithContentDescription(journalStrings.exportSaveCd).performClick()
        assertEquals("save must invoke onSaveJournal", 1, saves)
        assertEquals("save must not invoke onShareJournal again", 1, shares)
    }

    @Test
    fun `both export actions meet the touch minimum`() {
        composeTestRule.setContent {
            KnotworkTheme { TriggersContent(state = TriggersPreview.populated()) }
        }

        // Asserted on the TOUCH bounds rather than the node size, as elsewhere in
        // this suite: Compose expands the touchable area around a glyph drawn
        // smaller than its target.
        listOf(triggerStrings.exportShareCd, triggerStrings.exportSaveCd).forEach { label ->
            val bounds = composeTestRule.onNodeWithContentDescription(label).fetchSemanticsNode().touchBoundsInRoot
            with(composeTestRule.density) {
                assertTrue("$label touch width was ${bounds.width.toDp()}", bounds.width.toDp().value >= MIN_TARGET_DP)
                assertTrue(
                    "$label touch height was ${bounds.height.toDp()}",
                    bounds.height.toDp().value >= MIN_TARGET_DP,
                )
            }
        }
    }

    @Test
    fun `the export actions stay reachable when the trigger list itself failed to load`() {
        composeTestRule.setContent {
            KnotworkTheme { TriggersContent(state = TriggersPreview.error()) }
        }

        // The journal is read separately from the list, so a screen that could not
        // enumerate its triggers can still hand over the journal — which is
        // precisely the file worth sending when that happens.
        composeTestRule.onNodeWithContentDescription(triggerStrings.exportShareCd).assertExists()
        composeTestRule.onNodeWithContentDescription(triggerStrings.exportSaveCd).assertExists()
    }

    private companion object {
        /** Material's minimum touch target, in dp. */
        const val MIN_TARGET_DP = 48f
    }
}
