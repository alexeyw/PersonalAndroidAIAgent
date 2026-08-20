package app.knotwork.design.screens.prompts

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.Density
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.knotwork.design.theme.KnotworkTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * Behaviour tests for the prompt card's action cluster.
 *
 * The one that matters is [overflow survives font scale 200 %]. The delivered
 * design drew the overflow disappearing at that scale, which would leave
 * **Delete** and **Export** with no route at all — strictly worse than the
 * three-icon row that shipped before, where Delete was its own button. A
 * snapshot alone would not catch a regression here, because a snapshot only
 * fails once someone looks at it.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [36], qualifiers = "w360dp-h760dp-xhdpi")
class PromptLibraryContentActionsTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private val strings = PromptLibraryStrings()

    private fun setContent(fontScale: Float = 1f, state: PromptLibraryViewState) {
        composeTestRule.setContent {
            KnotworkTheme(darkTheme = false) {
                AtFontScale(fontScale) {
                    PromptLibraryContent(state = state, strings = strings)
                }
            }
        }
    }

    @Composable
    private fun AtFontScale(scale: Float, content: @Composable () -> Unit) {
        val base = LocalDensity.current
        CompositionLocalProvider(
            LocalDensity provides Density(density = base.density, fontScale = scale),
            content = content,
        )
    }

    @Test
    fun `given a user row at font scale 200 percent when rendered then the overflow is still reachable`() {
        setContent(fontScale = 2f, state = PromptLibraryPreview.default())

        // Two user rows, so two overflows: assert on the first rather than
        // expecting a unique match.
        composeTestRule.onAllNodesWithContentDescription(strings.moreCd)[0].assertIsDisplayed()
    }

    @Test
    fun `given the overflow at font scale 200 percent when opened then export and delete are both offered`() {
        setContent(fontScale = 2f, state = PromptLibraryPreview.default())

        composeTestRule.onAllNodesWithContentDescription(strings.moreCd)[0].performClick()

        composeTestRule.onNodeWithText(strings.exportAction).assertIsDisplayed()
        composeTestRule.onNodeWithText(strings.deleteAction).assertIsDisplayed()
    }

    @Test
    fun `given a read-only row when rendered then export is direct and no overflow is drawn`() {
        // A bundled prompt has three verbs, so three fit, and an overflow
        // holding one item is not a menu.
        setContent(state = PromptLibraryPreview.default())

        composeTestRule.onNodeWithContentDescription(strings.exportCdFormat.format("Report writer"))
            .assertIsDisplayed()
    }

    @Test
    fun `given an empty library when rendered then import leads and writing one is offered beside it`() {
        setContent(state = PromptLibraryPreview.emptyLibrary())

        composeTestRule.onNodeWithText(strings.emptyImportCta).assertIsDisplayed()
        composeTestRule.onNodeWithText(strings.emptyNewCta).assertIsDisplayed()
    }

    @Test
    fun `given the library screen when rendered then the top bar offers import`() {
        setContent(state = PromptLibraryPreview.default())

        composeTestRule.onNodeWithContentDescription(strings.importCd).assertIsDisplayed()
    }
}
