package ai.agent.android.presentation.ui.prompts

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.platform.app.InstrumentationRegistry
import io.mockk.verify
import org.junit.Rule
import org.junit.Test
import ai.agent.android.R as AppR

/**
 * Phase 23 / Task 8 — covers the Prompt Library list surface: prompts in
 * the currently-selected category render with their name; the category
 * `ScrollableTabRow` forwards selections to
 * [PromptLibraryViewModel.selectCategory]; and the per-card Duplicate /
 * Delete affordances + the FAB call into their matching VM hooks.
 */
class PromptLibraryScreenListTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun selectedCategory_rendersOnlyPromptsInThatCategory() {
        val (vm, _) = mockPromptLibraryViewModel(
            initialUiState = PromptLibraryUiState(
                promptTemplates = listOf(
                    samplePromptTemplate(id = 1L, name = "Decompose first", category = "DECOMPOSITION"),
                    samplePromptTemplate(id = 2L, name = "Route the intent", category = "INTENT_ROUTER"),
                ),
                selectedCategory = "DECOMPOSITION",
            ),
        )

        composeTestRule.setContent {
            MaterialTheme { PromptLibraryScreen(viewModel = vm) }
        }

        // Only the DECOMPOSITION row's name renders in the list; the
        // INTENT_ROUTER prompt is filtered out by the category mapper.
        composeTestRule.onNodeWithText(text = "Decompose first").assertIsDisplayed()
        composeTestRule.onNodeWithText(text = "Route the intent").assertDoesNotExist()
    }

    @Test
    fun categoryTab_tap_invokesSelectCategory() {
        val (vm, _) = mockPromptLibraryViewModel(
            initialUiState = PromptLibraryUiState(
                promptTemplates = listOf(
                    samplePromptTemplate(id = 1L, name = "Decompose first", category = "DECOMPOSITION"),
                    samplePromptTemplate(id = 2L, name = "Route the intent", category = "INTENT_ROUTER"),
                ),
                selectedCategory = "DECOMPOSITION",
            ),
        )

        composeTestRule.setContent {
            MaterialTheme { PromptLibraryScreen(viewModel = vm) }
        }

        // INTENT_ROUTER is the label of the other tab; tapping it fires
        // the VM hook even though the screen doesn't yet re-render
        // (state mutation is the ViewModel's job).
        composeTestRule.onNodeWithText(text = "INTENT_ROUTER").performClick()

        verify(exactly = 1) { vm.selectCategory(category = "INTENT_ROUTER") }
    }

    @Test
    fun fabTap_opensNewPromptEditor() {
        val (vm, _) = mockPromptLibraryViewModel(
            initialUiState = PromptLibraryUiState(
                promptTemplates = listOf(
                    samplePromptTemplate(id = 1L, name = "Prompt A", category = "DECOMPOSITION"),
                ),
                selectedCategory = "DECOMPOSITION",
            ),
        )

        composeTestRule.setContent {
            MaterialTheme { PromptLibraryScreen(viewModel = vm) }
        }

        val ctx = InstrumentationRegistry.getInstrumentation().targetContext
        val fabCd = ctx.getString(AppR.string.prompts_fab_cd)

        composeTestRule.onNodeWithContentDescription(label = fabCd).performClick()

        verify(exactly = 1) { vm.openEditor(promptId = null) }
    }

    @Test
    fun deleteIcon_invokesDeletePrompt() {
        val (vm, _) = mockPromptLibraryViewModel(
            initialUiState = PromptLibraryUiState(
                promptTemplates = listOf(
                    samplePromptTemplate(id = 7L, name = "Doomed prompt", category = "DECOMPOSITION"),
                ),
                selectedCategory = "DECOMPOSITION",
            ),
        )

        composeTestRule.setContent {
            MaterialTheme { PromptLibraryScreen(viewModel = vm) }
        }

        val ctx = InstrumentationRegistry.getInstrumentation().targetContext
        val deleteCd = ctx.getString(AppR.string.prompts_delete_cd)

        // Only one card on the surface, so the first node carrying the
        // delete content description is the one we want.
        composeTestRule.onAllNodesWithContentDescription(label = deleteCd).onFirst().performClick()

        verify(exactly = 1) { vm.deletePrompt(id = 7L) }
    }

    @Test
    fun editIcon_invokesOpenEditor_withPromptId() {
        val (vm, _) = mockPromptLibraryViewModel(
            initialUiState = PromptLibraryUiState(
                promptTemplates = listOf(
                    samplePromptTemplate(id = 11L, name = "Edit me", category = "DECOMPOSITION"),
                ),
                selectedCategory = "DECOMPOSITION",
            ),
        )

        composeTestRule.setContent {
            MaterialTheme { PromptLibraryScreen(viewModel = vm) }
        }

        val ctx = InstrumentationRegistry.getInstrumentation().targetContext
        val editCd = ctx.getString(AppR.string.prompts_edit_cd)

        composeTestRule.onAllNodesWithContentDescription(label = editCd).onFirst().performClick()

        verify(exactly = 1) { vm.openEditor(promptId = 11L) }
    }

    @Test
    fun duplicateButton_invokesDuplicatePrompt() {
        val (vm, _) = mockPromptLibraryViewModel(
            initialUiState = PromptLibraryUiState(
                promptTemplates = listOf(
                    samplePromptTemplate(id = 13L, name = "Cloneable", category = "DECOMPOSITION"),
                ),
                selectedCategory = "DECOMPOSITION",
            ),
        )

        composeTestRule.setContent {
            MaterialTheme { PromptLibraryScreen(viewModel = vm) }
        }

        val ctx = InstrumentationRegistry.getInstrumentation().targetContext
        val duplicateLabel = ctx.getString(AppR.string.prompts_duplicate)

        composeTestRule.onNodeWithText(text = duplicateLabel).performClick()

        verify(exactly = 1) { vm.duplicatePrompt(id = 13L) }
    }
}
