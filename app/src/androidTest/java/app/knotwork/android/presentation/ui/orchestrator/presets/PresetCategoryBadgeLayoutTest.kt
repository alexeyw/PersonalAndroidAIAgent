package app.knotwork.android.presentation.ui.orchestrator.presets

import androidx.compose.runtime.Composable
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.knotwork.android.domain.models.PipelineGraph
import app.knotwork.android.domain.models.PipelinePreset
import app.knotwork.android.domain.models.PresetCategory
import app.knotwork.design.theme.KnotworkTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Layout guard for the category badge on both preset rows.
 *
 * ### The defect this exists for
 *
 * In a Compose `Row`, unweighted children are measured first against the full
 * available width. So `Row { Text(longTitle); Badge() }` hands the title
 * everything and leaves the badge **zero width** — its label then wraps one
 * character per line and renders as a tall vertical sliver that inflates the
 * row. `maxLines = 1` on the title does not prevent it: the title only
 * ellipsizes once something else constrains it, and nothing does. The fix is
 * to weight the **title** (`weight(1f, fill = false)`), not the badge.
 *
 * This has shipped **twice**, on the two sibling rows tested here, both times
 * triggered by the same preset name — because nothing measured these rows.
 *
 * ### Why this is an instrumented test
 *
 * It has to measure a real layout pass. The same assertions written as JVM
 * unit tests against a Robolectric-hosted composition were tried first and
 * **reported nonsense** — a 62 dp root for a full-width row, a 3.5 dp badge on
 * a healthy one — and passed with every fix reverted. A green test that a
 * mutation cannot break is worse than no test, so the guard lives here, where
 * the numbers are real.
 *
 * Note that `./gradlew check` neither runs nor compiles this source set;
 * compile it with `:app:compileFullDebugAndroidTestKotlin` and run it with
 * `connectedFullDebugAndroidTest`.
 */
@RunWith(AndroidJUnit4::class)
class PresetCategoryBadgeLayoutTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    /** Long enough to claim an entire phone-width row on its own. */
    private val longTitle = "Virtual Companion (Mood Router) with an even longer trailing name"

    @Test
    fun managerRowKeepsItsBadgeOnScreen() {
        composeTestRule.setContent {
            KnotworkTheme(darkTheme = false) {
                PresetManagerRow(preset = preset(), onRename = {}, onDelete = {}, onExport = {})
            }
        }

        assertBadgeIsFullyVisible()
    }

    @Test
    fun pickerRowKeepsItsBadgeOnScreen() {
        composeTestRule.setContent {
            KnotworkTheme(darkTheme = false) {
                PresetPickerRow(preset = preset(), selected = false, onClick = {})
            }
        }

        assertBadgeIsFullyVisible()
    }

    /**
     * Asserts the badge sits entirely inside the row.
     *
     * Position, not height. Height is the tempting measurement and it is the
     * wrong one: it catches the sliver but not a badge that has simply been
     * pushed past the right edge, which is the same bug one layout constraint
     * later. What breaks for the user in both cases is that they cannot see
     * the badge.
     */
    private fun assertBadgeIsFullyVisible() {
        // `useUnmergedTree` is required: a preset row is clickable, and a
        // clickable merges its descendants' semantics, so the merged tree
        // resolves this text to the whole row and reports the row's box.
        val badge = composeTestRule
            .onNodeWithText(text = "Hybrid", useUnmergedTree = true)
            .getUnclippedBoundsInRoot()
        val root = composeTestRule.onRoot().getUnclippedBoundsInRoot()

        assertTrue(
            "Badge spans ${badge.left}..${badge.right} but the row ends at ${root.right} — " +
                "it was measured after the title and pushed off the edge. Weight the title, not the badge.",
            badge.right <= root.right,
        )
    }

    @Composable
    private fun preset() = PipelinePreset(
        id = "preset-1",
        name = longTitle,
        description = "A companion that senses your mood and shifts how it talks with you.",
        category = PresetCategory.HYBRID,
        graph = PipelineGraph(id = "graph-1", name = longTitle),
        isBundled = true,
    )
}
