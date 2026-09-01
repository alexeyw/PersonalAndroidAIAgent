package app.knotwork.design.components.dialogs

import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.knotwork.design.theme.KnotworkTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * Behaviour of the shared single-field dialog — the part a baseline cannot show.
 *
 * The confirm gate is the whole contract: two surfaces used to enforce
 * "non-blank, trimmed" separately, and a rule enforced twice is a rule that can
 * disagree with itself. It is asserted here on the real composable rather than
 * on an extracted predicate, because what matters is that the *button* is
 * disabled, not that a function returns false.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [36])
class SingleFieldDialogTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    private val ui = SingleFieldDialogUi(
        title = "Rename pipeline",
        label = "Name",
        initialValue = "Morning brief",
        confirmLabel = "Save",
        cancelLabel = "Cancel",
    )

    @Test
    fun `confirm is enabled while the field holds a non-blank value`() {
        setContent()

        composeTestRule.onNodeWithText("Save").assertIsEnabled()
    }

    @Test
    fun `confirm is disabled once the field is emptied`() {
        setContent()

        field().performTextClearance()

        composeTestRule.onNodeWithText("Save").assertIsNotEnabled()
    }

    @Test
    fun `confirm is disabled for whitespace alone`() {
        // The gate trims. Without that, a name of three spaces reaches storage
        // and shows up in the library as a row with no title.
        setContent()

        field().performTextClearance()
        field().performTextInput("   ")

        composeTestRule.onNodeWithText("Save").assertIsNotEnabled()
    }

    @Test
    fun `confirming hands back the value as typed`() {
        // Untrimmed on purpose: the host owns what trimming means for its field,
        // and a component that silently rewrote the value would make one of the
        // two callers wrong.
        var confirmed: String? = null
        setContent(onConfirm = { confirmed = it })

        composeTestRule.onNodeWithText("Save").performClick()

        assertEquals("Morning brief", confirmed)
    }

    /**
     * The one editable node, found by its text action rather than by a tag: a
     * tag lands on the field's decoration box, while text actions need the
     * editable node beneath it.
     */
    private fun field() = composeTestRule.onNode(hasSetTextAction())

    private fun setContent(onConfirm: (String) -> Unit = {}) {
        composeTestRule.setContent {
            KnotworkTheme(darkTheme = false) {
                SingleFieldDialog(ui = ui, onDismiss = {}, onConfirm = onConfirm)
            }
        }
    }
}
