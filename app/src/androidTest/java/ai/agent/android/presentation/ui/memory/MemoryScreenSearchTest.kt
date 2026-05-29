package ai.agent.android.presentation.ui.memory

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
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
 * `rememberDebouncedString` inside [MemoryScreen] and is backed by a
 * coroutine `delay`, not a Compose frame clock — so the tests poll for
 * the post-debounce condition via [`waitUntil`] instead of advancing
 * `mainClock`. Frame-clock advancement does not reliably drive the
 * coroutine scheduler under the v1 `createComposeRule()` test rule and
 * produced flaky assertions during development; `waitUntil` is the
 * dispatcher-agnostic equivalent.
 *
 * State is plumbed via [mockMemoryViewModel] so we don't touch the real
 * `MemoryRepository` / `EmbeddingProviderResolver` graph.
 */
class MemoryScreenSearchTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun searchQuery_typed_filtersOnceDebounceWindowElapses() {
        val (vm, _) = mockMemoryViewModel()

        composeTestRule.setContent {
            MaterialTheme { MemoryScreen(viewModel = vm) }
        }

        val ctx = InstrumentationRegistry.getInstrumentation().targetContext
        val searchCd = ctx.getString(KnotworkR.string.knotwork_memory_search_cd)

        composeTestRule.onNodeWithContentDescription(searchCd).performTextInput(text = "alpha")

        // Wait until the debounced query takes effect: the non-matching
        // row falls off the list. This is dispatcher-agnostic — any
        // combination of frame clock + coroutine scheduler that
        // eventually applies the 200 ms `delay` will satisfy it.
        composeTestRule.waitUntil(timeoutMillis = WAIT_UNTIL_TIMEOUT_MS) {
            composeTestRule.onAllNodesWithText(text = "beta note about tea")
                .fetchSemanticsNodes()
                .isEmpty()
        }
        composeTestRule.onNodeWithText(text = "alpha note about coffee").assertIsDisplayed()
        composeTestRule.onNodeWithText(text = "beta note about tea").assertDoesNotExist()
    }

    @Test
    fun searchQuery_cleared_restoresFullListAfterDebounce() {
        val (vm, _) = mockMemoryViewModel()

        composeTestRule.setContent {
            MaterialTheme { MemoryScreen(viewModel = vm) }
        }

        val ctx = InstrumentationRegistry.getInstrumentation().targetContext
        val searchCd = ctx.getString(KnotworkR.string.knotwork_memory_search_cd)
        val clearCd = ctx.getString(KnotworkR.string.knotwork_memory_search_clear_cd)

        // Phase 1 — filter down to only the alpha row.
        composeTestRule.onNodeWithContentDescription(searchCd).performTextInput(text = "alpha")
        composeTestRule.waitUntil(timeoutMillis = WAIT_UNTIL_TIMEOUT_MS) {
            composeTestRule.onAllNodesWithText(text = "beta note about tea")
                .fetchSemanticsNodes()
                .isEmpty()
        }

        // Phase 2 — tap the clear icon; both rows reappear once the
        // debounce window elapses on the now-empty query.
        composeTestRule.onNodeWithContentDescription(clearCd).performClick()
        composeTestRule.waitUntil(timeoutMillis = WAIT_UNTIL_TIMEOUT_MS) {
            composeTestRule.onAllNodesWithText(text = "beta note about tea")
                .fetchSemanticsNodes()
                .isNotEmpty()
        }
        composeTestRule.onNodeWithText(text = "alpha note about coffee").assertIsDisplayed()
        composeTestRule.onNodeWithText(text = "beta note about tea").assertIsDisplayed()
    }

    private companion object {
        /**
         * Timeout cap for [`waitUntil`] polling — large enough to absorb
         * the 200 ms production debounce plus generous CI headroom,
         * small enough to fail loudly if the debounced flow stops
         * propagating entirely.
         */
        const val WAIT_UNTIL_TIMEOUT_MS: Long = 2_000L
    }
}
