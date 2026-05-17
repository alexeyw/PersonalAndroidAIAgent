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
 * both themes. The cloud-card variant (`step2_cloud_invalid`) covers the
 * inline-error branch of step 2.
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
    fun onboarding_step2_model_source_light() = snapshot(name = "step2_model_source", dark = false) {
        OnboardingContent(state = OnboardingPreview.modelSource())
    }

    @Test
    fun onboarding_step2_model_source_dark() = snapshot(name = "step2_model_source", dark = true) {
        OnboardingContent(state = OnboardingPreview.modelSource())
    }

    @Test
    fun onboarding_step2_cloud_invalid_light() = snapshot(name = "step2_cloud_invalid", dark = false) {
        OnboardingContent(state = OnboardingPreview.cloudInvalid())
    }

    @Test
    fun onboarding_step3_permissions_light() = snapshot(name = "step3_permissions", dark = false) {
        OnboardingContent(state = OnboardingPreview.permissions())
    }

    @Test
    fun onboarding_step3_permissions_dark() = snapshot(name = "step3_permissions", dark = true) {
        OnboardingContent(state = OnboardingPreview.permissions())
    }

    @Test
    fun onboarding_step4_samples_light() = snapshot(name = "step4_samples", dark = false) {
        OnboardingContent(state = OnboardingPreview.samples())
    }

    @Test
    fun onboarding_step4_samples_dark() = snapshot(name = "step4_samples", dark = true) {
        OnboardingContent(state = OnboardingPreview.samples())
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

    fun welcome(): OnboardingViewState = OnboardingViewState(step = OnboardingStep.Welcome)

    fun modelSource(): OnboardingViewState = OnboardingViewState(
        step = OnboardingStep.ModelSource,
        modelSource = OnboardingModelSource.LocalOnly,
    )

    fun cloudInvalid(): OnboardingViewState = OnboardingViewState(
        step = OnboardingStep.ModelSource,
        modelSource = OnboardingModelSource.Cloud,
        apiKey = "garbage",
        apiKeyError = "Key doesn't look like a known provider format.",
    )

    fun permissions(): OnboardingViewState = OnboardingViewState(
        step = OnboardingStep.Permissions,
        permissions = listOf(
            OnboardingPermissionRow(
                id = PermissionIds.NOTIFICATIONS,
                title = "Notifications",
                body = "Tell you when long pipelines finish.",
                state = OnboardingPermissionState.NotRequested,
            ),
            OnboardingPermissionRow(
                id = PermissionIds.MICROPHONE,
                title = "Microphone (optional)",
                body = "Voice input in Chat.",
                state = OnboardingPermissionState.NotRequested,
            ),
            OnboardingPermissionRow(
                id = PermissionIds.FOREGROUND,
                title = "Foreground service",
                body = "Keep the agent running while you switch apps.",
                state = OnboardingPermissionState.Auto,
            ),
            OnboardingPermissionRow(
                id = PermissionIds.STORAGE,
                title = "Storage (scoped)",
                body = "Save and import pipelines.",
                state = OnboardingPermissionState.Granted,
            ),
        ),
    )

    fun samples(): OnboardingViewState = OnboardingViewState(
        step = OnboardingStep.SamplePipelines,
        selectedSamples = setOf(OnboardingSample.LocalQa.id),
    )
}
