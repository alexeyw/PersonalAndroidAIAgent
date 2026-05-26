@file:Suppress("MatchingDeclarationName") // Hosts OnboardingContent and its 4 step composables.

package app.knotwork.design.screens.onboarding

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material.icons.outlined.Hub
import androidx.compose.material.icons.outlined.VpnKey
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import app.knotwork.design.R
import app.knotwork.design.components.buttons.KnotworkPrimaryButton
import app.knotwork.design.components.buttons.KnotworkTextButton
import app.knotwork.design.theme.KnotworkTheme
import app.knotwork.design.tokens.KnotworkPalette
import app.knotwork.design.tokens.KnotworkTextStyles

/** Height of the segmented progress bar shown above the bottom CTA. */
private val ProgressSegmentHeight = 4.dp

/** Width of the brand logo glyph rendered in the topbar. */
private val LogoIconSize = 24.dp

/** Diameter of the amber bullet rendered before each step-1 feature tile label. */
private val FeatureBulletSize = 8.dp

/** Diameter of the radio circle shown on each model row. */
private val RadioOuterSize = 22.dp

/** Diameter of the filled inner dot rendered when a model row is selected. */
private val RadioInnerSize = 10.dp

/** Border width of the unselected radio circle / the recommended pill / pipeline-node chip. */
private val OutlineBorderWidth = 1.dp

/** Diameter of the cloud-provider row's leading key icon. */
private val CloudIconSize = 20.dp

/** Height of the inline pipeline-node chip rendered in the step-4 recap. */
private val PipelineChipHeight = 28.dp

/** Multiplier turning a normalized download progress (`0f..1f`) into a percentage Int. */
private const val PERCENT_SCALE: Float = 100f

/**
 * Maximum effective `fontScale` honoured by the onboarding headlines.
 * Per `decisions.md §14`, the headline visual on the onboarding pager
 * is part of the spec, so above the system "Largest" preset (2.0×) the
 * type is clamped to 1.6× to keep the four-step pager from clipping
 * its CTA / progress segments off the bottom edge.
 */
private const val HEADLINE_FONT_SCALE_CLAMP: Float = 1.6f

/**
 * Threshold above which the reduced-motion fallback collapses the step-2
 * download bar to a static full-width fill instead of running the M3
 * `LinearProgressIndicator` stripe animation. Matches the task brief
 * "под reduced-motion — статичный full bar при `>= 0.99f`".
 */
private const val PROGRESS_FULL_BAR_THRESHOLD: Float = 0.99f

/**
 * Stateless Knotwork onboarding surface — renders one of four steps from
 * [OnboardingViewState.step]. The host (`:app/OnboardingScreen`) owns the
 * `HorizontalPager` if the swipe gesture is desired; the catalog stays
 * snapshot-deterministic by deriving the visible step from [state] alone.
 *
 * Layout (per the second-pass mockups, Phase 21 / Task 10):
 *  - Top bar: brand glyph + product title left, Skip link right.
 *  - Body: mono "0N · {label}" step indicator + headline + body + per-step
 *    content (welcome tiles / radio cards / cloud rows / pipeline preview).
 *  - Footer: 4 horizontal progress segments + a single full-width CTA
 *    whose label varies per step.
 *
 * @param state immutable view-state snapshot.
 * @param callbacks bundle of one-shot event handlers; defaults to no-op.
 * @param modifier optional layout modifier applied to the screen root.
 */
@Composable
fun OnboardingContent(
    state: OnboardingViewState,
    modifier: Modifier = Modifier,
    callbacks: OnboardingCallbacks = noopOnboardingCallbacks(),
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(color = MaterialTheme.colorScheme.surface),
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            OnboardingTopBar(state = state, callbacks = callbacks)
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = KnotworkTheme.spacing.sp4),
            ) {
                when (state.step) {
                    OnboardingStep.Welcome -> WelcomeStep(state = state)
                    OnboardingStep.LiteRtModel -> LiteRtModelStep(state = state, callbacks = callbacks)
                    OnboardingStep.CloudKeys -> CloudKeysStep(state = state, callbacks = callbacks)
                    OnboardingStep.Ready -> ReadyStep(state = state)
                }
            }
            OnboardingFooter(state = state, callbacks = callbacks)
        }
    }
}

