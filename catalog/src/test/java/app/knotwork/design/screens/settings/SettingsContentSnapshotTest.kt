package app.knotwork.design.screens.settings

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
 * Roborazzi baseline for the Phase 22 / Task 9 redesigned Settings
 * surface. State matrix mirrors `compose/screens/README.md §C7` post-
 * redesign: Loading / Default / RestartRequired / DestructiveAction
 * (typed-input idle + typed-input yes) × Light/Dark.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], qualifiers = "w360dp-h760dp-xhdpi")
class SettingsContentSnapshotTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun settings_loading_light() = snapshot("loading", dark = false) {
        SettingsContent(state = SettingsPreview.loading())
    }

    @Test
    fun settings_default_light() = snapshot("default", dark = false) {
        SettingsContent(state = SettingsPreview.default())
    }

    @Test
    fun settings_default_dark() = snapshot("default", dark = true) {
        SettingsContent(state = SettingsPreview.default())
    }

    @Test
    fun settings_restart_required_light() = snapshot("restart_required", dark = false) {
        SettingsContent(state = SettingsPreview.restartRequired())
    }

    @Test
    fun settings_restart_required_dark() = snapshot("restart_required", dark = true) {
        SettingsContent(state = SettingsPreview.restartRequired())
    }

    // NOTE — destructive typed-confirm snapshots were intentionally omitted from
    // this baseline pass: AlertDialog + OutlinedTextField triggers a
    // continuously-recomposing cursor-blink animation that Roborazzi's
    // `AppNotIdleException` guard rejects. The behaviour is asserted at the
    // VM level instead (SettingsViewModelTest.confirmDestructive_*); a
    // future iteration can swap the dialog body for a stateless `Text`
    // composable to capture a static snapshot.

    private fun snapshot(name: String, dark: Boolean, content: @Composable () -> Unit) {
        composeTestRule.setContent {
            CompositionLocalProvider(LocalKnotworkA11y provides FixedKnotworkA11y(reducedMotion = true)) {
                KnotworkTheme(darkTheme = dark) { content() }
            }
        }
        val themeTag = if (dark) "dark" else "light"
        composeTestRule.onRoot().captureRoboImage(
            filePath = "src/test/snapshots/settings_${name}_$themeTag.png",
        )
    }
}

/**
 * Internal preview fixtures backing the Settings snapshot suite.
 */
internal object SettingsPreview {
    private fun identity(): IdentityCardState = IdentityCardState(
        displayName = "Anonymous · this device",
        avatarInitials = "AA",
        metaLine = "device-id 4f3a-92d1 · keys in Android Keystore",
    )

    private fun systemInstructions(): SystemInstructionsCardState = SystemInstructionsCardState(
        value = "Be concise. Prefer bullet points over prose. " +
            "Use \$DATE for any date reference and \$LANG for the user's language.",
        placeholder = "Be concise.",
        variableChips = listOf("\$DATE", "\$TIME", "\$LANG", "\$LOCATION", "\$USER", "\$DEVICE"),
        characterCount = 218,
        characterLimit = 4_000,
        approximateTokens = 62,
        helperText = "Prepended to every system prompt the agent sends.",
        validationError = null,
    )

    private fun restrictions(): RestrictionsCardState = RestrictionsCardState(
        approveSelection = ApproveToolCallsOption.Sensitive,
        approveAllLabel = "All",
        approveSensitiveLabel = "Sensitive +",
        approveNeverLabel = "Never",
        blockDestructive = true,
        blockDestructiveSubtitle = "schedule_task · delegate_task · file write — a…",
        blockNetwork = true,
        blockNetworkSubtitle = "LiteRT runs offline · cloud providers gated se…",
        capSteps = 20,
        capStepsSubtitle = "Pause and ask for guidance after N planner cycles.",
    )

