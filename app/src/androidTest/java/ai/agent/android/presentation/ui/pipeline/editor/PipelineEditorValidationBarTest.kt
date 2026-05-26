package ai.agent.android.presentation.ui.pipeline.editor

import ai.agent.android.R
import ai.agent.android.domain.models.PipelineValidationError
import ai.agent.android.presentation.ui.pipeline.editor.bars.ValidationBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

/**
 * Phase 23 / Task 7/9 — verifies the validation bar header banner,
 * per-error rows, the Auto-fix action, and the per-row `Go` deep-link.
 *
 * We render `ValidationBar` directly with a tiny fixture graph so the
 * error labels we pass in show up verbatim in the assertions.
 */
class PipelineEditorValidationBarTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private val ctx = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun emptyErrors_rendersCleanLabel() {
        val graph = PipelineEditorTestFixtures.inputOutputPipeline()
        composeTestRule.setContent {
            MaterialTheme {
                ValidationBar(
                    graph = graph,
                    errors = emptyList(),
                    errorLabels = emptyList(),
                    nodeLookup = { id -> graph.nodes.find { it.id == id }?.label },
                    onFocusNode = {},
                    onAutoFix = {},
                )
            }
        }

        composeTestRule
            .onNodeWithText(ctx.getString(R.string.pipeline_editor_validation_clean))
            .assertIsDisplayed()
    }

    @Test
    fun nonEmptyErrors_rendersHeaderAndRows() {
        val graph = PipelineEditorTestFixtures.validationInvalidPipeline()
        val errors = listOf(
            PipelineValidationError.MissingOutput,
            PipelineValidationError.MultipleInputs,
        )
        composeTestRule.setContent {
            MaterialTheme {
                ValidationBar(
                    graph = graph,
                    errors = errors,
                    errorLabels = listOf("Missing OUTPUT node", "Multiple INPUT nodes"),
                    nodeLookup = { id -> graph.nodes.find { it.id == id }?.label },
                    onFocusNode = {},
                    onAutoFix = {},
                )
            }
        }

        composeTestRule.onNodeWithText("Missing OUTPUT node").assertIsDisplayed()
        composeTestRule.onNodeWithText("Multiple INPUT nodes").assertIsDisplayed()
        // Header plural pulls from `pipeline_editor_validation_count` — 2 errors.
        composeTestRule
            .onNodeWithText("2 issues — can't run")
            .assertIsDisplayed()
    }

    @Test
    fun autoFix_action_invokesCallback_whenAutoFixable() {
        val graph = PipelineEditorTestFixtures.validationInvalidPipeline()
        val errors = listOf(PipelineValidationError.MissingOutput)
        var autoFixCount = 0
        composeTestRule.setContent {
            MaterialTheme {
                ValidationBar(
                    graph = graph,
                    errors = errors,
                    errorLabels = listOf("Missing OUTPUT node"),
                    nodeLookup = { id -> graph.nodes.find { it.id == id }?.label },
                    onFocusNode = {},
                    onAutoFix = { autoFixCount += 1 },
                )
            }
        }

        composeTestRule
            .onNodeWithText(ctx.getString(R.string.pipeline_editor_validation_auto_fix))
            .performClick()
        composeTestRule.waitForIdle()

        assertEquals(1, autoFixCount)
    }

    @Test
    fun goButton_invokesOnFocusNode_whenErrorResolvesNodeId() {
        // DisconnectedInput is a per-node warning; `PipelineValidationError.focusableNodeId`
        // resolves it to the unconnected INPUT node, so the Go button is enabled.
        val graph = PipelineEditorTestFixtures.validationInvalidPipeline()
        val unconnectedInputId = graph.nodes.first().id
        val errors = listOf(PipelineValidationError.DisconnectedInput)
        var focused: String? = null
        composeTestRule.setContent {
            MaterialTheme {
                ValidationBar(
                    graph = graph,
                    errors = errors,
                    errorLabels = listOf("Disconnected input"),
                    nodeLookup = { id -> graph.nodes.find { it.id == id }?.label },
                    onFocusNode = { focused = it },
                    onAutoFix = {},
                )
            }
        }

        // Per-row "Go" labels can repeat once per error row; tap the first match.
        composeTestRule
            .onAllNodesWithText(ctx.getString(R.string.pipeline_editor_validation_go))[0]
            .performClick()
        composeTestRule.waitForIdle()

        assertEquals(unconnectedInputId, focused)
    }
}
