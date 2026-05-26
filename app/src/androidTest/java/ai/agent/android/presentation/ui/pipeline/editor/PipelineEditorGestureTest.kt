package ai.agent.android.presentation.ui.pipeline.editor

import ai.agent.android.R
import ai.agent.android.presentation.ui.orchestrator.OrchestratorUiState
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performTouchInput
import androidx.test.platform.app.InstrumentationRegistry
import io.mockk.verify
import org.junit.Rule
import org.junit.Test

/**
 * Phase 23 / Task 7/9 — the only file driving real `performTouchInput`
 * gestures against the editor.
 *
 * Drag, long-press, and tap propagation through `EditorNode` /
 * `EditorCanvas` are gesture-driven and have no equivalent deterministic
 * state API in `EditorState`. The other test files mutate the state
 * holder directly; this file verifies that the pointer plumbing
 * survives a real input stream.
 *
 * **Excluded**: real-gesture pinch / two-finger pan is intentionally
 * out of scope. The pure-Kotlin math in `CanvasTransformTest` already
 * covers zoom / pan transforms exhaustively, and
 * `MultiModalInjectionScope.pinch` is too density-sensitive to be a
 * stable regression gate — the editor's `ZoomRail` exposes the same
 * code path through the deterministic `+ / − / ⤡` buttons (covered in
 * tests upstream).
 */
class PipelineEditorGestureTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private val ctx = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun longPressOnNodeCard_entersMultiSelectMode() {
        val (vm, handles) = mockOrchestratorViewModel()
        val pipeline = PipelineEditorTestFixtures.threeNodePipeline()
        handles.uiStateFlow.value = OrchestratorUiState(currentPipeline = pipeline)

        composeTestRule.setContent {
            MaterialTheme {
                PipelineEditorScreen(viewModel = vm, onBack = {})
            }
        }
        composeTestRule.waitForIdle()

        // Long-press the middle node's label — `EditorNode.onLongPress` flips
        // `editor.multiSelectMode = true`, which causes the top toolbar to
        // swap to [MultiSelectToolbar]. The cancel button's content
        // description is the cheapest proof the swap happened.
        composeTestRule
            .onNodeWithText(pipeline.nodes[1].label)
            .performTouchInput { longClick() }
        composeTestRule.waitForIdle()

        composeTestRule
            .onNodeWithContentDescription(
                ctx.getString(R.string.pipeline_editor_multi_select_cancel),
            )
            .assertIsDisplayed()
    }

    @Test
    fun dragNodeCard_commitsMoveToViewModel() {
        val (vm, handles) = mockOrchestratorViewModel()
        val pipeline = PipelineEditorTestFixtures.threeNodePipeline()
        handles.uiStateFlow.value = OrchestratorUiState(currentPipeline = pipeline)

        composeTestRule.setContent {
            MaterialTheme {
                PipelineEditorScreen(viewModel = vm, onBack = {})
            }
        }
        composeTestRule.waitForIdle()

        // Pick up the middle node and drag it a comfortable distance so the
        // snap-to-grid quantiser commits a non-zero delta. `moveBy` after a
        // `down` produces a sustained drag gesture; `up` releases at the
        // final position, at which point `EditorNode.onDragEnd` rounds to
        // the nearest grid step and calls `viewModel.moveNode(id, dx, dy)`.
        composeTestRule
            .onNodeWithText(pipeline.nodes[1].label)
            .performTouchInput {
                down(center)
                moveBy(Offset(x = 96f, y = 0f))
                up()
            }
        composeTestRule.waitForIdle()

        verify {
            vm.moveNode(
                nodeId = pipeline.nodes[1].id,
                deltaX = match<Float> { it != 0f },
                deltaY = any(),
            )
        }
    }
}