@Composable
private fun OnboardingTopBar(state: OnboardingViewState, callbacks: OnboardingCallbacks) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(KnotworkTheme.spacing.sp2),
        modifier = Modifier
            .fillMaxWidth()
            .padding(
                start = KnotworkTheme.spacing.sp4,
                end = KnotworkTheme.spacing.sp4,
                top = KnotworkTheme.spacing.sp3,
                bottom = KnotworkTheme.spacing.sp3,
            ),
    ) {
        Icon(
            imageVector = Icons.Outlined.Hub,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(LogoIconSize),
        )
        Text(
            text = androidx.compose.ui.res.stringResource(R.string.knotwork_onboarding_brand_title),
            style = KnotworkTextStyles.TitleMd.copy(fontWeight = FontWeight.SemiBold),
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
        )
        // Skip is suppressed on the final step — the user commits via the
        // primary CTA there, not by skipping.
        if (state.step != OnboardingStep.Ready) {
            KnotworkTextButton(
                text = androidx.compose.ui.res.stringResource(R.string.knotwork_onboarding_skip),
                onClick = callbacks.onSkip,
            )
        }
    }
}

@Composable
private fun OnboardingFooter(state: OnboardingViewState, callbacks: OnboardingCallbacks) {
    Column(
        verticalArrangement = Arrangement.spacedBy(KnotworkTheme.spacing.sp4),
        modifier = Modifier
            .fillMaxWidth()
            .padding(
                horizontal = KnotworkTheme.spacing.sp4,
                vertical = KnotworkTheme.spacing.sp4,
            ),
    ) {
        ProgressSegments(currentStepIndex = state.step.pageIndex)
        val ctaLabel = when (state.step) {
            OnboardingStep.Welcome -> androidx.compose.ui.res.stringResource(
                R.string.knotwork_onboarding_continue,
            )
            OnboardingStep.LiteRtModel -> liteRtCtaLabel(state)
            OnboardingStep.CloudKeys -> androidx.compose.ui.res.stringResource(
                R.string.knotwork_onboarding_cloud_skip_cta,
            )
            OnboardingStep.Ready -> androidx.compose.ui.res.stringResource(
                R.string.knotwork_onboarding_ready_cta,
            )
        }
        val leadingIcon = if (state.step == OnboardingStep.Ready) {
            Icons.AutoMirrored.Outlined.ArrowForward
        } else {
            null
        }
        // Step 2 launches the download via `onStartDownload`; once the picked
        // model is installed the same button advances to step 3 via `onNext`.
        val ctaClick: () -> Unit = when {
            state.isFinalStep -> callbacks.onFinish
            state.step == OnboardingStep.LiteRtModel && state.installedModelId == null ->
                callbacks.onStartDownload
            else -> callbacks.onNext
        }
        KnotworkPrimaryButton(
            text = ctaLabel,
            onClick = ctaClick,
            enabled = state.isPrimaryCtaEnabled,
            leadingIcon = leadingIcon,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun ProgressSegments(currentStepIndex: Int) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(KnotworkTheme.spacing.sp2),
        modifier = Modifier.fillMaxWidth(),
    ) {
        OnboardingStep.entries.forEach { entry ->
            val filled = entry.pageIndex <= currentStepIndex
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(ProgressSegmentHeight)
                    .clip(KnotworkTheme.shapes.full)
                    .background(
                        color = if (filled) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            KnotworkTheme.extended.divider
                        },
                    ),
            )
        }
    }
}

@Composable
private fun StepIndicator(step: OnboardingStep) {
    val padded = "%02d".format(step.pageIndex + 1)
    Text(
        text = "$padded · ${step.indicatorLabel}",
        style = KnotworkTextStyles.MonoSm,
        color = KnotworkTheme.extended.onSurfaceMuted,
    )
}

