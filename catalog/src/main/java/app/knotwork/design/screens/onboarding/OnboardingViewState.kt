package app.knotwork.design.screens.onboarding

/**
 * Four-step pager state for the catalog `OnboardingContent` composable.
 * Mirrors the redesigned mockups (Phase 21 / Task 10, second pass).
 */
enum class OnboardingStep(val pageIndex: Int, val indicatorLabel: String) {
    /** Welcome / brand pitch. Step 1. */
    Welcome(pageIndex = 0, indicatorLabel = "Welcome"),

    /** Pick a LiteRT model to download — Gemma / Phi / Custom URL. Step 2. */
    LiteRtModel(pageIndex = 1, indicatorLabel = "Pick a model"),

    /** Optional cloud provider keys. Step 3. */
    CloudKeys(pageIndex = 2, indicatorLabel = "Optional cloud keys"),

    /** "You're ready" recap with the default pipeline preview. Step 4. */
    Ready(pageIndex = 3, indicatorLabel = "You're ready"),
}

/**
 * LiteRT model offered on step 2. The id is the stable key the
 * `:app/OnboardingViewModel` persists to settings; the human-facing
 * label, size, and recommended flag come from the catalog so the row
 * presentation stays self-contained.
 */
enum class OnboardingLiteRtModel(
    val id: String,
    val displayName: String,
    val sizeLabel: String,
    val recommended: Boolean,
) {
    /** Gemma 4 E2B — bundled starter, smallest footprint. */
    Gemma4E2B(
        id = "gemma_4_e2b",
        displayName = "Gemma 4 E2B",
        sizeLabel = "2.59 GB",
        recommended = true,
    ),

    /** Gemma 4 E4B — larger model with more headroom. */
    Gemma4E4B(
        id = "gemma_4_e4b",
        displayName = "Gemma 4 E4B",
        sizeLabel = "3.66 GB",
        recommended = false,
    ),

    /** User-supplied URL pointing at a `.litert` model bundle. */
    CustomUrl(
        id = "custom_url",
        displayName = "Custom URL…",
        sizeLabel = "paste",
        recommended = false,
    ),
}

/**
 * Cloud provider surfaced on step 3. The id matches
 * `domain.models.CloudProvider.wireKey` so the onboarding pick flows
 * straight into the settings-repository slot.
 */
enum class OnboardingCloudProvider(val id: String, val displayName: String) {
    OpenAI(id = "openai", displayName = "OpenAI"),
    Anthropic(id = "anthropic", displayName = "Anthropic"),
    Google(id = "google", displayName = "Google"),
    DeepSeek(id = "deepseek", displayName = "DeepSeek"),
    Ollama(id = "ollama", displayName = "Ollama (local network)"),
}

/**
 * Compact projection of the default pipeline rendered on step 4. The
 * names render as monospace chips; [accentNodeName] gets the
 * `NodeIfCondition`-green outline to distinguish the routing node from
 * the rest of the flow.
 */
data class OnboardingDefaultPipelinePreview(
    val nodes: List<String>,
    val nodeCount: Int,
    val edgeCount: Int,
    val accentNodeName: String? = null,
)

/**
 * Top-level immutable input to `OnboardingContent`.
 *
 * @property step current page index (0..3).
 * @property liteRtModel currently-selected model on step 2.
 * @property configuredCloudProviders ids of providers the user has
 * already configured (renders the "Configured" affordance instead of
 * "Configure").
 * @property defaultPipelinePreview compact recap of the bundled default
 * pipeline rendered on step 4. `null` is permitted for snapshot tests
 * that drive earlier steps.
 * @property downloadProgress current download progress on step 2.
 * `null` = idle (no download in flight), `0f..1f` = active download
 * (linear indicator overlays the picked row).
 * @property downloadError last download / load failure message, or
 * `null` when nothing has failed since the user last selected a model.
 * Rendered as an inline banner below the picker.
 * @property installedModelId stable catalog id of the model that is
 * already installed (either re-detected on entry or set after a
 * successful download). When non-`null` on step 2 the picked row shows
 * an "Installed" pill and the CTA becomes enabled.
 * @property customDownloadUrl user-supplied URL bound to the
 * `OnboardingLiteRtModel.CustomUrl` row; ignored for the bundled
 * presets.
 * @property isModelWarmed `true` once the host has loaded the model
 * into the inference engine. Gates the step-4 "Open chat" CTA so the
 * user cannot land in Chat with a cold handle.
 */
