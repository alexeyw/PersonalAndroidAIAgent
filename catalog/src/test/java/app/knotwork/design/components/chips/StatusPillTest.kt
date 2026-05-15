package app.knotwork.design.components.chips

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.knotwork.design.theme.KnotworkTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * Verifies every [StatusPill] variant exposes a `Status: <label>`
 * `contentDescription` so colour is never the only signal
 * (`decisions.md §14`). All variants are rendered in one column so a single
 * `setContent` block exercises the full mapping.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [36])
class StatusPillTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun `every status variant exposes a content description`() {
        composeTestRule.setContent {
            KnotworkTheme {
                Column(verticalArrangement = Arrangement.spacedBy(KnotworkTheme.spacing.sp1)) {
                    Status.entries.forEach { status -> StatusPill(status = status) }
                }
            }
        }
        listOf("Idle", "Running", "Success", "Warning", "Error").forEach { label ->
            composeTestRule.onNodeWithContentDescription("Status: $label").assertIsDisplayed()
        }
    }
}
