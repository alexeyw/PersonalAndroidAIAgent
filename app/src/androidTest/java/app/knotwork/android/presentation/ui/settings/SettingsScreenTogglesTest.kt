package app.knotwork.android.presentation.ui.settings

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.platform.app.InstrumentationRegistry
import app.knotwork.android.domain.models.Identity
import io.mockk.verify
import org.junit.Rule
import org.junit.Test
import app.knotwork.design.R as KnotworkR

/**
 * Covers a representative toggle row on the Background category sub-screen,
 * exercising the clickable-row wiring that connects the catalog `IconToggleRow`
 * to its ViewModel callback through the `SettingsCallbacks` bag.
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
            MaterialTheme { BackgroundSettingsScreen(viewModel = vm, nav = settingsNavStub()) }
        }

        val ctx = InstrumentationRegistry.getInstrumentation().targetContext
        val rowTitle = ctx.getString(KnotworkR.string.knotwork_settings_notifications_long_running)

        // Clicking the row toggles the Switch via the IconToggleRow's clickable
        // modifier; the resulting `onLongRunningToggle(false)` lands on the VM
        // via the SettingsCallbacks wiring.
        composeTestRule.onNodeWithText(text = rowTitle).performClick()

        verify(exactly = 1) { vm.setLongRunningTaskNotificationsEnabled(enabled = false) }
    }

    private fun identityStub(): Identity = Identity(
        displayName = "Anonymous · this device",
        deviceId = "1234-5678",
        keystoreAvailable = true,
    )
}
