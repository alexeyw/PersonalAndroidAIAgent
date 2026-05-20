package ai.agent.android.presentation.ui.settings

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Rule
import org.junit.Test

/**
 * Instrumented coverage for the Knotwork-styled `SettingsScreen`.
 *
 * Verifies that:
 *  1. The OpenAI provider row collapses by default and expands on tap.
 *  2. Typing into the expanded API-key field reaches the ViewModel.
 *
 * The ViewModel is faked via MockK so the test exercises the screen
 * composition + the `KnotworkProviderRow` slot without touching DataStore
 * or EncryptedSharedPreferences.
 */
class SettingsScreenTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun tappingProviderRow_expandsSectionAndPersistsKey() {
        val mockViewModel = mockk<SettingsViewModel>(relaxed = true)
        val fakeState = MutableStateFlow(SettingsUiState())
        every { mockViewModel.uiState } returns fakeState

        composeTestRule.setContent {
            SettingsScreen(viewModel = mockViewModel)
        }

        // The collapsed card header shows the provider title.
        composeTestRule.onNodeWithText("OpenAI").assertIsDisplayed()

        // Tap the header — body fields reveal the API-key label.
        composeTestRule.onNodeWithText("OpenAI").performClick()
        composeTestRule.onNodeWithText("OpenAI API Key").assertIsDisplayed()

        // Type into the expanded API-key field — the VM receives the write.
        composeTestRule.onNodeWithText("OpenAI API Key").performTextInput("sk-test-123")
        verify { mockViewModel.updateOpenAiKey(any()) }
    }
}
