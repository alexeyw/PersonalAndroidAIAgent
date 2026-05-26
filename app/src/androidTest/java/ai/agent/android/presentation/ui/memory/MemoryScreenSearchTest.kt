package ai.agent.android.presentation.ui.memory

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Rule
import org.junit.Test
import app.knotwork.design.R as KnotworkR

/**
 * Phase 23 / Task 8 — covers the 200 ms search-debounce behaviour on the
 * Memory surface (`compose/screens/README.md §C6`). The debounce lives in
 * `rememberDebouncedString` inside [MemoryScreen]; this test drives the
 * compose test clock manually to verify rows are not filtered before the
 * debounce window elapses, and that they are filtered once it does.
 *
 * State is plumbed via [mockMemoryViewModel] so we don't touch the real
 * `MemoryRepository` / `TextEmbeddingEngine` graph.
 */
class MemoryScreenSearchTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun searchQuery_typed_doesNotFilterBeforeDebounceWindow() {
        val (vm, _) = mockMemoryViewModel()

        composeTestRule.mainClock.autoAdvance = false
        composeTestRule.setContent {
            MaterialTheme { MemoryScreen(viewModel = vm) }
        }
        // Flush the initial composition.
        composeTestRule.mainClock.advanceTimeBy(milliseconds = INITIAL_FRAME_MS)

        val ctx = InstrumentationRegistry.getInstrumentation().targetContext
        val searchCd = ctx.getString(KnotworkR.string.knotwork_memory_search_cd)

        composeTestRule.onNodeWithContentDescription(searchCd).performTextInput(text = "alpha")
        composeTestRule.mainClock.advanceTimeBy(milliseconds = BEFORE_DEBOUNCE_MS)

        // Within the debounce window: both rows still rendered.
        composeTestRule.onNodeWithText(text = "alpha note about coffee").assertIsDisplayed()
        composeTestRule.onNodeWithText(text = "beta note about tea").assertIsDisplayed()
    }

    @Test
    fun searchQuery_typed_filtersOnceDebounceWindowElapses() {
        val (vm, _) = mockMemoryViewModel()

        composeTestRule.mainClock.autoAdvance = false
        composeTestRule.setContent {
            MaterialTheme { MemoryScreen(viewModel = vm) }
        }
        composeTestRule.mainClock.advanceTimeBy(milliseconds = INITIAL_FRAME_MS)

        val ctx = InstrumentationRegistry.getInstrumentation().targetContext
        val searchCd = ctx.getString(KnotworkR.string.knotwork_memory_search_cd)

        composeTestRule.onNodeWithContentDescription(searchCd).performTextInput(text = "alpha")

        // Cross the 200 ms debounce; the matching row stays, the other drops.
        composeTestRule.mainClock.advanceTimeBy(milliseconds = PAST_DEBOUNCE_MS)

        composeTestRule.onNodeWithText(text = "alpha note about coffee").assertIsDisplayed()
        composeTestRule.onNodeWithText(text = "beta note about tea").assertDoesNotExist()
    }

    @Test
    fun searchQuery_cleared_restoresFullListAfterDebounce() {
        val (vm, _) = mockMemoryViewModel()

        composeTestRule.mainClock.autoAdvance = false
        composeTestRule.setContent {
            MaterialTheme { MemoryScreen(viewModel = vm) }
        }
        composeTestRule.mainClock.advanceTimeBy(milliseconds = INITIAL_FRAME_MS)

        val ctx = InstrumentationRegistry.getInstrumentation().targetContext
        val searchCd = ctx.getString(KnotworkR.string.knotwork_memory_search_cd)
        val clearCd = ctx.getString(KnotworkR.string.knotwork_memory_search_clear_cd)

        // Filter down to only the alpha row.
        composeTestRule.onNodeWithContentDescription(searchCd).performTextInput(text = "alpha")
        composeTestRule.mainClock.advanceTimeBy(milliseconds = PAST_DEBOUNCE_MS)
        composeTestRule.onNodeWithText(text = "beta note about tea").assertDoesNotExist()

        // Tap the clear icon; both rows reappear after the debounce.
        composeTestRule.onNodeWithContentDescription(clearCd).performClick()
        composeTestRule.mainClock.advanceTimeBy(milliseconds = PAST_DEBOUNCE_MS)

        composeTestRule.onNodeWithText(text = "alpha note about coffee").assertIsDisplayed()
        composeTestRule.onNodeWithText(text = "beta note about tea").assertIsDisplayed()
    }

    private companion object {
        /** Time required to flush the very first composition under a manual clock. */
        const val INITIAL_FRAME_MS: Long = 32L

        /** Comfortably below the 200 ms debounce floor. */
        const val BEFORE_DEBOUNCE_MS: Long = 150L

        /** Comfortably past the 200 ms debounce floor; leaves headroom for recomposition. */
        const val PAST_DEBOUNCE_MS: Long = 350L
    }
}