@Composable
private fun StepHeadline(text: String) {
    // Per `decisions.md §14`, the onboarding headline visual is part of the
    // design spec; above 1.6× the layout starts pushing the CTA / progress
    // segments past the bottom edge. We clamp the effective `fontScale` to
    // 1.6× via an overridden [LocalDensity] for this subtree only — the
    // user's preference still applies up to that ceiling and every other
    // text in the flow keeps the unclamped value.
    //
    // Style choice: `TitleXl` (24sp) rather than `Display2xl` (30sp) so the
    // headline + body block does not eat half the viewport on smaller
    // phones. Matches the second-pass JSX mockup target of ~28sp at the
    // upper bound of the Knotwork title scale.
    val systemScale = KnotworkTheme.a11y.fontScale()
    val outer = LocalDensity.current
    val clampedScale = if (systemScale > HEADLINE_FONT_SCALE_CLAMP) {
        HEADLINE_FONT_SCALE_CLAMP
    } else {
        systemScale
    }
    CompositionLocalProvider(
        LocalDensity provides Density(density = outer.density, fontScale = clampedScale),
    ) {
        Text(
            text = text,
            style = KnotworkTextStyles.TitleXl,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

@Composable
private fun StepBody(text: String) {
    Text(
        text = text,
        style = KnotworkTextStyles.BodyBase,
        color = KnotworkTheme.extended.onSurfaceMuted,
    )
}

// ----------------------- Step 1 · Welcome ---------------------------------

@Composable
private fun WelcomeStep(state: OnboardingViewState) {
    Column(
        verticalArrangement = Arrangement.spacedBy(KnotworkTheme.spacing.sp3),
        modifier = Modifier.fillMaxSize(),
    ) {
        StepIndicator(step = state.step)
        StepHeadline(
            text = androidx.compose.ui.res.stringResource(R.string.knotwork_onboarding_welcome_headline),
        )
        StepBody(text = androidx.compose.ui.res.stringResource(R.string.knotwork_onboarding_welcome_body))
        Spacer(modifier = Modifier.height(KnotworkTheme.spacing.sp2))
        FeatureTile(
            label = androidx.compose.ui.res.stringResource(R.string.knotwork_onboarding_welcome_tile_litert),
        )
        FeatureTile(
            label = androidx.compose.ui.res.stringResource(R.string.knotwork_onboarding_welcome_tile_appfunctions),
        )
        FeatureTile(
            label = androidx.compose.ui.res.stringResource(R.string.knotwork_onboarding_welcome_tile_storage),
        )
    }
}

@Composable
private fun FeatureTile(label: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(KnotworkTheme.spacing.sp3),
        modifier = Modifier
            .fillMaxWidth()
            .clip(KnotworkTheme.shapes.md)
            .background(color = KnotworkTheme.extended.surface1)
            .padding(
                horizontal = KnotworkTheme.spacing.sp4,
                vertical = KnotworkTheme.spacing.sp3,
            ),
    ) {
        Box(
            modifier = Modifier
                .size(FeatureBulletSize)
                .background(color = MaterialTheme.colorScheme.primary, shape = CircleShape),
        )
        Text(
            text = label,
            style = KnotworkTextStyles.BodyBase,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

// ----------------------- Step 2 · LiteRT model ----------------------------

@Composable
private fun LiteRtModelStep(state: OnboardingViewState, callbacks: OnboardingCallbacks) {
    Column(
        verticalArrangement = Arrangement.spacedBy(KnotworkTheme.spacing.sp3),
        modifier = Modifier.fillMaxSize(),
    ) {
        StepIndicator(step = state.step)
        StepHeadline(
            text = androidx.compose.ui.res.stringResource(R.string.knotwork_onboarding_models_headline),
        )
        StepBody(text = androidx.compose.ui.res.stringResource(R.string.knotwork_onboarding_models_body))
        Spacer(modifier = Modifier.height(KnotworkTheme.spacing.sp2))
        OnboardingLiteRtModel.entries.forEach { model ->
            val isSelected = model == state.liteRtModel
            LiteRtModelRow(
                model = model,
                selected = isSelected,
                installed = state.installedModelId == model.id,
                downloadProgress = state.downloadProgress.takeIf { isSelected },
                onClick = { callbacks.onLiteRtModelPick(model) },
            )
            if (isSelected && model == OnboardingLiteRtModel.CustomUrl) {
                CustomUrlField(
                    value = state.customDownloadUrl,
                    onValueChange = callbacks.onCustomDownloadUrlChanged,
                )
            }
        }
        state.downloadError?.let { ErrorBanner(message = it) }
    }
}

@Composable
private fun LiteRtModelRow(
    model: OnboardingLiteRtModel,
    selected: Boolean,
    installed: Boolean,
    downloadProgress: Float?,
    onClick: () -> Unit,
) {
    // `Accent50` is a static palette colour that stays light in dark theme,
    // which collapses contrast against the `onSurface` (light) text. Route
    // through the theme-aware `primaryContainer` instead (Accent100 in
    // light, dark brown in dark) so the selected row keeps WCAG-AA
    // contrast in both themes.
    val containerColor = if (selected) {
        MaterialTheme.colorScheme.primaryContainer
    } else {
        KnotworkTheme.extended.surface1
    }
    val borderColor = if (selected) MaterialTheme.colorScheme.primary else Color.Transparent
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(KnotworkTheme.shapes.md)
            .background(color = containerColor)
            .border(width = OutlineBorderWidth, color = borderColor, shape = KnotworkTheme.shapes.md)
            .clickable(onClick = onClick)
            .padding(
                horizontal = KnotworkTheme.spacing.sp4,
                vertical = KnotworkTheme.spacing.sp3,
            ),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(KnotworkTheme.spacing.sp3),
        ) {
            RadioCircle(selected = selected)
            Column(
                verticalArrangement = Arrangement.spacedBy(KnotworkTheme.spacing.sp1),
                modifier = Modifier.weight(1f),
            ) {
                Text(
                    text = model.displayName,
                    style = KnotworkTextStyles.TitleMd.copy(fontWeight = FontWeight.SemiBold),
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = model.sizeLabel,
                    style = KnotworkTextStyles.MonoSm,
                    color = KnotworkTheme.extended.onSurfaceMuted,
                )
            }
            when {
                installed -> InstalledPill()
                model.recommended -> RecommendedPill()
            }
        }
        if (downloadProgress != null) {
            DownloadProgressIndicator(progress = downloadProgress)
        }
    }
}

@Composable
private fun DownloadProgressIndicator(progress: Float) {
    val clamped = progress.coerceIn(minimumValue = 0f, maximumValue = 1f)
    val reducedMotion = KnotworkTheme.a11y.reducedMotion()
    Column(
        verticalArrangement = Arrangement.spacedBy(KnotworkTheme.spacing.sp1),
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = KnotworkTheme.spacing.sp2),
    ) {
        // Under reduced-motion, M3's `LinearProgressIndicator` still draws
        // its determinate stripe with a 250 ms tween between progress
        // updates. The task brief requires a static full bar at `>= 0.99f`
        // — we collapse the indicator to a plain primary-filled `Box` so
        // the surface stops animating once the download completes.
        if (reducedMotion && clamped >= PROGRESS_FULL_BAR_THRESHOLD) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(ProgressSegmentHeight)
                    .clip(KnotworkTheme.shapes.full)
                    .background(color = MaterialTheme.colorScheme.primary),
            )
        } else {
            LinearProgressIndicator(
                progress = { clamped },
                color = MaterialTheme.colorScheme.primary,
                trackColor = KnotworkTheme.extended.divider,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        Text(
            text = androidx.compose.ui.res.stringResource(
                R.string.knotwork_onboarding_models_progress,
                (clamped * PERCENT_SCALE).toInt(),
            ),
            style = KnotworkTextStyles.MonoSm,
            color = KnotworkTheme.extended.onSurfaceMuted,
        )
    }
}

@Composable
private fun CustomUrlField(value: String, onValueChange: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        singleLine = true,
        label = {
            Text(text = androidx.compose.ui.res.stringResource(R.string.knotwork_onboarding_models_custom_url_label))
        },
        placeholder = {
            Text(
                text = androidx.compose.ui.res.stringResource(
                    R.string.knotwork_onboarding_models_custom_url_placeholder,
                ),
            )
        },
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = KnotworkTheme.spacing.sp2),
    )
}

