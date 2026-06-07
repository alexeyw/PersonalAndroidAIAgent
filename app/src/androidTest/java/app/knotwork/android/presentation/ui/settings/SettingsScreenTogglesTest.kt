package app.knotwork.android.presentation.ui.settings

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.test.platform.app.InstrumentationRegistry
import app.knotwork.android.domain.models.Identity
import app.knotwork.design.screens.settings.SETTINGS_BODY_TEST_TAG
import io.mockk.verify
import org.junit.Rule
import org.junit.Test
import app.knotwork.design.R as KnotworkR

/**
 * Phase 23 / Task 8 — covers a representative toggle row inside the
 * Settings body, exercising the LazyColumn scroll + clickable-row wiring
 * that connects the catalog `IconToggleRow` to its ViewModel callback.
 *
 * The Notifications card sits near the bottom of the body so reaching it
 * also verifies the scrollable container is wired through to its test
 * tag (`SETTINGS_BODY_TEST_TAG`).
 */
class SettingsScreenTogglesTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun longRunningToggleRow_tap_invokesSetLongRunningTaskNotificationsEnabled() {
        val (vm, _) = mockSettingsViewModel(
            initialUiState = SettingsUiState(
                identity = identityStub(),
                longRunningTaskNotificationsEnabled = true,
            ),
        )

        composeTestRule.setContent {
            MaterialTheme { SettingsScreen(viewModel = vm) }
        }

        val ctx = InstrumentationRegistry.getInstrumentation().targetContext
        val rowTitle = ctx.getString(KnotworkR.string.knotwork_settings_notifications_long_running)

        // Scroll the body until the target row is composed, then click it.
        // Clicking the row toggles the Switch via the IconToggleRow's
        // clickable modifier; the resulting `onLongRunningToggle(false)`
        // call lands on the VM via the SettingsCallbacks wiring.
        composeTestRule.onNodeWithTag(testTag = SETTINGS_BODY_TEST_TAG)
            .performScrollToNode(matcher = hasText(text = rowTitle))
        composeTestRule.onNodeWithText(text = rowTitle).performClick()

        verify(exactly = 1) { vm.setLongRunningTaskNotificationsEnabled(enabled = false) }
    }

    private fun identityStub(): Identity = Identity(
        displayName = "Anonymous · this device",
        deviceId = "1234-5678",
        keystoreAvailable = true,
    )
}
