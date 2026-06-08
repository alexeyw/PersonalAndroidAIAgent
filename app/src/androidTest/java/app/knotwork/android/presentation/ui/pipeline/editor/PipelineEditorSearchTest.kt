package app.knotwork.android.presentation.ui.pipeline.editor

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextReplacement
import androidx.test.platform.app.InstrumentationRegistry
import app.knotwork.android.R
import app.knotwork.android.presentation.ui.pipeline.editor.canvas.FilterBar
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

/**
 * Covers the search bar that overlays the canvas
 * when the user picks "Find node…" from the overflow menu. The bar's
 * inputs and submit/close callbacks are deterministic; we drive them
 * directly without going through the full screen so the test stays
 * focused on the bar's contract.
 */
class PipelineEditorSearchTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private val ctx = InstrumentationRegistry.getInstrumentation().targetContext
    private val placeholder = ctx.getString(R.string.pipeline_editor_search_placeholder)
    private val closeCd = ctx.getString(R.string.pipeline_editor_search_close)

    @Test
    fun filterBar_rendersPlaceholderField() {
        composeTestRule.setContent {
            MaterialTheme {
                FilterBar(
                    query = "",
                    matchCount = 0,
                    onQueryChange = {},
                    onSubmit = {},
                    onClose = {},
                )
            }
        }

        composeTestRule
            .onNodeWithContentDescription(placeholder)
            .assertIsDisplayed()
    }

    @Test
    fun typingInField_dispatchesOnQueryChange() {
        var latest: String? = null
        composeTestRule.setContent {
            MaterialTheme {
                FilterBar(
                    query = latest.orEmpty(),
                    matchCount = 0,
                    onQueryChange = { latest = it },
                    onSubmit = {},
                    onClose = {},
                )
            }
        }

        // KnotworkTextField applies `contentDescription` on the outer
        // wrapper Box, while the editable `BasicTextField` is a child
        // semantics node carrying the `SetText` / `RequestFocus` actions
        // — `performTextReplacement` requires the latter, so we target
        // the unique text-input node directly. There is only one
        // text field in `FilterBar`, so the matcher is unambiguous.
        composeTestRule
            .onNode(matcher = hasSetTextAction())
            .performTextReplacement("router")
        composeTestRule.waitForIdle()

        assertEquals("router", latest)
    }

    @Test
    fun nonEmptyQuery_rendersMatchCountPill() {
        composeTestRule.setContent {
            MaterialTheme {
                FilterBar(
                    query = "router",
                    matchCount = 1,
                    onQueryChange = {},
                    onSubmit = {},
                    onClose = {},
                )
            }
        }

        composeTestRule.onNodeWithText("1 match").assertIsDisplayed()
    }

    @Test
    fun closeButton_dispatchesOnClose() {
        var closeCount = 0
        composeTestRule.setContent {
            MaterialTheme {
                FilterBar(
                    query = "router",
                    matchCount = 1,
                    onQueryChange = {},
                    onSubmit = {},
                    onClose = { closeCount += 1 },
                )
            }
        }

        composeTestRule.onNodeWithContentDescription(closeCd).performClick()
        composeTestRule.waitForIdle()

        assertEquals(1, closeCount)
    }
}
