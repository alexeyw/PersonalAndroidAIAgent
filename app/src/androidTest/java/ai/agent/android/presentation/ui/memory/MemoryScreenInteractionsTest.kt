package ai.agent.android.presentation.ui.memory

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.platform.app.InstrumentationRegistry
import io.mockk.verify
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import app.knotwork.design.R as KnotworkR

/**
 * Phase 23 / Task 8 — covers row-level interactions on the Memory
 * detail overlay: pin toggle, edit commit (which forces re-embedding
 * via [MemoryViewModel.editVectorMemory]), and delete. Each scenario
 * mounts the screen with one or two seeded chunks, opens the detail
 * overlay by tapping the row title, then verifies the ViewModel
 * receives the expected call.
 */
class MemoryScreenInteractionsTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun rowPinAction_invokesTogglePinned() {
        val (vm, _) = mockMemoryViewModel(
            initialUiState = MemoryUiState(
                vectorMemories = listOf(
                    sampleMemoryChunk(id = ROW_ID, text = "Pin me later\nLong-form description"),
                ),
            ),
        )

        composeTestRule.setContent {
            MaterialTheme { MemoryScreen(viewModel = vm) }
        }

        // Open the detail overlay for the only row. The row title is the
        // first line of the chunk text (`MemoryScreen.toMemoryRow`), so
        // matching on "Pin me later" hits a unique node.
        composeTestRule.onNodeWithText(text = "Pin me later").performClick()

        val ctx = InstrumentationRegistry.getInstrumentation().targetContext
        val pinLabel = ctx.getString(KnotworkR.string.knotwork_memory_detail_pin)
        composeTestRule.onNodeWithText(text = pinLabel).performClick()

        verify(exactly = 1) { vm.togglePinned(id = ROW_ID) }
    }

    @Test
    fun editCommit_invokesEditVectorMemory_whichReEmbeds() {
        val (vm, _) = mockMemoryViewModel(
            initialUiState = MemoryUiState(
                vectorMemories = listOf(
                    sampleMemoryChunk(id = ROW_ID, text = "Editable title\nDetailed body content"),
                ),
            ),
        )

        composeTestRule.setContent {
            MaterialTheme { MemoryScreen(viewModel = vm) }
        }

        val ctx = InstrumentationRegistry.getInstrumentation().targetContext
        val editLabel = ctx.getString(KnotworkR.string.knotwork_memory_detail_edit)

        // Tap row → tap Edit (text button) to enter editing mode → tap
        // Edit again (now the Primary Save button — same string, different
        // role). The OutlinedTextField's draft is seeded from `detail.body`
        // so committing without typing still exercises the
        // `editVectorMemory` call site, which is the single entry point
        // that triggers a re-embedding pass via
        // `TextEmbeddingEngine.generateEmbedding`.
        composeTestRule.onNodeWithText(text = "Editable title").performClick()
        composeTestRule.onNodeWithText(text = editLabel).performClick()
        composeTestRule.onNodeWithText(text = editLabel).performClick()

        verify(exactly = 1) {
            vm.editVectorMemory(id = ROW_ID, newText = "Editable title\nDetailed body content")
        }
    }

    @Test
    fun deleteAction_invokesDeleteVectorMemory() {
        val (vm, _) = mockMemoryViewModel(
            initialUiState = MemoryUiState(
                vectorMemories = listOf(
                    sampleMemoryChunk(id = ROW_ID, text = "Doomed entry\nWill be deleted"),
                ),
            ),
        )

        composeTestRule.setContent {
            MaterialTheme { MemoryScreen(viewModel = vm) }
        }

        val ctx = InstrumentationRegistry.getInstrumentation().targetContext
        val deleteLabel = ctx.getString(KnotworkR.string.knotwork_memory_detail_delete)

        composeTestRule.onNodeWithText(text = "Doomed entry").performClick()
        composeTestRule.onNodeWithText(text = deleteLabel).performClick()

        verify(exactly = 1) { vm.deleteVectorMemory(memoryId = ROW_ID) }
    }

    @Test
    fun pinnedRow_rendersPinGlyph() {
        val (vm, _) = mockMemoryViewModel(
            initialUiState = MemoryUiState(
                vectorMemories = listOf(
                    sampleMemoryChunk(id = 10L, text = "Unpinned row\nbody A"),
                    sampleMemoryChunk(id = 20L, text = "Pinned row\nbody B", isPinned = true),
                ),
            ),
        )

        composeTestRule.setContent {
            MaterialTheme { MemoryScreen(viewModel = vm) }
        }

        val ctx = InstrumentationRegistry.getInstrumentation().targetContext
        val pinnedCd = ctx.getString(KnotworkR.string.knotwork_memory_pinned_cd)

        // Exactly one row renders the pin glyph; both row titles are visible.
        composeTestRule.onAllNodesWithContentDescription(label = pinnedCd)
            .assertCountEquals(expectedSize = 1)
        composeTestRule.onNodeWithText(text = "Pinned row").assertIsDisplayed()
        composeTestRule.onNodeWithText(text = "Unpinned row").assertIsDisplayed()
    }

    @Test
    fun emptyState_emptyCta_navigatesToChat() {
        val (vm, _) = mockMemoryViewModel(
            initialUiState = MemoryUiState(vectorMemories = emptyList()),
        )
        var openChatTaps = 0

        composeTestRule.setContent {
            MaterialTheme {
                MemoryScreen(viewModel = vm, onOpenChat = { openChatTaps += 1 })
            }
        }

        val ctx = InstrumentationRegistry.getInstrumentation().targetContext
        val emptyCtaLabel = ctx.getString(KnotworkR.string.knotwork_memory_empty_cta)

        composeTestRule.onNodeWithText(text = emptyCtaLabel).performClick()

        // Use JUnit assertions (not Kotlin `assert`) so the check is
        // always enforced — Android instrumentation runs do not enable
        // `-ea` by default, which would silently skip a Kotlin assert.
        assertEquals(
            "Empty-state CTA should forward exactly one tap to onOpenChat",
            1,
            openChatTaps,
        )
    }

    private companion object {
        const val ROW_ID: Long = 42L
    }
}