@Composable
private fun ErrorBanner(message: String) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(KnotworkTheme.shapes.md)
            .background(color = MaterialTheme.colorScheme.errorContainer)
            .padding(
                horizontal = KnotworkTheme.spacing.sp4,
                vertical = KnotworkTheme.spacing.sp3,
            ),
    ) {
        Text(
            text = message,
            style = KnotworkTextStyles.BodyBase,
            color = MaterialTheme.colorScheme.onErrorContainer,
        )
    }
}

@Composable
private fun RadioCircle(selected: Boolean) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(RadioOuterSize)
            .border(
                width = OutlineBorderWidth + 0.5.dp,
                color = MaterialTheme.colorScheme.primary,
                shape = CircleShape,
            ),
    ) {
        if (selected) {
            Box(
                modifier = Modifier
                    .size(RadioInnerSize)
                    .background(color = MaterialTheme.colorScheme.primary, shape = CircleShape),
            )
        }
    }
}

@Composable
private fun InstalledPill() {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .clip(KnotworkTheme.shapes.sm)
            .border(
                width = OutlineBorderWidth,
                color = MaterialTheme.colorScheme.primary,
                shape = KnotworkTheme.shapes.sm,
            )
            .background(color = MaterialTheme.colorScheme.primary)
            .padding(
                horizontal = KnotworkTheme.spacing.sp2,
                vertical = KnotworkTheme.spacing.sp1,
            ),
    ) {
        Text(
            text = androidx.compose.ui.res.stringResource(R.string.knotwork_onboarding_models_installed),
            style = KnotworkTextStyles.LabelSm.copy(fontWeight = FontWeight.SemiBold),
            color = MaterialTheme.colorScheme.onPrimary,
        )
    }
}

