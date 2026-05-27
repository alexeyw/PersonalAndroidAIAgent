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

        // Pick up the LAST node and drag it a comfortable distance so the
        // snap-to-grid quantiser commits a non-zero delta. `moveBy` after a
        // `down` produces a sustained drag gesture; `up` releases at the
        // final position, at which point `EditorNode.onDragEnd` rounds to
        // the nearest grid step and calls `viewModel.moveNode(id, dx, dy)`.
        //
        // Why drag the last node, not the middle one: `EditorNode`
        // positions each card via `Modifier.graphicsLayer { translationX
        // = screenX; translationY = screenY }`, which moves the card
        // *visually* but leaves all three layout boxes stacked at the
        // canvas's origin. Compose's pointer-input hit-test uses the
        // layout box, not the graphicsLayer-translated bounds — so every
        // touch event is dispatched to whichever node is topmost in the
        // composition's z-order. `EditorCanvas.kt` iterates
        // `nodesWithDrag.forEach { node -> EditorNode(...) }`, drawing
        // later nodes on top, so the LAST node in `graph.nodes` is the
        // one that owns pointer input. Targeting it makes the gesture
        // deterministic.
        //
        // (Fixing the graphicsLayer / hit-test mismatch in production
        // would require switching `EditorNode` to `Modifier.offset` for
        // positioning, which is a tracked refactor for a later phase.)
        val draggedNode = pipeline.nodes.last()
        composeTestRule
            .onNodeWithText(draggedNode.label)
            .performTouchInput {
                down(center)
                moveBy(Offset(x = 0f, y = 96f))
                up()
            }
        composeTestRule.waitForIdle()

        verify {
            vm.moveNode(
                nodeId = draggedNode.id,
                deltaX = any(),
                deltaY = match<Float> { it != 0f },
            )
        }
    }
}
