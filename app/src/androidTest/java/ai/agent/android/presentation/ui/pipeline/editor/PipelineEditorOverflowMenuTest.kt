package ai.agent.android.presentation.ui.pipeline.editor

import ai.agent.android.R
import ai.agent.android.presentation.ui.orchestrator.OrchestratorUiState
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.platform.app.InstrumentationRegistry
import io.mockk.verify
import org.junit.Rule
import org.junit.Test
import app.knotwork.design.R as KnotworkR

/**
 * Phase 23 / Task 7/9 — verifies the overflow `DropdownMenu` wiring on
 * [PipelineEditorScreen]. The menu items + click handlers live in
 * `PipelineEditorScreen` (not the catalog atom), so a fast Compose test
 * is enough — no Hilt graph required. We mock the VM with deterministic
 * StateFlows via [mockOrchestratorViewModel] and assert the actions
 * reach the right VM seam.
 */
class PipelineEditorOverflowMenuTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private val ctx = InstrumentationRegistry.getInstrumentation().targetContext
    private val overflowCd = ctx.getString(KnotworkR.string.knotwork_editor_action_overflow)

    @Test
    fun overflowMenu_opens_andContainsCanonicalItems() {
        val (vm, handles) = mockOrchestratorViewModel()
        handles.uiStateFlow.value = OrchestratorUiState(
            currentPipeline = PipelineEditorTestFixtures.inputOutputPipeline(),
        )

        composeTestRule.setContent {
            MaterialTheme {
                PipelineEditorScreen(viewModel = vm, onBack = {})
            }
        }

        composeTestRule.onNodeWithContentDescription(overflowCd).performClick()

        listOf(
            R.string.pipeline_editor_overflow_save,
            R.string.pipeline_editor_overflow_undo,
            R.string.pipeline_editor_overflow_redo,
            R.string.pipeline_editor_overflow_rename,
            R.string.pipeline_editor_overflow_delete,
            R.string.pipeline_editor_overflow_auto_layout,
            R.string.pipeline_editor_overflow_mini_map,
            R.string.pipeline_editor_overflow_find,
            R.string.pipeline_editor_overflow_paste,
        ).forEach { res ->
            composeTestRule.onNodeWithText(ctx.getString(res)).assertIsDisplayed()
        }
    }

    @Test
    fun overflowMenu_save_invokesViewModel() {
        val (vm, handles) = mockOrchestratorViewModel()
        handles.uiStateFlow.value = OrchestratorUiState(
            currentPipeline = PipelineEditorTestFixtures.inputOutputPipeline(),
        )

        composeTestRule.setContent {
            MaterialTheme {
                PipelineEditorScreen(viewModel = vm, onBack = {})
            }
        }

        composeTestRule.onNodeWithContentDescription(overflowCd).performClick()
        composeTestRule
            .onNodeWithText(ctx.getString(R.string.pipeline_editor_overflow_save))
            .performClick()
        composeTestRule.waitForIdle()

        verify(exactly = 1) { vm.saveCurrentPipeline() }
    }

    @Test
    fun overflowMenu_findNode_opensFilterBar() {
        val (vm, handles) = mockOrchestratorViewModel()
        handles.uiStateFlow.value = OrchestratorUiState(
            currentPipeline = PipelineEditorTestFixtures.threeNodePipeline(),
        )

        composeTestRule.setContent {
            MaterialTheme {
                PipelineEditorScreen(viewModel = vm, onBack = {})
            }
        }

        composeTestRule.onNodeWithContentDescription(overflowCd).performClick()
        composeTestRule
            .onNodeWithText(ctx.getString(R.string.pipeline_editor_overflow_find))
            .performClick()
        composeTestRule.waitForIdle()

        // The FilterBar's placeholder doubles as its contentDescription so a
        // testTag-less assertion is possible.
        composeTestRule
            .onNodeWithContentDescription(
                ctx.getString(R.string.pipeline_editor_search_placeholder),
            )
            .assertIsDisplayed()
    }

    @Test
    fun overflowMenu_gridToggle_flipsLabel() {
        val (vm, handles) = mockOrchestratorViewModel()
        handles.uiStateFlow.value = OrchestratorUiState(
            currentPipeline = PipelineEditorTestFixtures.threeNodePipeline(),
        )

        composeTestRule.setContent {
            MaterialTheme {
                PipelineEditorScreen(viewModel = vm, onBack = {})
            }
        }

        // Grid defaults to on → menu item reads "Hide grid".
        composeTestRule.onNodeWithContentDescription(overflowCd).performClick()
        composeTestRule
            .onNodeWithText(ctx.getString(R.string.pipeline_editor_overflow_toggle_grid_hide))
            .assertIsDisplayed()
            .performClick()
        composeTestRule.waitForIdle()

        // Re-open → label has flipped to "Show grid".
        composeTestRule.onNodeWithContentDescription(overflowCd).performClick()
        composeTestRule
            .onNodeWithText(ctx.getString(R.string.pipeline_editor_overflow_toggle_grid_show))
            .assertIsDisplayed()
    }

    @Test
    fun overflowMenu_paste_onEmptyClipboard_doesNotInvokeAddNode() {
        val (vm, handles) = mockOrchestratorViewModel()
        handles.uiStateFlow.value = OrchestratorUiState(
            currentPipeline = PipelineEditorTestFixtures.inputOutputPipeline(),
        )

        composeTestRule.setContent {
            MaterialTheme {
                PipelineEditorScreen(viewModel = vm, onBack = {})
            }
        }

        composeTestRule.onNodeWithContentDescription(overflowCd).performClick()
        // Paste is rendered but disabled when clipboard is empty. Clicking a
        // disabled menu item is a no-op; assert no addNode was dispatched.
        composeTestRule
            .onNodeWithText(ctx.getString(R.string.pipeline_editor_overflow_paste))
            .performClick()
        composeTestRule.waitForIdle()

        verify(exactly = 0) { vm.addNode(any(), any(), any()) }
    }
}