@Composable
private fun liteRtCtaLabel(state: OnboardingViewState): String = when {
    state.downloadProgress != null -> androidx.compose.ui.res.stringResource(
        R.string.knotwork_onboarding_models_downloading_cta,
    )
    state.installedModelId != null -> androidx.compose.ui.res.stringResource(
        R.string.knotwork_onboarding_models_continue_cta,
    )
    else -> androidx.compose.ui.res.stringResource(
        R.string.knotwork_onboarding_models_download_cta,
        state.liteRtModel.displayName,
    )
}

@Composable
private fun RecommendedPill() {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .clip(KnotworkTheme.shapes.sm)
            .border(
                width = OutlineBorderWidth,
                color = MaterialTheme.colorScheme.primary,
                shape = KnotworkTheme.shapes.sm,
            )
            .padding(
                horizontal = KnotworkTheme.spacing.sp2,
                vertical = KnotworkTheme.spacing.sp1,
            ),
    ) {
        Text(
            text = androidx.compose.ui.res.stringResource(R.string.knotwork_onboarding_models_recommended),
            style = KnotworkTextStyles.LabelSm.copy(fontWeight = FontWeight.SemiBold),
            color = MaterialTheme.colorScheme.primary,
        )
    }
}

// ----------------------- Step 3 · Cloud keys ------------------------------

@Composable
private fun CloudKeysStep(state: OnboardingViewState, callbacks: OnboardingCallbacks) {
    Column(
        verticalArrangement = Arrangement.spacedBy(KnotworkTheme.spacing.sp3),
        modifier = Modifier.fillMaxSize(),
    ) {
        StepIndicator(step = state.step)
        StepHeadline(
            text = androidx.compose.ui.res.stringResource(R.string.knotwork_onboarding_cloud_headline),
        )
        StepBody(text = androidx.compose.ui.res.stringResource(R.string.knotwork_onboarding_cloud_body))
        Spacer(modifier = Modifier.height(KnotworkTheme.spacing.sp2))
        OnboardingCloudProvider.entries.forEach { provider ->
            CloudProviderRow(
                provider = provider,
                configured = provider.id in state.configuredCloudProviders,
                onConfigure = { callbacks.onConfigureCloudProvider(provider) },
            )
        }
    }
}

@Composable
private fun CloudProviderRow(provider: OnboardingCloudProvider, configured: Boolean, onConfigure: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(KnotworkTheme.spacing.sp3),
        modifier = Modifier
            .fillMaxWidth()
            .clip(KnotworkTheme.shapes.md)
            .background(color = KnotworkTheme.extended.surface1)
            .clickable(onClick = onConfigure)
            .padding(
                horizontal = KnotworkTheme.spacing.sp4,
                vertical = KnotworkTheme.spacing.sp3,
            ),
    ) {
        Icon(
            imageVector = Icons.Outlined.VpnKey,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.size(CloudIconSize),
        )
        Text(
            text = provider.displayName,
            style = KnotworkTextStyles.BodyBase.copy(fontWeight = FontWeight.SemiBold),
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = androidx.compose.ui.res.stringResource(
                if (configured) {
                    R.string.knotwork_onboarding_cloud_configured
                } else {
                    R.string.knotwork_onboarding_cloud_configure
                },
            ),
            style = KnotworkTextStyles.LabelLg.copy(fontWeight = FontWeight.SemiBold),
            color = MaterialTheme.colorScheme.primary,
        )
    }
}

