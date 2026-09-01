package app.knotwork.android.presentation.ui.settings

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
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
 *
 * **Where the row is tapped is part of the contract, not an implementation
 * detail of the test.** Every settings label now carries a help glyph beside it,
 * and that glyph is an `IconButton`, so Compose gives it the 48 dp minimum touch
 * target its 28 dp footprint would otherwise miss. For a short label the
 * enlarged target sits near the row's horizontal centre — which is exactly where
 * a click on the row's own node lands. This test used to click there and passed
 * only because the glyph did not exist yet; when it did, the click opened the
 * hint and the toggle was never reached. So the row is tapped at its leading
 * edge, and the second test pins the other side of the boundary rather than
 * leaving it to be rediscovered.
 */
class SettingsScreenTogglesTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun scheduledResultsToggleRow_tapAwayFromHint_invokesSetScheduledTaskNotificationsEnabled() {
        val (vm, _) = mockSettingsViewModel(
            initialUiState = SettingsUiState(
                identity = identityStub(),
                scheduledTaskNotificationsEnabled = true,
            ),
        )

        composeTestRule.setContent {
            MaterialTheme { BackgroundSettingsScreen(viewModel = vm, nav = settingsNavStub()) }
        }

        // Clicking the row toggles the Switch via the IconToggleRow's clickable
        // modifier; the resulting `onScheduledResultsToggle(false)` lands on the
        // VM via the SettingsCallbacks wiring. This used to exercise the
        // Long-running tasks row, which was removed for gating a notification
        // nothing posted — the wiring under test is the same.
        composeTestRule.onNodeWithText(text = rowTitle()).performTouchInput {
            // Leading edge, over the row's own icon: clear of the help glyph's
            // touch target, which sits just after the label.
            click(Offset(x = width * LEADING_EDGE_FRACTION, y = height / 2f))
        }

        verify(exactly = 1) { vm.setScheduledTaskNotificationsEnabled(enabled = false) }
    }

    @Test
    fun scheduledResultsHelpGlyph_tap_opensTheHintAndLeavesTheSettingAlone() {
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
        val glyphDescription = ctx.getString(KnotworkR.string.knotwork_settings_hint_open_cd, rowTitle())

        val collapsed = ctx.getString(KnotworkR.string.knotwork_settings_hint_state_collapsed)
        val expanded = ctx.getString(KnotworkR.string.knotwork_settings_hint_state_expanded)
        val glyph = composeTestRule.onNodeWithContentDescription(label = glyphDescription)

        glyph.assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, collapsed))
        glyph.performClick()

        // Both halves are asserted on purpose. That the hint *opened* is what
        // makes this a test of the glyph rather than of a dead pixel — a click
        // that did nothing at all would satisfy the second assertion alone. And
        // that the setting did not move is what gives the first test's offset its
        // meaning: without it, moving that click back to the row centre would
        // look like a passing test of the row.
        glyph.assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, expanded))
        verify(exactly = 0) { vm.setScheduledTaskNotificationsEnabled(any()) }
    }

    /** Title of the row under test, resolved from the shipped catalog string. */
    private fun rowTitle(): String = InstrumentationRegistry.getInstrumentation().targetContext
        .getString(KnotworkR.string.knotwork_settings_notifications_scheduled_results)

    private fun identityStub(): Identity = Identity(
        displayName = "Anonymous · this device",
        deviceId = "1234-5678",
        keystoreAvailable = true,
    )

    private companion object {
        /**
         * How far across the row to click: far enough in to be inside the
         * clickable, far enough from the label's end to clear the help glyph.
         */
        const val LEADING_EDGE_FRACTION = 0.1f
    }
}
