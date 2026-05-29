package ai.agent.android.presentation.ui.prompts

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.platform.app.InstrumentationRegistry
import io.mockk.verify
import org.junit.Rule
import org.junit.Test
import ai.agent.android.R as AppR

/**
 * Phase 23 / Task 8 — covers the prompt editor `ModalBottomSheet` on the
 * Prompt Library surface. The sheet is gated on
 * `PromptLibraryUiState.editorDraft`; a non-null draft renders the
 * editor body with prefilled fields, and the Save / Cancel CTAs forward
 * to the matching ViewModel hooks.
 */
class PromptLibraryScreenEditorTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun editorDraftNonNull_sheetVisible_andFieldsPrefilled() {
        val (vm, _) = mockPromptLibraryViewModel(
            initialUiState = PromptLibraryUiState(
                editorDraft = PromptEditorDraft(
                    id = "99",
                    name = "Existing prompt name",
                    category = "DECOMPOSITION",
                    body = "Existing prompt body",
                ),
            ),
        )

        composeTestRule.setContent {
            MaterialTheme { PromptLibraryScreen(viewModel = vm) }
        }

        val ctx = InstrumentationRegistry.getInstrumentation().targetContext
        val editTitle = ctx.getString(AppR.string.prompts_editor_title_edit)

        // Editor sheet title flips to "Edit prompt" because draft.id is
        // non-null. The prefilled name / body values render inside the
        // form, and the persisted category renders verbatim.
        composeTestRule.onNodeWithText(text = editTitle).assertIsDisplayed()
        composeTestRule.onNodeWithText(text = "Existing prompt name").assertIsDisplayed()
        composeTestRule.onNodeWithText(text = "Existing prompt body").assertIsDisplayed()
    }

    @Test
    fun cancelButton_invokesCloseEditor() {
        val (vm, _) = mockPromptLibraryViewModel(
            initialUiState = PromptLibraryUiState(
                editorDraft = PromptEditorDraft(name = "Untitled", category = "DECOMPOSITION"),
            ),
        )

        composeTestRule.setContent {
            MaterialTheme { PromptLibraryScreen(viewModel = vm) }
        }

        val ctx = InstrumentationRegistry.getInstrumentation().targetContext
        val cancelLabel = ctx.getString(AppR.string.common_cancel)

        composeTestRule.onNodeWithText(text = cancelLabel).performClick()

        verify(atLeast = 1) { vm.closeEditor() }
    }

    @Test
    fun saveButton_invokesSaveEditor() {
        val (vm, _) = mockPromptLibraryViewModel(
            initialUiState = PromptLibraryUiState(
                editorDraft = PromptEditorDraft(name = "New prompt", category = "DECOMPOSITION"),
            ),
        )

        composeTestRule.setContent {
            MaterialTheme { PromptLibraryScreen(viewModel = vm) }
        }

        val ctx = InstrumentationRegistry.getInstrumentation().targetContext
        val saveLabel = ctx.getString(AppR.string.common_save)

        composeTestRule.onNodeWithText(text = saveLabel).performClick()

        verify(exactly = 1) { vm.saveEditor() }
    }
}
