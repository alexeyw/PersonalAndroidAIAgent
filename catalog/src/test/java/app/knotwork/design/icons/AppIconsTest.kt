package app.knotwork.design.icons

import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Cheap correctness gate for every [AppIcons] entry.
 *
 * Each custom icon must:
 *  - declare a `defaultWidth` / `defaultHeight` of 24 dp (matches the SVG
 *    sources in `project_docs/design/icons-src/`);
 *  - render against a 24×24 viewport so the vector stays grid-aligned
 *    inside [androidx.compose.material3.Icon] at any tint colour.
 *
 * Catches off-grid ports immediately at JVM-test time, without needing the
 * full Roborazzi snapshot pipeline.
 */
class AppIconsTest {

    private val allIcons: List<Pair<String, ImageVector>> = listOf(
        "Wordmark" to AppIcons.Wordmark,
        "Mark" to AppIcons.Mark,
        "Flow" to AppIcons.Flow,
        "AutoLayout" to AppIcons.AutoLayout,
        "Brain" to AppIcons.Brain,
        "NodeInput" to AppIcons.NodeInput,
        "NodeIntentRouter" to AppIcons.NodeIntentRouter,
        "NodeBranch" to AppIcons.NodeBranch,
        "NodeClarify" to AppIcons.NodeClarify,
        "NodeLite" to AppIcons.NodeLite,
        "NodeCloud" to AppIcons.NodeCloud,
        "NodeTool" to AppIcons.NodeTool,
        "NodeDecompose" to AppIcons.NodeDecompose,
        "NodeQueue" to AppIcons.NodeQueue,
        "NodeEval" to AppIcons.NodeEval,
        "NodeSummary" to AppIcons.NodeSummary,
        "NodeOutput" to AppIcons.NodeOutput,
    )

    @Test
    fun `every custom icon ships at 24x24 dp default size`() {
        allIcons.forEach { (name, vector) ->
            assertEquals("$name defaultWidth must be 24.dp", 24.dp, vector.defaultWidth)
            assertEquals("$name defaultHeight must be 24.dp", 24.dp, vector.defaultHeight)
        }
    }

    @Test
    fun `every custom icon uses a 24-unit viewport`() {
        allIcons.forEach { (name, vector) ->
            assertEquals("$name viewportWidth must be 24f", 24f, vector.viewportWidth)
            assertEquals("$name viewportHeight must be 24f", 24f, vector.viewportHeight)
        }
    }

    @Test
    fun `every custom icon ships at least one path`() {
        allIcons.forEach { (name, vector) ->
            assertTrue("$name must contain at least one path node", vector.root.size > 0)
        }
    }

    @Test
    fun `every custom icon declares a stable name`() {
        allIcons.forEach { (name, vector) ->
            assertTrue("$name must declare a non-empty ImageVector.name", vector.name.isNotEmpty())
        }
    }
}
