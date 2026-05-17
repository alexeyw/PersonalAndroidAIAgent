package app.knotwork.design.screens.onboarding

/**
 * Four-step pager state for the catalog `OnboardingContent` composable.
 * Mirrors `compose/screens/README.md §C5 · Onboarding` step ordering.
 */
enum class OnboardingStep(val pageIndex: Int) {
    /** Welcome / brand pitch. Step 1. */
    Welcome(pageIndex = 0),

    /** Pick a model source — local / cloud / skip. Step 2. */
    ModelSource(pageIndex = 1),

    /** Permissions advance-notice (lazy grants). Step 3. */
    Permissions(pageIndex = 2),

    /** Pick a sample pipeline. Step 4. */
    SamplePipelines(pageIndex = 3),
}

/** Model-source choice driven by step 2. */
enum class OnboardingModelSource {
    /** On-device LiteRT model. Default choice when no key is supplied. */
    LocalOnly,

    /** Cloud BYO-key — the user provides an API key. */
    Cloud,

    /** Defer the decision; user configures later in Settings → Models. */
    Skip,
}

/**
 * Permission grant state surfaced in step 3 rows. The platform request
 * itself is screen-layer responsibility — the catalog only owns the visual
 * state per row.
 */
enum class OnboardingPermissionState {
    /** No request has been made yet; row renders the `Grant now` CTA. */
    NotRequested,

    /** Platform granted the permission; row renders the `Granted` pill. */
    Granted,

    /** Platform always grants this permission on the target SDK floor. */
    Auto,
}

/**
 * Per-row state surfaced in step 3. Kept inside the catalog so previews
 * can build deterministic fixtures.
 */
data class OnboardingPermissionRow(
    val id: String,
    val title: String,
    val body: String,
    val state: OnboardingPermissionState,
)

/**
 * Per-card state for the step 4 sample-pipeline picker. Three samples land
 * in v0.1 — `LocalQa` is installable today; `WebResearch` and `CodeHelper`
 * stay disabled until the orchestrator integration ships the required
 * node executors.
 */
enum class OnboardingSample(val id: String, val unlocked: Boolean) {
    /** INPUT → LITE_RT → OUTPUT. Always installable. */
    LocalQa(id = "local_qa", unlocked = true),

    /** Requires the search tool node — disabled in v0.1. */
    WebResearch(id = "web_research", unlocked = false),

    /** Requires the shell tool node — disabled in v0.1. */
    CodeHelper(id = "code_helper", unlocked = false),
}

/**
 * Top-level immutable input to `OnboardingContent`. Mirrors
 * `compose/screens/README.md §C5`.
 *
 * @property step current page index (0..3).
 * @property modelSource currently-selected option in step 2; `null` until
 * the user picks one.
 * @property apiKey raw value of the inline API-key field shown when [modelSource]
 * is [OnboardingModelSource.Cloud]. Empty string = field present but no
 * input yet.
 * @property apiKeyError optional inline error rendered beneath the API-key
 * field. `null` while the field is valid or empty.
 * @property permissions ordered list of permission rows.
 * @property selectedSamples currently-installed sample ids; the chip on
 * each card flips between `Install` / `Installed` / `Coming soon`.
 */
data class OnboardingViewState(
    val step: OnboardingStep = OnboardingStep.Welcome,
    val modelSource: OnboardingModelSource? = null,
    val apiKey: String = "",
    val apiKeyError: String? = null,
    val permissions: List<OnboardingPermissionRow> = emptyList(),
    val selectedSamples: Set<String> = emptySet(),
) {
    /** Whether the primary CTA should be enabled in the current step. */
    val isPrimaryCtaEnabled: Boolean
        get() = when (step) {
            OnboardingStep.Welcome -> true
            OnboardingStep.ModelSource ->
                modelSource != null &&
                    !(modelSource == OnboardingModelSource.Cloud && apiKey.isNotBlank() && apiKeyError != null)
            OnboardingStep.Permissions -> true
            OnboardingStep.SamplePipelines -> true
        }

    /** Convenience: are we on the final step (Finish CTA visible)? */
    val isFinalStep: Boolean get() = step == OnboardingStep.SamplePipelines
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
    val onModelSourcePick: (OnboardingModelSource) -> Unit = {},
    val onApiKeyChange: (String) -> Unit = {},
    val onPermissionGrant: (String) -> Unit = {},
    val onSampleToggle: (String) -> Unit = {},
)

/** Convenience factory returning a callbacks bundle that ignores every event. */
fun noopOnboardingCallbacks(): OnboardingCallbacks = OnboardingCallbacks()
