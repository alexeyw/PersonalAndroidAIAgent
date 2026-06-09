package app.knotwork.android.presentation.ui.settings

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import app.knotwork.android.domain.models.Identity
import app.knotwork.design.screens.settings.RESTART_BANNER_TEST_TAG
import org.junit.Rule
import org.junit.Test

/**
 * Covers the restart-required banner on the Settings
 * surface (`compose/screens/README.md §C5 · Settings`). The banner is
 * gated on `SettingsUiState.restartRequired`; flipping that flag pushes
 * the visual state to `RestartRequired` and overlays the banner above
 * the body.
 *
 * We assert presence and absence of the banner by its catalog test-tag;
 * tapping the restart action itself is not exercised here because the
 * production callback calls `ProcessPhoenix.triggerRebirth`, which would
 * tear the instrumented test process down.
 */
class SettingsScreenRestartRequiredTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun restartRequired_true_showsBanner() {
        val (vm, _) = mockSettingsViewModel(
            initialUiState = SettingsUiState(
                identity = identityStub(),
                restartRequired = true,
            ),
        )

        composeTestRule.setContent {
            MaterialTheme { SettingsScreen(viewModel = vm) }
        }

        composeTestRule.onNodeWithTag(testTag = RESTART_BANNER_TEST_TAG).assertIsDisplayed()
    }

    @Test
    fun restartRequired_false_bannerHidden() {
        val (vm, _) = mockSettingsViewModel(
            initialUiState = SettingsUiState(
                identity = identityStub(),
                restartRequired = false,
            ),
        )

        composeTestRule.setContent {
            MaterialTheme { SettingsScreen(viewModel = vm) }
        }

        composeTestRule.onNodeWithTag(testTag = RESTART_BANNER_TEST_TAG).assertDoesNotExist()
    }

    private fun identityStub(): Identity = Identity(
        displayName = "Anonymous · this device",
        deviceId = "1234-5678",
        keystoreAvailable = true,
    )
}
