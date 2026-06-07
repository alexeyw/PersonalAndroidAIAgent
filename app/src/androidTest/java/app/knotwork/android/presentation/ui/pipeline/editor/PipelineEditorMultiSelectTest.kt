package app.knotwork.android.presentation.ui.pipeline.editor

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.platform.app.InstrumentationRegistry
import app.knotwork.android.R
import app.knotwork.android.presentation.ui.pipeline.editor.bars.MultiSelectToolbar
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

/**
 * Phase 23 / Task 7/9 — verifies the multi-select toolbar swap, count
 * label, and the three actions (Cancel / Copy / Delete).
 *
 * We render the toolbar atom directly with hand-rolled callback recorders
 * — the integration with `PipelineEditorScreen.onMultiSelectDelete`
 * (which translates the click into a chain of `vm.removeNode(id)` calls
 * plus state cleanup) is covered separately in
 * [PipelineEditorCopyPasteTest].
 */
class PipelineEditorMultiSelectTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private val ctx = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun toolbar_rendersPluralCount() {
        composeTestRule.setContent {
            MaterialTheme {
                MultiSelectToolbar(
                    count = 2,
                    onCancel = {},
                    onCopy = {},
                    onDelete = {},
                )
            }
        }

        composeTestRule.onNodeWithText("2 selected").assertIsDisplayed()
    }

    @Test
    fun toolbar_cancelButton_invokesCallback() {
        var cancelCount = 0
        composeTestRule.setContent {
            MaterialTheme {
                MultiSelectToolbar(
                    count = 1,
                    onCancel = { cancelCount += 1 },
                    onCopy = {},
                    onDelete = {},
                )
            }
        }

        composeTestRule
            .onNodeWithContentDescription(ctx.getString(R.string.pipeline_editor_multi_select_cancel))
            .performClick()
        composeTestRule.waitForIdle()

        assertEquals(1, cancelCount)
    }

    @Test
    fun toolbar_copyButton_invokesCallback() {
        var copyCount = 0
        composeTestRule.setContent {
            MaterialTheme {
                MultiSelectToolbar(
                    count = 3,
                    onCancel = {},
                    onCopy = { copyCount += 1 },
                    onDelete = {},
                )
            }
        }

        composeTestRule
            .onNodeWithContentDescription(ctx.getString(R.string.pipeline_editor_multi_select_copy))
            .performClick()
        composeTestRule.waitForIdle()

        assertEquals(1, copyCount)
    }

    @Test
    fun toolbar_deleteButton_invokesCallback() {
        var deleteCount = 0
        composeTestRule.setContent {
            MaterialTheme {
                MultiSelectToolbar(
                    count = 5,
                    onCancel = {},
                    onCopy = {},
                    onDelete = { deleteCount += 1 },
                )
            }
        }

        composeTestRule
            .onNodeWithContentDescription(ctx.getString(R.string.pipeline_editor_multi_select_delete))
            .performClick()
        composeTestRule.waitForIdle()

        assertEquals(1, deleteCount)
    }
}
