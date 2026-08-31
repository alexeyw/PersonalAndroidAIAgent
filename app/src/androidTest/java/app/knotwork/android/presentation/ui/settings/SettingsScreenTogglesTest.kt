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
    fun scheduledResultsToggleRow_tap_invokesSetScheduledTaskNotificationsEnabled() {
        val (vm, _) = mockSettingsViewModel(
            initialUiState = SettingsUiState(
                identity = identityStub(),
                scheduledTaskNotificationsEnabled = true,
            ),
        )

        composeTestRule.setContent {
            MaterialTheme { BackgroundSettingsScreen(viewModel = vm, nav = settingsNavStub()) }
        }

        val ctx = InstrumentationRegistry.getInstrumentation().targetContext
        val rowTitle = ctx.getString(KnotworkR.string.knotwork_settings_notifications_scheduled_results)

        // Clicking the row toggles the Switch via the IconToggleRow's clickable
        // modifier; the resulting `onScheduledResultsToggle(false)` lands on the
        // VM via the SettingsCallbacks wiring. This used to exercise the
        // Long-running tasks row, which was removed for gating a notification
        // nothing posted — the wiring under test is the same.
        composeTestRule.onNodeWithText(text = rowTitle).performClick()

        verify(exactly = 1) { vm.setScheduledTaskNotificationsEnabled(enabled = false) }
    }

    private fun identityStub(): Identity = Identity(
        displayName = "Anonymous · this device",
        deviceId = "1234-5678",
        keystoreAvailable = true,
    )
}
