package app.knotwork.design.screens.onboarding

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
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
 * Roborazzi snapshot baseline for `OnboardingContent` across the 4 steps in
 * both themes.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], qualifiers = "w360dp-h760dp-xhdpi")
class OnboardingContentSnapshotTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun onboarding_step1_welcome_light() = snapshot(name = "step1_welcome", dark = false) {
        OnboardingContent(state = OnboardingPreview.welcome())
    }

    @Test
    fun onboarding_step1_welcome_dark() = snapshot(name = "step1_welcome", dark = true) {
        OnboardingContent(state = OnboardingPreview.welcome())
    }

    @Test
    fun onboarding_step2_lite_rt_light() = snapshot(name = "step2_lite_rt", dark = false) {
        OnboardingContent(state = OnboardingPreview.liteRtModel())
    }

    @Test
    fun onboarding_step2_lite_rt_dark() = snapshot(name = "step2_lite_rt", dark = true) {
        OnboardingContent(state = OnboardingPreview.liteRtModel())
    }

    @Test
    fun onboarding_step3_cloud_keys_light() = snapshot(name = "step3_cloud_keys", dark = false) {
        OnboardingContent(state = OnboardingPreview.cloudKeys())
    }

    @Test
    fun onboarding_step3_cloud_keys_dark() = snapshot(name = "step3_cloud_keys", dark = true) {
        OnboardingContent(state = OnboardingPreview.cloudKeys())
    }

    @Test
    fun onboarding_step4_ready_light() = snapshot(name = "step4_ready", dark = false) {
        OnboardingContent(state = OnboardingPreview.ready())
    }

    @Test
    fun onboarding_step4_ready_dark() = snapshot(name = "step4_ready", dark = true) {
        OnboardingContent(state = OnboardingPreview.ready())
    }

    private fun snapshot(name: String, dark: Boolean, content: @Composable () -> Unit) {
        composeTestRule.setContent {
            CompositionLocalProvider(LocalKnotworkA11y provides FixedKnotworkA11y(reducedMotion = true)) {
                KnotworkTheme(darkTheme = dark) { content() }
            }
        }
        val themeTag = if (dark) "dark" else "light"
        composeTestRule.onRoot().captureRoboImage(
            filePath = "src/test/snapshots/onboarding_${name}_$themeTag.png",
        )
    }
}

/** Internal preview fixtures backing the onboarding snapshot suite. */
internal object OnboardingPreview {

    fun welcome(): OnboardingViewState = OnboardingViewState(
        step = OnboardingStep.Welcome,
        defaultPipelinePreview = defaultPipelinePreview(),
    )

    fun liteRtModel(): OnboardingViewState = OnboardingViewState(
        step = OnboardingStep.LiteRtModel,
        liteRtModel = OnboardingLiteRtModel.Gemma4E2B,
        defaultPipelinePreview = defaultPipelinePreview(),
    )

    fun cloudKeys(): OnboardingViewState = OnboardingViewState(
        step = OnboardingStep.CloudKeys,
        defaultPipelinePreview = defaultPipelinePreview(),
    )

    fun ready(): OnboardingViewState = OnboardingViewState(
        step = OnboardingStep.Ready,
        defaultPipelinePreview = defaultPipelinePreview(),
    )

    private fun defaultPipelinePreview(): OnboardingDefaultPipelinePreview = OnboardingDefaultPipelinePreview(
        nodes = listOf("INPUT", "LITE_RT", "IF", "TOOL", "LITE_RT", "OUTPUT"),
        nodeCount = 6,
        edgeCount = 7,
        accentNodeName = "IF",
    )
}
