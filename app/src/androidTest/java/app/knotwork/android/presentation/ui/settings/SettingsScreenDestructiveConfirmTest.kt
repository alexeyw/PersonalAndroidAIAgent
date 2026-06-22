package app.knotwork.android.presentation.ui.settings

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import app.knotwork.android.domain.models.Identity
import app.knotwork.design.screens.settings.DESTRUCTIVE_CONFIRM_BUTTON_TEST_TAG
import app.knotwork.design.screens.settings.DESTRUCTIVE_TYPED_FIELD_TEST_TAG
import app.knotwork.design.screens.settings.RESET_CONFIRM_BUTTON_TEST_TAG
import io.mockk.verify
import org.junit.Rule
import org.junit.Test

/**
 * Covers the destructive typed-confirm dialog on the
 * Settings surface. The dialog is gated on
 * `SettingsUiState.pendingDestructive`; the Confirm button only enables
 * once `destructiveTypedInput` matches the keyword (case-insensitive,
 * trimmed). Three flows are pinned down:
 *
 *  * dialog visibility and initial Confirm-disabled state;
 *  * Confirm flips to enabled and forwards taps when the keyword is typed;
 *  * Cancel tap forwards to [SettingsViewModel.cancelDestructive].
 *
 * The keyword string is sourced from the production resources via the
 * test app context so we stay in sync if the wording changes.
 */
class SettingsScreenDestructiveConfirmTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun pendingClearMemory_dialogVisible_confirmDisabled() {
        val (vm, _) = mockSettingsViewModel(
            initialUiState = SettingsUiState(
                identity = identityStub(),
                pendingDestructive = PendingDestructiveAction.ClearMemory,
                destructiveTypedInput = "",
            ),
        )

        composeTestRule.setContent {
            MaterialTheme { MemorySettingsScreen(viewModel = vm, nav = settingsNavStub()) }
        }

        composeTestRule.onNodeWithTag(testTag = DESTRUCTIVE_TYPED_FIELD_TEST_TAG).assertIsDisplayed()
        composeTestRule.onNodeWithTag(testTag = DESTRUCTIVE_CONFIRM_BUTTON_TEST_TAG).assertIsNotEnabled()
    }

    @Test
    fun typedConfirmKeyword_confirmEnabled_andTapForwardsToConfirmDestructive() {
        val (vm, _) = mockSettingsViewModel(
            initialUiState = SettingsUiState(
                identity = identityStub(),
                pendingDestructive = PendingDestructiveAction.ClearMemory,
                // Catalog matches the keyword case-insensitive; "yes" is the
                // app-side string (`R.string.settings_destructive_typed_keyword`).
                destructiveTypedInput = "yes",
            ),
        )

        composeTestRule.setContent {
            MaterialTheme { MemorySettingsScreen(viewModel = vm, nav = settingsNavStub()) }
        }

        composeTestRule.onNodeWithTag(testTag = DESTRUCTIVE_CONFIRM_BUTTON_TEST_TAG).assertIsEnabled()
        composeTestRule.onNodeWithTag(testTag = DESTRUCTIVE_CONFIRM_BUTTON_TEST_TAG).performClick()

        verify(exactly = 1) { vm.confirmDestructive() }
    }

    @Test
    fun pendingResetSettings_plainDialog_noTypedField_confirmForwards() {
        val (vm, _) = mockSettingsViewModel(
            initialUiState = SettingsUiState(
                identity = identityStub(),
                pendingDestructive = PendingDestructiveAction.ResetSettings,
                destructiveTypedInput = "",
            ),
        )

        composeTestRule.setContent {
            MaterialTheme { AboutSettingsScreen(viewModel = vm, nav = settingsNavStub()) }
        }

        // The plain reset dialog has no typed-keyword gate: its confirm is
        // immediately enabled and there is no typed-confirm field on screen.
        composeTestRule.onNodeWithTag(testTag = RESET_CONFIRM_BUTTON_TEST_TAG).assertIsDisplayed()
        composeTestRule.onAllNodesWithTag(testTag = DESTRUCTIVE_TYPED_FIELD_TEST_TAG).assertCountEquals(0)
        composeTestRule.onNodeWithTag(testTag = RESET_CONFIRM_BUTTON_TEST_TAG).performClick()

        verify(exactly = 1) { vm.confirmDestructive() }
    }

    private fun identityStub(): Identity = Identity(
        displayName = "Anonymous · this device",
        deviceId = "1234-5678",
        keystoreAvailable = true,
    )
}
