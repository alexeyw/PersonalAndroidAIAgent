package app.knotwork.android.presentation.ui.pipeline.editor

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Rule
import org.junit.Test
import app.knotwork.design.R as KnotworkR

/**
 * Verifies the catalog `NodeConfigSheet` form
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
            // `OutlinedTextField` is stateless — its visible text is whatever
            // its parent passes as `value`. If we hoist that into a plain
            // Kotlin `var`, Compose does not observe writes and the field
            // never recomposes with the new value the user typed. The
            // internal text-input state then resyncs back to the (still
            // unchanged) parent value on the next recomposition cycle, and
            // `onValueChange` ultimately fires with the original
            // "user.message" — which is exactly the symptom this test hit
            // before. Holding the config in a `remember { mutableStateOf }`
            // makes Compose track the writes, the field stays in sync, and
            // the final onChange carries the typed "user.email".
            var current by remember { mutableStateOf<NodeConfig>(initial) }
            MaterialTheme {
                NodeConfigSheetBody(
                    config = current,
                    errors = emptyMap(),
                    onChange = {
                        current = it
                        latest = it
                    },
                    onCancel = {},
                    onSave = {},
                )
            }
        }

        // The field is matched by its visible value — `onNodeWithText`
        // resolves to the OutlinedTextField whose `EditableText` is
        // "user.message", and `performTextReplacement` fires the field's
        // `SetText` semantic action.
        composeTestRule
            .onNodeWithText("user.message")
            .performTextReplacement("user.email")
        composeTestRule.waitForIdle()

        val mutated = latest as? InputConfig
        assertNotNull("expected onChange to fire with an InputConfig", mutated)
        assertEquals("user.email", mutated?.inputName)
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

        assertEquals(initial, saved)
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

        assertEquals(1, cancelCount)
    }
}
