package ai.agent.android.presentation.ui.pipeline.editor

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextReplacement
import androidx.test.platform.app.InstrumentationRegistry
import app.knotwork.design.components.pipelineeditor.CloudConfig
import app.knotwork.design.components.pipelineeditor.InputConfig
import app.knotwork.design.components.pipelineeditor.LiteRtConfig
import app.knotwork.design.components.pipelineeditor.NodeConfig
import app.knotwork.design.components.pipelineeditor.NodeConfigSheetBody
import app.knotwork.design.components.pipelineeditor.OutputConfig
import app.knotwork.design.components.pipelineeditor.ToolConfig
import org.junit.Rule
import org.junit.Test
import app.knotwork.design.R as KnotworkR

/**
 * Phase 23 / Task 7/9 — verifies the catalog `NodeConfigSheet` form
 * surfaces the per-type fields and round-trips edits + save.
 *
 * We render `NodeConfigSheetBody` (the inline content of the sheet)
 * instead of the modal `NodeConfigSheet` to keep the test off the
 * `ModalBottomSheet` animation path — bottom sheets render into their
 * own window and reading their content via `composeTestRule` requires
 * brittle wait-for-window timing.
 *
 * The host-side encode (`NodeConfigCodec.apply` → `NodeModel.configJson`)
 * is exhaustively covered by `NodeConfigCodecTest` (JVM); here we
 * verify the sheet's onSave hand-off receives the mutated payload.
 */
class PipelineEditorNodeConfigSheetTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private val ctx = InstrumentationRegistry.getInstrumentation().targetContext
    private val saveLabel = ctx.getString(KnotworkR.string.knotwork_node_config_action_save)
    private val cancelLabel = ctx.getString(KnotworkR.string.knotwork_node_config_action_cancel)

    @Test
    fun inputForm_rendersInputVariableNameField() {
        val config: NodeConfig = InputConfig(title = "Input")
        composeTestRule.setContent {
            MaterialTheme {
                NodeConfigSheetBody(
                    config = config,
                    errors = emptyMap(),
                    onChange = {},
                    onCancel = {},
                    onSave = {},
                )
            }
        }

        composeTestRule
            .onNodeWithText(ctx.getString(KnotworkR.string.knotwork_node_field_input_name))
            .assertIsDisplayed()
    }

    @Test
    fun outputForm_rendersFormatAndSystemPromptFields() {
        val config: NodeConfig = OutputConfig(title = "Output")
        composeTestRule.setContent {
            MaterialTheme {
                NodeConfigSheetBody(
                    config = config,
                    errors = emptyMap(),
                    onChange = {},
                    onCancel = {},
                    onSave = {},
                )
            }
        }

        composeTestRule
            .onNodeWithText(ctx.getString(KnotworkR.string.knotwork_node_field_format))
            .assertIsDisplayed()
        composeTestRule
            .onNodeWithText(ctx.getString(KnotworkR.string.knotwork_node_field_system_prompt))
            .assertIsDisplayed()
    }

    @Test
    fun liteRtForm_rendersSystemPromptAndTemperature() {
        val config: NodeConfig = LiteRtConfig(title = "Local LLM")
        composeTestRule.setContent {
            MaterialTheme {
                NodeConfigSheetBody(
                    config = config,
                    errors = emptyMap(),
                    onChange = {},
                    onCancel = {},
                    onSave = {},
                )
            }
        }

        composeTestRule
            .onNodeWithText(ctx.getString(KnotworkR.string.knotwork_node_field_system_prompt))
            .assertIsDisplayed()
        composeTestRule
            .onNodeWithText(ctx.getString(KnotworkR.string.knotwork_node_field_temperature))
            .assertIsDisplayed()
    }

    @Test
    fun cloudForm_rendersProviderSelector() {
        val config: NodeConfig = CloudConfig(title = "Cloud")
        composeTestRule.setContent {
            MaterialTheme {
                NodeConfigSheetBody(
                    config = config,
                    errors = emptyMap(),
                    onChange = {},
                    onCancel = {},
                    onSave = {},
                )
            }
        }

        composeTestRule
            .onNodeWithText(ctx.getString(KnotworkR.string.knotwork_node_field_provider))
            .assertIsDisplayed()
    }

    @Test
    fun editingInputName_invokesOnChangeWithMutatedConfig() {
        val initial = InputConfig(title = "Input", inputName = "user.message")
        var latest: NodeConfig? = null
        composeTestRule.setContent {
            MaterialTheme {
                NodeConfigSheetBody(
                    config = latest ?: initial,
                    errors = emptyMap(),
                    onChange = { latest = it },
                    onCancel = {},
                    onSave = {},
                )
            }
        }

        // The field is matched by its visible label — we can't replace text on the
        // label node itself, so we type into the parent's text child via the
        // label-matched node's editable subtree.
        composeTestRule
            .onNodeWithText("user.message")
            .performTextReplacement("user.email")
        composeTestRule.waitForIdle()

        val mutated = latest as? InputConfig
        assert(mutated != null) { "expected onChange to fire with an InputConfig" }
        assert(mutated?.inputName == "user.email") {
            "expected inputName=user.email, got ${mutated?.inputName}"
        }
    }

    @Test
    fun saveAction_dispatchesCurrentConfig() {
        val initial = InputConfig(title = "Input")
        var saved: NodeConfig? = null
        composeTestRule.setContent {
            MaterialTheme {
                NodeConfigSheetBody(
                    config = initial,
                    errors = emptyMap(),
                    onChange = {},
                    onCancel = {},
                    onSave = { saved = it },
                )
            }
        }

        composeTestRule.onNodeWithText(saveLabel).performClick()
        composeTestRule.waitForIdle()

        assert(saved == initial) { "expected saved=$initial, got $saved" }
    }

    @Test
    fun cancelAction_dispatchesCallback() {
        val initial = ToolConfig(title = "Tool")
        var cancelCount = 0
        composeTestRule.setContent {
            MaterialTheme {
                NodeConfigSheetBody(
                    config = initial,
                    errors = emptyMap(),
                    onChange = {},
                    onCancel = { cancelCount += 1 },
                    onSave = {},
                )
            }
        }

        composeTestRule.onNodeWithText(cancelLabel).performClick()
        composeTestRule.waitForIdle()

        assert(cancelCount == 1) { "expected onCancel once, got $cancelCount" }
    }
}