data class OnboardingViewState(
    val step: OnboardingStep = OnboardingStep.Welcome,
    val liteRtModel: OnboardingLiteRtModel = OnboardingLiteRtModel.Gemma4E2B,
    val configuredCloudProviders: Set<String> = emptySet(),
    val defaultPipelinePreview: OnboardingDefaultPipelinePreview? = null,
    val downloadProgress: Float? = null,
    val downloadError: String? = null,
    val installedModelId: String? = null,
    val customDownloadUrl: String = "",
    val isModelWarmed: Boolean = false,
) {
    /**
     * Primary CTA enablement matrix. The step-2 CTA serves three roles
     * depending on download state (the catalog renders distinct labels
     * for each), so enablement has to follow the matching state:
     *  - **Welcome / CloudKeys** — always enabled (cloud keys are
     *    optional);
     *  - **LiteRtModel — `Continue`** (model already on disk) — enabled
     *    so the user can advance to step 3;
     *  - **LiteRtModel — `Downloading…`** (download in flight) —
     *    disabled, the CTA acts as a non-clickable status indicator and
     *    re-tapping would be a no-op;
     *  - **LiteRtModel — `Download {name}`** (no install yet, no
     *    download in flight) — enabled for the bundled presets (the
     *    URL is known); for the `CustomUrl` row it depends on whether
     *    the user typed something in [customDownloadUrl];
     *  - **Ready** — enabled only after the host has warmed the
     *    inference handle, so chat will work on the first send.
     */
    val isPrimaryCtaEnabled: Boolean
        get() = when (step) {
            OnboardingStep.Welcome, OnboardingStep.CloudKeys -> true
            OnboardingStep.LiteRtModel -> when {
                installedModelId != null -> true
                downloadProgress != null -> false
                liteRtModel == OnboardingLiteRtModel.CustomUrl -> customDownloadUrl.isNotBlank()
                else -> true
            }
            OnboardingStep.Ready -> isModelWarmed
        }

    /** Convenience: are we on the final step? */
    val isFinalStep: Boolean get() = step == OnboardingStep.Ready
}

/**
 * Stable callback bundle accepted by `OnboardingContent`.
 */
@Suppress("LongParameterList") // Mirrors user-visible affordances; collapsing further hides intent.
class OnboardingCallbacks(
    val onNext: () -> Unit = {},
    val onBack: () -> Unit = {},
    val onSkip: () -> Unit = {},
    val onFinish: () -> Unit = {},
    val onLiteRtModelPick: (OnboardingLiteRtModel) -> Unit = {},
    val onConfigureCloudProvider: (OnboardingCloudProvider) -> Unit = {},
    /**
     * Invoked when the user taps the step-2 CTA to begin a model
     * download. The host (`OnboardingViewModel`) resolves the picked
     * model to its URL / filename and starts the download stream.
     */
    val onStartDownload: () -> Unit = {},
    /**
     * Invoked on every character change in the `Custom URL` input. The
     * host stores the latest value in `customDownloadUrl` and uses it
     * when [onStartDownload] fires for the `CustomUrl` row.
     */
    val onCustomDownloadUrlChanged: (String) -> Unit = {},
)

/** Convenience factory returning a callbacks bundle that ignores every event. */
fun noopOnboardingCallbacks(): OnboardingCallbacks = OnboardingCallbacks()
