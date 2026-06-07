package app.knotwork.android.presentation.ui.pipeline.editor

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.test.platform.app.InstrumentationRegistry
import app.knotwork.android.presentation.ui.orchestrator.OrchestratorUiState
import app.knotwork.android.presentation.ui.orchestrator.PipelineRunState
import app.knotwork.design.a11y.FixedKnotworkA11y
import app.knotwork.design.a11y.LocalKnotworkA11y
import org.junit.Rule
import org.junit.Test
import app.knotwork.design.R as KnotworkR

/**
 * Phase 23 / Task 7/9 — verifies the run-state plumbing on
 * [PipelineEditorScreen]: when the ViewModel emits a Running snapshot,
 * the run-status banner appears, the toolbar primary action is hidden,
 * and the subtitle reads `Running · <node label>`.
 */
class PipelineEditorRunStateTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private val ctx = InstrumentationRegistry.getInstrumentation().targetContext
    private val runLabel = ctx.getString(KnotworkR.string.knotwork_editor_action_run)
    private val runningBadge =
        ctx.getString(KnotworkR.string.knotwork_run_banner_label_running)

    @Test
    fun idleRunState_hidesBanner_andRunButtonVisible() {
        val (vm, handles) = mockOrchestratorViewModel()
        handles.uiStateFlow.value = OrchestratorUiState(
            currentPipeline = PipelineEditorTestFixtures.inputOutputPipeline(),
        )

        composeTestRule.setContent {
            MaterialTheme {
                PipelineEditorScreen(viewModel = vm, onBack = {})
            }
        }

        composeTestRule.onNodeWithText(runLabel).assertIsDisplayed()
        composeTestRule.onAllNodesWithText(runningBadge).assertCountEquals(0)
    }

    @Test
    fun runningRunState_showsBanner_andHidesRunButton() {
        val (vm, handles) = mockOrchestratorViewModel()
        val pipeline = PipelineEditorTestFixtures.threeNodePipeline()
        handles.uiStateFlow.value = OrchestratorUiState(currentPipeline = pipeline)

        // Pin `reducedMotion = true` for the duration of the test so the
        // active-node pulse animation (NodeCard.kt rememberInfiniteTransition,
        // gated by KnotworkTheme.a11y.reducedMotion()) does not keep the
        // Compose recomposer perpetually busy. Without this, the first
        // `waitForIdle()` after flipping the run state into Running times
        // out with "ComposeIdlingResource is busy due to pending
        // recompositions" because the infinite transition never settles.
        composeTestRule.setContent {
            CompositionLocalProvider(LocalKnotworkA11y provides FixedKnotworkA11y(reducedMotion = true)) {
                MaterialTheme {
                    PipelineEditorScreen(viewModel = vm, onBack = {})
                }
            }
        }

        // Sanity baseline.
        composeTestRule.onNodeWithText(runLabel).assertIsDisplayed()

        // Flip to running on the middle node.
        handles.runStateFlow.value = PipelineRunState(
            isRunning = true,
            activeNodeId = pipeline.nodes[1].id,
        )
        composeTestRule.waitForIdle()

        // Banner badge is uppercased "RUNNING".
        composeTestRule.onNodeWithText(runningBadge).assertIsDisplayed()
        // Toolbar primary action is `None` while running — the `Run` text is gone.
        composeTestRule.onAllNodesWithText(runLabel).assertCountEquals(0)
    }
}
