package app.knotwork.design.components.console

import androidx.compose.runtime.Composable
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.knotwork.design.R
import app.knotwork.design.theme.KnotworkTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * Pins what makes the console's entry point findable.
 *
 * The strip was already a `Role.Button` with a content description and was
 * still not recognised as a control — and the sentence that settled the design
 * came *after* the tester was told where to tap: it was still unclear what the
 * button was. So the strip has to carry its own **name**, and the description
 * has to speak the **state**, not just the verb. These tests fail if either
 * goes back to being implicit.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [36], qualifiers = "w360dp-h760dp-xhdpi")
class ConsoleEntryStripAffordanceTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private fun string(id: Int): String = RuntimeEnvironment.getApplication().getString(id)

    @Test
    fun `the strip says its own name`() {
        setContent { ConsoleEntryStrip(statusLine = STATUS, open = false, onClick = {}) }

        composeTestRule.onNodeWithText(string(R.string.knotwork_console_strip_label)).assertIsDisplayed()
    }

    @Test
    fun `the description speaks the live status, not only the verb`() {
        setContent { ConsoleEntryStrip(statusLine = STATUS, open = false, onClick = {}) }

        val expected = RuntimeEnvironment.getApplication()
            .getString(R.string.knotwork_console_strip_open_cd, STATUS)
        composeTestRule.onNodeWithContentDescription(expected).assertIsDisplayed()
    }

    @Test
    fun `tapping the strip asks the host to open the console`() {
        var opened = 0
        setContent { ConsoleEntryStrip(statusLine = STATUS, open = false, onClick = { opened++ }) }

        composeTestRule.onNodeWithText(string(R.string.knotwork_console_strip_label)).performClick()

        assertEquals(1, opened)
    }

    @Test
    fun `open, the same strip offers to close instead`() {
        setContent { ConsoleEntryStrip(statusLine = STATUS, open = true, onClick = {}) }

        // One element in two positions: open, it is the console sheet's header,
        // and it announces the reverse action rather than disappearing.
        composeTestRule.onNodeWithText(string(R.string.knotwork_console_strip_label)).assertIsDisplayed()
        composeTestRule.onNodeWithContentDescription(string(R.string.knotwork_console_strip_close_cd))
            .assertIsDisplayed()
    }

    private fun setContent(content: @Composable () -> Unit) {
        composeTestRule.setContent { KnotworkTheme { content() } }
    }

    private companion object {
        const val STATUS = "[NODE]  idle · ready"
    }
}
