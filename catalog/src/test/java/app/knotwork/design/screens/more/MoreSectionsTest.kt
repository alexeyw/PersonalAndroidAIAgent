package app.knotwork.design.screens.more

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.knotwork.design.theme.KnotworkTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * Pins the structure of the More tab.
 *
 * The screen is twelve navigation rows, and flat it made the row a closed-test
 * user was hunting for take two hours to find. Sections are labels, not
 * screens — nothing is navigated to differently — so what these tests protect
 * is the *order*: Triggers first, App last, and no row lost in the regrouping.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [36], qualifiers = "w360dp-h760dp-xhdpi")
class MoreSectionsTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun `the first row of the first section is Triggers`() {
        val state = MorePreview.sections()

        assertEquals("automation", state.sections.first().id)
        assertEquals("triggers", state.sections.first().rows.first().id)
    }

    @Test
    fun `App is last, and Live metrics lives in it rather than heading a section of one`() {
        val state = MorePreview.sections()
        val app = state.sections.last()

        assertEquals("app", app.id)
        assertEquals(listOf("settings", "metrics", "about"), app.rows.map { it.id })
    }

    @Test
    fun `every row survives the regrouping, three to a section`() {
        val state = MorePreview.sections()
        val ids = state.sections.flatMap { section -> section.rows.map { it.id } }

        assertEquals(EXPECTED_ROWS.toSet(), ids.toSet())
        assertEquals("no row is listed twice", EXPECTED_ROWS.size, ids.size)
        state.sections.forEach { section ->
            assertEquals("section ${section.id} holds three rows", 3, section.rows.size)
        }
    }

    @Test
    fun `only Tasks carries a badge, and a stored count goes in the subtitle`() {
        val state = MorePreview.sections()
        val badged = state.sections.flatMap { it.rows }.filter { it.badge > 0 }

        // A badge means "something is happening now"; being the only one is
        // what keeps that meaning. Archive at zero would otherwise read as
        // broken rather than empty.
        assertEquals(listOf("tasks"), badged.map { it.id })
        val archive = state.sections.flatMap { it.rows }.single { it.id == "archive" }
        assertEquals(0, archive.badge)
        assertEquals("0 archived chats", archive.subtitle)
    }

    @Test
    fun `the section headings render above their rows`() {
        composeTestRule.setContent {
            KnotworkTheme { MoreContent(state = MorePreview.sections()) }
        }

        composeTestRule.onNodeWithText("AUTOMATION").assertIsDisplayed()
        composeTestRule.onNodeWithText("Triggers").assertIsDisplayed()
    }

    private companion object {
        val EXPECTED_ROWS = listOf(
            "triggers", "library", "tasks",
            "memory", "files", "archive",
            "prompts", "skills", "models",
            "settings", "metrics", "about",
        )
    }
}
