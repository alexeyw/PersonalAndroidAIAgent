package app.knotwork.android.presentation.ui.pipeline.editor

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.IntSize
import androidx.test.platform.app.InstrumentationRegistry
import app.knotwork.android.R
import app.knotwork.android.presentation.ui.pipeline.editor.canvas.MiniMap
import app.knotwork.android.presentation.ui.pipeline.editor.core.CanvasTransform
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

/**
 * Verifies the mini-map overlay renders the
 * canonical OVERVIEW header (including the scale percent) and the close
 * button dispatches `onClose`.
 *
 * The grid toggle is covered indirectly by
 * [PipelineEditorOverflowMenuTest.overflowMenu_gridToggle_flipsLabel];
 * `DotGridBackground` itself is a pure-Canvas composable with no
 * text/semantics surface, so a Compose-test assertion adds no value
 * beyond the overflow-label flip already covered.
 */
class PipelineEditorMiniMapAndGridTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private val ctx = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun miniMap_rendersHeaderAndScalePercent() {
        val graph = PipelineEditorTestFixtures.threeNodePipeline()
        composeTestRule.setContent {
            MaterialTheme {
                MiniMap(
                    graph = graph,
                    transform = CanvasTransform(scale = 0.5f),
                    viewportSize = IntSize(1080, 1920),
                    onTapCanvasPoint = { _, _ -> },
                    onClose = {},
                )
            }
        }

        composeTestRule
            .onNodeWithText(ctx.getString(R.string.pipeline_editor_mini_map_title))
            .assertIsDisplayed()
        // The 0.5× transform renders as "0.50×" via `formatScalePercent`.
        composeTestRule.onNodeWithText("0.50×").assertIsDisplayed()
    }

    @Test
    fun miniMap_closeButton_invokesCallback() {
        val graph = PipelineEditorTestFixtures.threeNodePipeline()
        var closeCount = 0
        composeTestRule.setContent {
            MaterialTheme {
                MiniMap(
                    graph = graph,
                    transform = CanvasTransform(),
                    viewportSize = IntSize(1080, 1920),
                    onTapCanvasPoint = { _, _ -> },
                    onClose = { closeCount += 1 },
                )
            }
        }

        composeTestRule
            .onNodeWithContentDescription(ctx.getString(R.string.pipeline_editor_mini_map_close))
            .performClick()
        composeTestRule.waitForIdle()

        assertEquals(1, closeCount)
    }
}
