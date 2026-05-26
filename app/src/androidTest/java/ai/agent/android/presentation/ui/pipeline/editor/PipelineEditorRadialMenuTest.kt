package ai.agent.android.presentation.ui.pipeline.editor

import ai.agent.android.R
import ai.agent.android.domain.models.NodeType
import ai.agent.android.presentation.ui.pipeline.editor.canvas.QuickAddRadialMenu
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

/**
 * Phase 23 / Task 7/9 — verifies the quick-add radial menu surfaces
 * every catalog `NodeType` and dispatches the domain pick on tap.
 *
 * We render `QuickAddRadialMenu` directly with hand-rolled callbacks
 * rather than the screen — the menu's only inputs are anchor coordinates
 * and a dispatcher, and rendering through the screen would add
 * transform-projection plumbing that obscures the per-tile assertions.
 */
class PipelineEditorRadialMenuTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private val ctx = InstrumentationRegistry.getInstrumentation().targetContext

    /**
     * Twelve short labels paired with the matching domain [NodeType]. The
     * source of truth lives in `QuickAddRadialMenu.quickAddShortLabel`
     * (private) — duplicated here so a label rename breaks the test loudly.
     */
    private val expectedLabels: List<Pair<String, NodeType>> = listOf(
        "Input" to NodeType.INPUT,
        "Output" to NodeType.OUTPUT,
        "Local LLM" to NodeType.LITE_RT,
        "Cloud" to NodeType.CLOUD,
        "Router" to NodeType.INTENT_ROUTER,
        "If" to NodeType.IF_CONDITION,
        "Ask user" to NodeType.CLARIFICATION,
        "Tool" to NodeType.TOOL,
        "Decompose" to NodeType.DECOMPOSITION,
        "Queue" to NodeType.QUEUE_PROCESSOR,
        "Evaluate" to NodeType.EVALUATION,
        "Summary" to NodeType.SUMMARY,
    )

    @Test
    fun radialMenu_rendersAllTwelveLabels() {
        composeTestRule.setContent {
            MaterialTheme {
                QuickAddRadialMenu(
                    screenAnchorX = 400f,
                    screenAnchorY = 400f,
                    onPick = {},
                    onDismiss = {},
                )
            }
        }

        expectedLabels.forEach { (label, _) ->
            composeTestRule.onNodeWithText(label).assertIsDisplayed()
        }
    }

    @Test
    fun radialMenu_tapOnTile_dispatchesDomainNodeType() {
        var picked: NodeType? = null
        composeTestRule.setContent {
            MaterialTheme {
                QuickAddRadialMenu(
                    screenAnchorX = 400f,
                    screenAnchorY = 400f,
                    onPick = { picked = it },
                    onDismiss = {},
                )
            }
        }

        composeTestRule.onNodeWithText("Cloud").performClick()
        composeTestRule.waitForIdle()

        assertEquals(NodeType.CLOUD, picked)
    }

    @Test
    fun radialMenu_closeIcon_invokesDismiss() {
        var dismissCount = 0
        composeTestRule.setContent {
            MaterialTheme {
                QuickAddRadialMenu(
                    screenAnchorX = 400f,
                    screenAnchorY = 400f,
                    onPick = {},
                    onDismiss = { dismissCount += 1 },
                )
            }
        }

        composeTestRule
            .onNodeWithContentDescription(ctx.getString(R.string.pipeline_editor_quick_add_close))
            .performClick()
        composeTestRule.waitForIdle()

        assertEquals(1, dismissCount)
    }
}
