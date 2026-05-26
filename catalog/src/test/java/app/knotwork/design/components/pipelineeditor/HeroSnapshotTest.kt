package app.knotwork.design.components.pipelineeditor

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.knotwork.design.a11y.FixedKnotworkA11y
import app.knotwork.design.a11y.LocalKnotworkA11y
import app.knotwork.design.theme.KnotworkTheme
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Roborazzi baseline for the README "Pipeline editor" hero shot at the
 * canonical 1080 × 2400 resolution.
 *
 * The catalog harness [PipelineEditorCatalogContent] is intentionally long
 * (it exercises every editor component in a scrollable column for
 * regression diffs); for the marketing-style hero we clip it to a
 * 360 × 800 dp viewport via a `requiredSize + clipToBounds` wrapper so
 * the top of the harness (the per-NodeType card grid — the editor's most
 * recognisable surface) fills the entire frame.
 *
 * After the test passes, the generated baselines should be copied from
 * `catalog/src/test/snapshots/hero_pipeline_editor_{light,dark}.png` into
 * `docs/images/hero-pipeline-editor{,-dark}.png`.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], qualifiers = "w360dp-h800dp-xxhdpi")
class HeroSnapshotTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun hero_pipeline_editor_light() {
        composeTestRule.setContent {
            CompositionLocalProvider(
                LocalKnotworkA11y provides FixedKnotworkA11y(reducedMotion = true),
            ) {
                KnotworkTheme(darkTheme = false) {
                    HeroFrame { PipelineEditorCatalogContent() }
                }
            }
        }
        composeTestRule.onRoot().captureRoboImage(
            filePath = "src/test/snapshots/hero_pipeline_editor_light.png",
        )
    }

    @Test
    fun hero_pipeline_editor_dark() {
        composeTestRule.setContent {
            CompositionLocalProvider(
                LocalKnotworkA11y provides FixedKnotworkA11y(reducedMotion = true),
            ) {
                KnotworkTheme(darkTheme = true) {
                    HeroFrame { PipelineEditorCatalogContent() }
                }
            }
        }
        composeTestRule.onRoot().captureRoboImage(
            filePath = "src/test/snapshots/hero_pipeline_editor_dark.png",
        )
    }

    /**
     * Forces the captured root to the canonical 360 × 800 dp viewport and
     * clips the (taller) catalog content to that frame.
     */
    @androidx.compose.runtime.Composable
    private fun HeroFrame(content: @androidx.compose.runtime.Composable () -> Unit) {
        Box(
            modifier = Modifier
                .requiredSize(width = 360.dp, height = 800.dp)
                .clipToBounds()
                .background(MaterialTheme.colorScheme.surface),
        ) {
            content()
        }
    }
}