// ----------------------- Step 4 · Ready -----------------------------------

@Composable
private fun ReadyStep(state: OnboardingViewState) {
    Column(
        verticalArrangement = Arrangement.spacedBy(KnotworkTheme.spacing.sp3),
        modifier = Modifier.fillMaxSize(),
    ) {
        StepIndicator(step = state.step)
        StepHeadline(
            text = androidx.compose.ui.res.stringResource(R.string.knotwork_onboarding_ready_headline),
        )
        StepBody(text = androidx.compose.ui.res.stringResource(R.string.knotwork_onboarding_ready_body))
        Spacer(modifier = Modifier.height(KnotworkTheme.spacing.sp2))
        ActiveModelRow(state = state)
        state.defaultPipelinePreview?.let { PipelinePreviewCard(preview = it) }
    }
}

@Composable
private fun ActiveModelRow(state: OnboardingViewState) {
    // The catalog projects `installedModelId` (a stable string id) back to the
    // human-facing display name through the enum lookup; the host fills the
    // pending state when warm-up hasn't completed yet.
    val installedDisplayName = state.installedModelId?.let { id ->
        OnboardingLiteRtModel.entries.firstOrNull { it.id == id }?.displayName
    }
    val pendingLabel = androidx.compose.ui.res.stringResource(
        R.string.knotwork_onboarding_ready_active_model_pending,
    )
    Column(
        verticalArrangement = Arrangement.spacedBy(KnotworkTheme.spacing.sp1),
        modifier = Modifier
            .fillMaxWidth()
            .clip(KnotworkTheme.shapes.md)
            .background(color = KnotworkTheme.extended.surface1)
            .padding(KnotworkTheme.spacing.sp4),
    ) {
        Text(
            text = androidx.compose.ui.res.stringResource(R.string.knotwork_onboarding_ready_active_model_label),
            style = KnotworkTextStyles.MonoSm,
            color = KnotworkTheme.extended.onSurfaceMuted,
        )
        Text(
            text = installedDisplayName ?: pendingLabel,
            style = KnotworkTextStyles.TitleMd.copy(fontWeight = FontWeight.SemiBold),
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

@Composable
private fun PipelinePreviewCard(preview: OnboardingDefaultPipelinePreview) {
    Column(
        verticalArrangement = Arrangement.spacedBy(KnotworkTheme.spacing.sp3),
        modifier = Modifier
            .fillMaxWidth()
            .clip(KnotworkTheme.shapes.md)
            .background(color = KnotworkTheme.extended.surface1)
            .padding(KnotworkTheme.spacing.sp4),
    ) {
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(KnotworkTheme.spacing.sp2),
            verticalArrangement = Arrangement.spacedBy(KnotworkTheme.spacing.sp2),
            modifier = Modifier.fillMaxWidth(),
        ) {
            preview.nodes.forEachIndexed { index, name ->
                PipelineNodeChip(name = name, accent = name == preview.accentNodeName)
                if (index < preview.nodes.lastIndex) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Outlined.ArrowForward,
                        contentDescription = null,
                        tint = KnotworkTheme.extended.onSurfaceMuted,
                        modifier = Modifier
                            .size(CloudIconSize)
                            .padding(top = KnotworkTheme.spacing.sp1),
                    )
                }
            }
        }
        Text(
            text = androidx.compose.ui.res.pluralStringResource(
                R.plurals.knotwork_onboarding_ready_preview_caption,
                preview.nodeCount,
                preview.nodeCount,
                preview.edgeCount,
            ),
            style = KnotworkTextStyles.MonoSm,
            color = KnotworkTheme.extended.onSurfaceMuted,
        )
    }
}

@Composable
private fun PipelineNodeChip(name: String, accent: Boolean) {
    val border = if (accent) KnotworkPalette.NodeIfCondition else MaterialTheme.colorScheme.primary
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .height(PipelineChipHeight)
            .clip(KnotworkTheme.shapes.sm)
            .border(width = OutlineBorderWidth, color = border, shape = KnotworkTheme.shapes.sm)
            .padding(
                horizontal = KnotworkTheme.spacing.sp2,
                vertical = KnotworkTheme.spacing.sp1,
            ),
    ) {
        Text(
            text = name,
            style = KnotworkTextStyles.MonoSm.copy(fontWeight = FontWeight.SemiBold),
            color = border,
        )
    }
}
