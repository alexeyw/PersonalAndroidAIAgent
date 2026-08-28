package app.knotwork.design.screens.settings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.Density
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
 * Roborazzi baseline for the settings category sub-screens. Memory exercises the
 * full state matrix (Basic / Advanced / validation-error / pending re-embed /
 * provider-mismatch banner); Generation, Tools, Pipelines, Background, Privacy
 * and About cover breadth, with a font-scale 200% Tools board.
 *
 * The destructive typed-confirm dialog is intentionally omitted (its
 * `OutlinedTextField` cursor-blink animation trips Roborazzi's idle guard); that
 * flow is asserted at the VM / instrumented level instead.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], qualifiers = "w360dp-h760dp-xhdpi")
class SettingsCategorySnapshotTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun generation_basic_light() = snapshot("generation_basic", dark = false) {
        GenerationSettingsContent(state = SettingsPreview.generation())
    }

    @Test
    fun generation_advanced_light() = snapshot("generation_advanced", dark = false) {
        GenerationSettingsContent(state = SettingsPreview.generation(), advancedExpanded = true)
    }

    @Test
    fun models_default_light() = snapshot("models_default", dark = false) {
        ModelsSettingsContent(state = SettingsPreview.models())
    }

    @Test
    fun models_restart_required_light() = snapshot("models_restart", dark = false) {
        ModelsSettingsContent(state = SettingsPreview.modelsRestart())
    }

    @Test
    fun memory_basic_light() = snapshot("memory_basic", dark = false) {
        MemorySettingsContent(state = SettingsPreview.memory())
    }

    @Test
    fun memory_basic_dark() = snapshot("memory_basic", dark = true) {
        MemorySettingsContent(state = SettingsPreview.memory())
    }

    @Test
    fun memory_advanced_light() = snapshot("memory_advanced", dark = false) {
        MemorySettingsContent(state = SettingsPreview.memory(), advancedExpanded = true)
    }

    @Test
    fun memory_validation_error_light() = snapshot("memory_error", dark = false) {
        MemorySettingsContent(state = SettingsPreview.memoryError(), advancedExpanded = true)
    }

    @Test
    fun memory_pending_reembed_light() = snapshot("memory_pending", dark = false) {
        MemorySettingsContent(state = SettingsPreview.memoryPending(), advancedExpanded = true)
    }

    @Test
    fun memory_reembed_banner_light() = snapshot("memory_reembed_banner", dark = false) {
        MemorySettingsContent(state = SettingsPreview.memoryReembedBanner(), advancedExpanded = true)
    }

    @Test
    fun pipelines_advanced_light() = snapshot("pipelines_advanced", dark = false) {
        PipelinesSettingsContent(state = SettingsPreview.pipelines(), advancedExpanded = true)
    }

    @Test
    fun pipelines_advanced_dark() = snapshot("pipelines_advanced", dark = true) {
        PipelinesSettingsContent(state = SettingsPreview.pipelines(), advancedExpanded = true)
    }

    /**
     * The Basic tier on its own, which is where the run-limits entry row lives.
     * Previously the only Pipelines baseline had the Advanced disclosure open,
     * so the row a user actually lands on had no capture of its own.
     */
    @Test
    fun pipelines_basic_light() = snapshot("pipelines_basic", dark = false) {
        PipelinesSettingsContent(state = SettingsPreview.pipelines())
    }

    @Test
    fun pipelines_basic_dark() = snapshot("pipelines_basic", dark = true) {
        PipelinesSettingsContent(state = SettingsPreview.pipelines())
    }

    @Test
    fun tools_basic_light() = snapshot("tools_basic", dark = false) {
        ToolsSettingsContent(state = SettingsPreview.tools())
    }

    @Test
    fun tools_advanced_font_scale_2x_light() = snapshot("tools_advanced_font_scale_2x", dark = false, fontScale = 2f) {
        ToolsSettingsContent(state = SettingsPreview.tools(), advancedExpanded = true)
    }

    @Test
    fun background_advanced_light() = snapshot("background_advanced", dark = false) {
        BackgroundSettingsContent(state = SettingsPreview.background(), advancedExpanded = true)
    }

    // The three external-automation postures. Each is a different decision for
    // the reader — nothing can call in, something can call in but nothing will
    // run, something can call in and one pipeline will — so each gets a baseline
    // in both themes rather than only the working one.

    @Test
    fun background_external_off_light() = snapshot("background_external_off", dark = false) {
        BackgroundSettingsContent(state = SettingsPreview.background())
    }

    @Test
    fun background_external_off_dark() = snapshot("background_external_off", dark = true) {
        BackgroundSettingsContent(state = SettingsPreview.background())
    }

    @Test
    fun background_external_unbound_light() = snapshot("background_external_unbound", dark = false) {
        BackgroundSettingsContent(state = SettingsPreview.backgroundExternalUnbound())
    }

    @Test
    fun background_external_unbound_dark() = snapshot("background_external_unbound", dark = true) {
        BackgroundSettingsContent(state = SettingsPreview.backgroundExternalUnbound())
    }

    @Test
    fun background_external_bound_light() = snapshot("background_external_bound", dark = false) {
        BackgroundSettingsContent(state = SettingsPreview.backgroundExternalBound())
    }

    @Test
    fun background_external_bound_dark() = snapshot("background_external_bound", dark = true) {
        BackgroundSettingsContent(state = SettingsPreview.backgroundExternalBound())
    }

    @Test
    fun privacy_advanced_light() = snapshot("privacy_advanced", dark = false) {
        PrivacySettingsContent(state = SettingsPreview.privacy(), advancedExpanded = true)
    }

    @Test
    fun privacy_foss_hidden_light() = snapshot("privacy_foss_hidden", dark = false) {
        PrivacySettingsContent(state = SettingsPreview.privacyFossHidden(), advancedExpanded = true)
    }

    @Test
    fun about_advanced_light() = snapshot("about_advanced", dark = false) {
        AboutSettingsContent(state = SettingsPreview.about(), advancedExpanded = true)
    }

    @Test
    fun usage_populated_light() = snapshot("usage_populated", dark = false) {
        UsageTelemetryContent(state = SettingsPreview.usageTelemetry())
    }

    @Test
    fun usage_populated_dark() = snapshot("usage_populated", dark = true) {
        UsageTelemetryContent(state = SettingsPreview.usageTelemetry())
    }

    @Test
    fun usage_empty_light() = snapshot("usage_empty", dark = false) {
        UsageTelemetryContent(state = SettingsPreview.usageTelemetryEmpty())
    }

    // ─── Hints ───────────────────────────────────────────────────────────────
    // Closed testing found the settings screens unreadable — "Тут тумблеров" ·
    // "Я тут состарюсь" — and the explanations that did exist unread, because
    // they sat in the muted micro-type slot the app uses for machine state.
    // These baselines cover both halves of the answer: what the screen looks
    // like at rest (shorter than before, every prose subtitle gone) and what one
    // summoned explanation looks like.

    @Test
    fun memory_hints_collapsed_light() = snapshot("memory_hints_collapsed", dark = false) {
        MemorySettingsContent(state = SettingsPreview.memory(), advancedExpanded = true)
    }

    @Test
    fun memory_hints_collapsed_dark() = snapshot("memory_hints_collapsed", dark = true) {
        MemorySettingsContent(state = SettingsPreview.memory(), advancedExpanded = true)
    }

    @Test
    fun memory_hint_open_light() = snapshot("memory_hint_open", dark = false, openHint = LONG_HINT_ANCHOR) {
        MemorySettingsContent(state = SettingsPreview.memory(), advancedExpanded = true)
    }

    @Test
    fun memory_hint_open_dark() = snapshot("memory_hint_open", dark = true, openHint = LONG_HINT_ANCHOR) {
        MemorySettingsContent(state = SettingsPreview.memory(), advancedExpanded = true)
    }

    /** A toggle row's hint: the panel sits under the whole row, switch included. */
    @Test
    fun memory_hint_open_toggle_light() =
        snapshot("memory_hint_open_toggle", dark = false, openHint = "AUTO_EXTRACT_ENABLED") {
            MemorySettingsContent(state = SettingsPreview.memory())
        }

    /**
     * The worst case the 140-character ceiling was measured against: the longest
     * permitted explanation at the largest font scale. If a hint ever pushes the
     * row it explains off the top of the screen, it shows here first.
     */
    @Test
    fun memory_hint_open_font_scale_2x_light() = snapshot(
        "memory_hint_open_font_scale_2x",
        dark = false,
        fontScale = 2f,
        openHint = "AUTO_EXTRACT_ENABLED",
    ) {
        // The hint opened here is the FIRST row, not the longest one buried
        // under the Advanced disclosure: at 200 % a row that far down sits
        // below the capture frame, so the baseline would show the glyph
        // wrapping and prove nothing about the panel it opens. The panel has
        // to be inside the frame for this snapshot to be evidence.
        MemorySettingsContent(state = SettingsPreview.memory())
    }

    private fun snapshot(
        name: String,
        dark: Boolean,
        fontScale: Float = 1f,
        openHint: String? = null,
        content: @Composable () -> Unit,
    ) {
        composeTestRule.setContent {
            val baseDensity = LocalDensity.current
            // The hint controller has to be in scope or no row renders its help
            // glyph, and every one of these baselines would certify a screen
            // that looks complete while the control this task added is in none
            // of them. The fixture opens one hint when asked, so the expanded
            // panel is captured too, not just the collapsed affordance.
            val hints = remember { SettingsHintController { anchor -> SNAPSHOT_HINTS[anchor] } }
            LaunchedEffect(openHint) { if (openHint != null) hints.toggle(openHint) }
            KnotworkTheme(darkTheme = dark) {
                CompositionLocalProvider(
                    LocalKnotworkA11y provides FixedKnotworkA11y(reducedMotion = true, fontScale = fontScale),
                    LocalDensity provides Density(density = baseDensity.density, fontScale = fontScale),
                    LocalSettingsHints provides hints,
                ) { content() }
            }
        }
        val themeTag = if (dark) "dark" else "light"
        composeTestRule.onRoot().captureRoboImage(filePath = "src/test/snapshots/settings_${name}_$themeTag.png")
    }

    private companion object {
        /** The longest shipped explanation, so the panel is captured at its worst case. */
        const val LONG_HINT_ANCHOR = "MEMORY_SEARCH_THRESHOLD"

        /**
         * Help text for the snapshot fixtures. Held here rather than read from
         * the app module — the catalog does not depend on it — but kept
         * verbatim from the shipped strings so the baselines show real copy at
         * real length.
         */
        val SNAPSHOT_HINTS: Map<String, SettingsHint> = mapOf(
            "AUTO_EXTRACT_ENABLED" to SettingsHint(
                "On, durable facts from your chats — names, preferences, project details — are saved and reused later.",
            ),
            "MEMORY_COMPACTION_ENABLED" to SettingsHint(
                "Old memories get merged into shorter summaries once there are many. " +
                    "Frees room; loses the exact wording.",
            ),
            LONG_HINT_ANCHOR to SettingsHint(
                "Higher recalls only close matches, so less is pulled in; " +
                    "lower recalls more, including memories that miss the point.",
            ),
            "MEMORY_SEARCH_TOP_K" to SettingsHint(
                "How many memories are pulled into a reply at most. " +
                    "More context, slower start, and more room for noise.",
            ),
            "MAX_MEMORY_CHUNKS" to SettingsHint(
                "The ceiling on stored pieces. At the ceiling the oldest are dropped, compaction or not.",
            ),
            "AUTO_SUMMARIZE_THRESHOLD" to SettingsHint(
                "How full the context window gets before the thread is summarised. " +
                    "Lower summarises sooner and more often.",
            ),
            "MEMORY_RECENCY_HALF_LIFE_DAYS" to SettingsHint(
                "How fast old memories lose to new ones when both match. " +
                    "Short favours this week; long treats everything as current.",
            ),
            "MEMORY_COMPACTION_AGE_DAYS" to SettingsHint(
                "How old a memory must be before compaction may merge it. " +
                    "Fresher facts keep their exact wording until they pass it.",
            ),
        )
    }
}