    private fun llmParameters(): LlmParametersCardState = LlmParametersCardState(
        sliders = listOf(
            LlmParameterSlider("temperature", "Temperature", "0.7", 0.7f, 0f..2f),
            LlmParameterSlider("top_k", "Top-K", "40", 40f, 1f..100f, steps = 99),
            LlmParameterSlider("top_p", "Top-P", "0.90", 0.9f, 0f..1f),
            LlmParameterSlider("repetition_penalty", "Repetition penalty", "1.10", 1.10f, 1f..2f),
            LlmParameterSlider("max_context", "Max context", "4096 tok", 4096f, 512f..8192f, steps = 14),
            LlmParameterSlider("max_steps", "Max steps", "20", 20f, 5f..100f, steps = 94),
        ),
    )

    private fun localModel(): LocalModelCardState = LocalModelCardState(
        modelName = "gemma-2b-it-q4",
        metaLine = "1.4 GB · 2 048 ctx · Q4_K_M · downloaded 12 May",
        backendLabel = "NPU (QNN) · auto-fallback to GPU then CPU.",
        backendOptions = listOf("NPU · auto", "GPU", "CPU"),
        selectedBackend = "NPU · auto",
        testProbeText = "Last probe · 248 tok in 1.42 s · 174 tok/s",
        testProbeIsError = false,
    )

    private fun providers(): ExternalProvidersCardState = ExternalProvidersCardState(
        rows = listOf(
            ProviderRowState("openai", "OpenAI", "sk-…3a9f", "gpt-4o-mini", null, false),
            ProviderRowState("anthropic", "Anthropic", "sk-ant-…b21c", "claude-sonnet-4", null, false),
            ProviderRowState("google", "Google", null, null, null, false),
            ProviderRowState("deepseek", "DeepSeek", null, null, null, false),
            ProviderRowState(
                "ollama",
                "Ollama",
                "192.168.1.42:11434",
                "mistral-7b · 4096",
                "192.168.1.42:11434",
                true,
            ),
        ),
    )

    private fun memory(): MemoryCardState = MemoryCardState(
        stats = listOf(
            MemoryStatCell("Chunks", "1 248"),
            MemoryStatCell("Size", "14.2 MB"),
            MemoryStatCell("Threads", "38"),
            MemoryStatCell("Avg score", "0.74"),
        ),
        autoExtractEnabled = true,
        autoExtractLabel = "Auto-extract from conversations",
        autoExtractSubtitle = "Saves durable facts to memory after each chat",
        autoSummarizeThreshold = 80,
        autoSummarizeLabel = "Auto-summarize threshold",
        embeddingTitle = "Embedding model",
        embeddingSubtitle = "bge-small-en-v1.5 · 384 dim · on-device",
        exportLabel = "Export base",
        reembedLabel = "Re-embed",
        clearLabel = "Clear",
        reembedProgressPercent = null,
    )

    private fun notifications(): NotificationsCardState = NotificationsCardState(longRunningEnabled = true)

    private fun privacy(): PrivacyCardState = PrivacyCardState(crashReportingEnabled = false)

    fun loading(): SettingsViewState = SettingsViewState(
        visualState = SettingsVisualState.Loading,
        subtitleVersion = "0.9.2",
        subtitleChannel = "alpha",
        subtitleBuildDate = "2026.05.18",
        identity = null,
        systemInstructions = systemInstructions(),
        restrictions = restrictions(),
        llmParameters = llmParameters(),
        localModel = localModel(),
        externalProviders = providers(),
        memory = memory(),
        notifications = notifications(),
        privacy = privacy(),
    )

    fun default(): SettingsViewState = loading().copy(
        visualState = SettingsVisualState.Default,
        identity = identity(),
    )

    fun restartRequired(): SettingsViewState = default().copy(
        visualState = SettingsVisualState.RestartRequired,
        restartRequiredMessage = "Backend change requires restart.",
    )

    fun destructiveTyped(pending: String): SettingsViewState = default().copy(
        visualState = SettingsVisualState.DestructiveAction,
        destructiveAction = DestructiveActionState(
            title = "Clear all memory?",
            body = "This deletes every memory chunk on this device — including pinned entries.",
            keyword = "yes",
            hint = "Type yes to confirm",
            pendingInput = pending,
            kind = DestructiveActionKind.ClearMemory,
        ),
    )
}
