package ai.agent.android.presentation.ui.chat.home

import ai.agent.android.R
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.platform.app.InstrumentationRegistry
import app.knotwork.design.components.console.ConsoleFilter
import app.knotwork.design.components.console.ConsoleSnap
import app.knotwork.design.components.console.ConsoleTab
import app.knotwork.design.screens.chat.ChatHomeThreadRow
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Rule
import org.junit.Test
import app.knotwork.design.R as KnotworkR

/**
 * Phase 22 / Task 4 — verifies the TopAppBar overflow menu wiring. The
 * menu is owned by [ChatHomeScreen] (not the catalog), so a fast Compose
 * test is enough — no Hilt graph required. The test pre-mocks the VM with
 * deterministic StateFlows, taps the `⋮` icon to open the menu, taps
 * `Delete chat`, confirms the destructive `AlertDialog`, and verifies
 * `viewModel.deleteCurrentSession()` was called exactly once.
 */
class ChatHomeOverflowMenuTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    /**
     * Builds a relaxed [ChatHomeViewModel] mock with every observed flow
     * stubbed to a stable initial value. The mock is `relaxed = true` so
     * methods we don't exercise simply no-op.
     */
    @Suppress("LongMethod") // Single source of truth for every flow ChatHomeScreen collects.
    private fun mockViewModel(): ChatHomeViewModel {
        val vm = mockk<ChatHomeViewModel>(relaxed = true)
        every { vm.state } returns MutableStateFlow(ChatHomeUiState.Idle)
        every { vm.threadTitle } returns MutableStateFlow("Test chat")
        every { vm.modelName } returns MutableStateFlow("Test model")
        every { vm.composerValue } returns MutableStateFlow("")
        every { vm.pendingTypedConfirm } returns MutableStateFlow("")
        every { vm.messages } returns MutableStateFlow(emptyList())
        every { vm.consoleSearchQuery } returns MutableStateFlow<String?>(null)
        every { vm.consoleFilter } returns MutableStateFlow(ConsoleFilter.allOn)
        every { vm.pipelineName } returns MutableStateFlow<String?>("default")
        every { vm.tokensUsed } returns MutableStateFlow(0)
        every { vm.tokensMax } returns MutableStateFlow(0)
        every { vm.pendingTool } returns MutableStateFlow<HitlPending?>(null)
        every { vm.pendingClarification } returns MutableStateFlow(null)
        every { vm.consoleLines } returns MutableStateFlow(emptyList())
        every { vm.consoleVars } returns MutableStateFlow(emptyList())
        every { vm.consoleTraces } returns MutableStateFlow(emptyList())
        every { vm.consoleTab } returns MutableStateFlow(ConsoleTab.Logs)
        every { vm.consoleSnap } returns MutableStateFlow<ConsoleSnap?>(null)
        every { vm.consoleClearConfirmRequested } returns MutableStateFlow(false)
        every { vm.favorite } returns MutableStateFlow(false)
        every { vm.threadRows } returns MutableStateFlow(
            listOf(
                ChatHomeThreadRow(
                    id = "active-id",
                    title = "Test chat",
                    subtitle = "Now",
                    selected = true,
                    active = true,
                ),
            ),
        )
        every { vm.installedModels } returns MutableStateFlow(emptyList())
        every { vm.activeModelId } returns MutableStateFlow(null)
        every { vm.availablePipelinesFlow } returns MutableStateFlow(emptyList())
        every { vm.pipelineFallbackEvents } returns MutableSharedFlow()
        every { vm.consoleSnackbarEvents } returns MutableSharedFlow()
        every { vm.exportEvents } returns MutableSharedFlow()
        every { vm.importErrorEvents } returns MutableSharedFlow()
        every { vm.currentPipelineId() } returns null
        return vm
    }

    @Test
    fun deleteFromOverflow_removesSession() {
        val ctx = InstrumentationRegistry.getInstrumentation().targetContext
        val overflowCd = ctx.getString(KnotworkR.string.knotwork_chat_home_action_overflow)
        val deleteItem = ctx.getString(R.string.chat_overflow_delete)
        val confirmLabel = ctx.getString(R.string.chat_delete_dialog_confirm)
        val viewModel = mockViewModel()

        composeTestRule.setContent {
            MaterialTheme {
                ChatHomeScreen(viewModel = viewModel)
            }
        }

        // Open overflow.
        composeTestRule.onNodeWithContentDescription(overflowCd).performClick()
        composeTestRule.onNodeWithText(deleteItem).assertIsDisplayed()
        composeTestRule.onNodeWithText(deleteItem).performClick()
        // Confirm destructive dialog.
        composeTestRule.onNodeWithText(confirmLabel).assertIsDisplayed()
        composeTestRule.onNodeWithText(confirmLabel).performClick()
        composeTestRule.waitForIdle()

        verify(exactly = 1) { viewModel.deleteCurrentSession() }
    }
}
