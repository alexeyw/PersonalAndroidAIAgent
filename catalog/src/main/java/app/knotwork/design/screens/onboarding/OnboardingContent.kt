@file:Suppress("MatchingDeclarationName") // Hosts OnboardingContent and its 4 step composables.

package app.knotwork.design.screens.onboarding

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material.icons.outlined.Cloud
import androidx.compose.material.icons.outlined.Hub
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.SmartToy
import androidx.compose.material.icons.outlined.Storage
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import app.knotwork.design.R
import app.knotwork.design.components.buttons.KnotworkPrimaryButton
import app.knotwork.design.components.buttons.KnotworkTextButton
import app.knotwork.design.components.chips.KnotworkChip
import app.knotwork.design.components.chips.Status
import app.knotwork.design.components.chips.StatusPill
import app.knotwork.design.components.misc.StripedPlaceholder
import app.knotwork.design.theme.KnotworkTheme
import app.knotwork.design.tokens.KnotworkTextStyles

/** Height of the segmented progress bar shown above the bottom CTA. */
private val ProgressSegmentHeight = 4.dp

/** Width of the brand logo glyph rendered in the topbar. */
private val LogoIconSize = 24.dp

/** Diameter of the amber bullet rendered before each feature tile label. */
private val FeatureBulletSize = 8.dp

/** Minimum height of the model-source radio card; the card grows to fit when subtitles wrap. */
private val ModelCardMinHeight = 96.dp

/** Minimum height of one permission row. */
private val PermissionRowMinHeight = 72.dp

/** Height of one sample-pipeline card. */
private val SampleCardHeight = 160.dp

/** Width of the hero illustration slot on step 1. */
private val HeroHeight = 288.dp

/**
 * Stateless Knotwork onboarding surface — renders one of four steps from
 * [OnboardingViewState.step]. The host owns pager-state animation (using
 * `HorizontalPager` if it wants the swipe gesture); the catalog stays
 * snapshot-deterministic by deriving the visible step from [state] alone.
 *
 * Layout (per `compose/screens/README.md §C5`):
 *  - Top inset row: skip button (left, steps 2-4) + progress dots (right).
 *  - Centre: per-step body.
 *  - Bottom inset row: `Back` (hidden on step 1) + primary CTA
 *    (`Get started` / `Continue` / `Finish & open Chat`).
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
                    OnboardingStep.ModelSource -> ModelSourceStep(state = state, callbacks = callbacks)
                    OnboardingStep.Permissions -> PermissionsStep(state = state, callbacks = callbacks)
                    OnboardingStep.SamplePipelines -> SamplePipelinesStep(state = state, callbacks = callbacks)
                }
            }
            OnboardingFooter(state = state, callbacks = callbacks)
        }
    }
}

/**
 * Top bar with the brand glyph + product title on the left and a `Skip`
 * link on the right. Skip is visible on every step per the spec mockup —
 * the user can opt out of the flow at any time, including step 1.
 */
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
        androidx.compose.material3.Icon(
            imageVector = Icons.Outlined.Hub,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(LogoIconSize),
        )
        Text(
            text = stringResource(R.string.knotwork_onboarding_brand_title),
            style = KnotworkTextStyles.TitleMd.copy(
                fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold,
            ),
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
        )
        // We disable, but still render, the Skip on the final step — the
        // user is then committing via the primary CTA, not skipping.
        if (state.step != OnboardingStep.SamplePipelines) {
            KnotworkTextButton(
                text = stringResource(R.string.knotwork_onboarding_skip),
                onClick = callbacks.onSkip,
            )
        }
    }
}

/**
 * Footer block beneath the body content: 4-segment progress bar followed
 * by a single full-width primary CTA. The Back affordance from the
 * earlier dual-button layout is dropped per the new spec mockup — system
 * back / `onBack()` is still handled by the host, but the surface is no
 * longer cluttered with a secondary button.
 */
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
            OnboardingStep.SamplePipelines -> stringResource(R.string.knotwork_onboarding_finish)
            else -> stringResource(R.string.knotwork_onboarding_continue)
        }
        KnotworkPrimaryButton(
            text = ctaLabel,
            onClick = if (state.isFinalStep) callbacks.onFinish else callbacks.onNext,
            enabled = state.isPrimaryCtaEnabled,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

/**
 * 4-segment horizontal progress bar rendered above the bottom CTA. The
 * segment at [currentStepIndex] (and every prior segment) is filled with
 * the brand primary; remaining segments use the muted divider tone.
 */
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
private fun WelcomeStep(state: OnboardingViewState) {
    Column(
        verticalArrangement = Arrangement.spacedBy(KnotworkTheme.spacing.sp3),
        modifier = Modifier.fillMaxSize(),
    ) {
        StepIndicator(stepIndex = state.step.pageIndex, stepLabel = "Welcome")
        Text(
            text = stringResource(R.string.knotwork_onboarding_welcome_headline),
            style = KnotworkTextStyles.Display2xl,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Text(
            text = stringResource(R.string.knotwork_onboarding_welcome_body),
            style = KnotworkTextStyles.BodyLg,
            color = KnotworkTheme.extended.onSurfaceMuted,
        )
        Spacer(modifier = Modifier.height(KnotworkTheme.spacing.sp2))
        FeatureTile(label = stringResource(R.string.knotwork_onboarding_welcome_tile_litert))
        FeatureTile(label = stringResource(R.string.knotwork_onboarding_welcome_tile_appfunctions))
        FeatureTile(label = stringResource(R.string.knotwork_onboarding_welcome_tile_storage))
    }
}

/**
 * Monospace step indicator rendered as "0N · Label" above each step body.
 */
@Composable
private fun StepIndicator(stepIndex: Int, stepLabel: String) {
    val padded = "%02d".format(stepIndex + 1)
    Text(
        text = "$padded · $stepLabel",
        style = KnotworkTextStyles.MonoSm,
        color = KnotworkTheme.extended.onSurfaceMuted,
    )
}

/**
 * Full-width feature tile with an amber bullet on the left and a body
 * label. Used by the welcome step in place of outline chips so the
 * three privacy / capability statements read as distinct claims.
 */
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
                .background(
                    color = MaterialTheme.colorScheme.primary,
                    shape = CircleShape,
                ),
        )
        Text(
            text = label,
            style = KnotworkTextStyles.BodyBase,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

@Composable
private fun ModelSourceStep(state: OnboardingViewState, callbacks: OnboardingCallbacks) {
    Column(
        verticalArrangement = Arrangement.spacedBy(KnotworkTheme.spacing.sp3),
        modifier = Modifier.fillMaxSize(),
    ) {
        StepIndicator(stepIndex = state.step.pageIndex, stepLabel = "Models")
        Text(
            text = stringResource(R.string.knotwork_onboarding_models_headline),
            style = KnotworkTextStyles.TitleXl,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Text(
            text = stringResource(R.string.knotwork_onboarding_models_body),
            style = KnotworkTextStyles.BodyBase,
            color = KnotworkTheme.extended.onSurfaceMuted,
        )
        ModelSourceCard(
            icon = Icons.Outlined.SmartToy,
            iconTint = KnotworkTheme.extended.nodeLiteRt,
            title = stringResource(R.string.knotwork_onboarding_models_local_title),
            subtitle = stringResource(R.string.knotwork_onboarding_models_local_subtitle),
            selected = state.modelSource == OnboardingModelSource.LocalOnly,
            onClick = { callbacks.onModelSourcePick(OnboardingModelSource.LocalOnly) },
        )
        ModelSourceCard(
            icon = Icons.Outlined.Cloud,
            iconTint = KnotworkTheme.extended.nodeCloud,
            title = stringResource(R.string.knotwork_onboarding_models_cloud_title),
            subtitle = stringResource(R.string.knotwork_onboarding_models_cloud_subtitle),
            selected = state.modelSource == OnboardingModelSource.Cloud,
            onClick = { callbacks.onModelSourcePick(OnboardingModelSource.Cloud) },
        ) {
            if (state.modelSource == OnboardingModelSource.Cloud) {
                CloudApiKeyField(
                    value = state.apiKey,
                    error = state.apiKeyError,
                    onChange = callbacks.onApiKeyChange,
                )
            }
        }
        ModelSourceCard(
            icon = Icons.AutoMirrored.Outlined.ArrowForward,
            iconTint = KnotworkTheme.extended.onSurfaceMuted,
            title = stringResource(R.string.knotwork_onboarding_models_skip_title),
            subtitle = stringResource(R.string.knotwork_onboarding_models_skip_subtitle),
            selected = state.modelSource == OnboardingModelSource.Skip,
            onClick = { callbacks.onModelSourcePick(OnboardingModelSource.Skip) },
        )
    }
}

@Composable
@Suppress("LongParameterList") // Internal card; collapsing into a data class hurts call-site clarity.
private fun ModelSourceCard(
    icon: ImageVector,
    iconTint: Color,
    title: String,
    subtitle: String,
    selected: Boolean,
    onClick: () -> Unit,
    extraContent: @Composable () -> Unit = {},
) {
    val borderColor = if (selected) MaterialTheme.colorScheme.primary else KnotworkTheme.extended.outlineStrong
    Surface(
        shape = KnotworkTheme.shapes.md,
        color = KnotworkTheme.extended.surface1,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .border(width = 1.dp, color = borderColor, shape = KnotworkTheme.shapes.md),
    ) {
        Column(modifier = Modifier.padding(KnotworkTheme.spacing.sp3)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(KnotworkTheme.spacing.sp3),
                modifier = Modifier
                    .fillMaxWidth()
                    .defaultMinSize(minHeight = ModelCardMinHeight - KnotworkTheme.spacing.sp6),
            ) {
                androidx.compose.material3.Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = iconTint,
                    modifier = Modifier.size(KnotworkTheme.spacing.sp8),
                )
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(KnotworkTheme.spacing.sp1),
                ) {
                    Text(
                        text = title,
                        style = KnotworkTextStyles.TitleMd,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        text = subtitle,
                        style = KnotworkTextStyles.BodySm,
                        color = KnotworkTheme.extended.onSurfaceMuted,
                    )
                }
                StatusPill(status = if (selected) Status.Running else Status.Idle)
            }
            extraContent()
        }
    }
}

@Composable
private fun CloudApiKeyField(value: String, error: String?, onChange: (String) -> Unit) {
    Column(modifier = Modifier.padding(top = KnotworkTheme.spacing.sp2)) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(KnotworkTheme.shapes.sm)
                .background(color = KnotworkTheme.extended.surface2)
                .padding(KnotworkTheme.spacing.sp2),
        ) {
            BasicTextField(
                value = value,
                onValueChange = onChange,
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                textStyle = KnotworkTextStyles.MonoBase.copy(color = MaterialTheme.colorScheme.onSurface),
                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                modifier = Modifier.fillMaxWidth(),
            )
            if (value.isEmpty()) {
                Text(
                    text = "sk-…",
                    style = KnotworkTextStyles.MonoBase,
                    color = KnotworkTheme.extended.onSurfaceMuted,
                )
            }
        }
        if (error != null) {
            Text(
                text = error,
                style = KnotworkTextStyles.BodySm,
                color = KnotworkTheme.extended.signalError,
                modifier = Modifier.padding(top = KnotworkTheme.spacing.sp1),
            )
        }
    }
}

@Composable
private fun PermissionsStep(state: OnboardingViewState, callbacks: OnboardingCallbacks) {
    Column(
        verticalArrangement = Arrangement.spacedBy(KnotworkTheme.spacing.sp3),
        modifier = Modifier.fillMaxSize(),
    ) {
        StepIndicator(stepIndex = state.step.pageIndex, stepLabel = "Permissions")
        Text(
            text = stringResource(R.string.knotwork_onboarding_permissions_headline),
            style = KnotworkTextStyles.TitleXl,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Text(
            text = stringResource(R.string.knotwork_onboarding_permissions_body),
            style = KnotworkTextStyles.BodyBase,
            color = KnotworkTheme.extended.onSurfaceMuted,
        )
        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(KnotworkTheme.spacing.sp2),
            contentPadding = PaddingValues(vertical = KnotworkTheme.spacing.sp1),
            modifier = Modifier.fillMaxWidth(),
        ) {
            items(items = state.permissions, key = { it.id }) { row ->
                PermissionRow(row = row, onGrant = { callbacks.onPermissionGrant(row.id) })
            }
        }
    }
}

@Composable
private fun PermissionRow(row: OnboardingPermissionRow, onGrant: () -> Unit) {
    val icon = when (row.id) {
        PermissionIds.NOTIFICATIONS -> Icons.Outlined.Notifications
        PermissionIds.MICROPHONE -> Icons.Outlined.Mic
        PermissionIds.FOREGROUND -> Icons.Outlined.Lock
        else -> Icons.Outlined.Storage
    }
    Surface(
        shape = KnotworkTheme.shapes.md,
        color = KnotworkTheme.extended.surface1,
        modifier = Modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = PermissionRowMinHeight),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(KnotworkTheme.spacing.sp3),
            modifier = Modifier.fillMaxWidth().padding(horizontal = KnotworkTheme.spacing.sp3),
        ) {
            androidx.compose.material3.Icon(
                imageVector = icon,
                contentDescription = null,
                tint = KnotworkTheme.extended.onSurfaceMuted,
                modifier = Modifier.size(KnotworkTheme.spacing.sp6),
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = row.title,
                    style = KnotworkTextStyles.TitleMd,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = row.body,
                    style = KnotworkTextStyles.BodySm,
                    color = KnotworkTheme.extended.onSurfaceMuted,
                )
            }
            when (row.state) {
                OnboardingPermissionState.NotRequested -> KnotworkTextButton(
                    text = stringResource(R.string.knotwork_onboarding_permissions_grant),
                    onClick = onGrant,
                )
                OnboardingPermissionState.Granted -> StatusPill(status = Status.Success)
                OnboardingPermissionState.Auto -> StatusPill(status = Status.Idle)
            }
        }
    }
}

@Composable
private fun SamplePipelinesStep(state: OnboardingViewState, callbacks: OnboardingCallbacks) {
    Column(
        verticalArrangement = Arrangement.spacedBy(KnotworkTheme.spacing.sp3),
        modifier = Modifier.fillMaxSize(),
    ) {
        StepIndicator(stepIndex = state.step.pageIndex, stepLabel = "Samples")
        Text(
            text = stringResource(R.string.knotwork_onboarding_samples_headline),
            style = KnotworkTextStyles.TitleXl,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Text(
            text = stringResource(R.string.knotwork_onboarding_samples_body),
            style = KnotworkTextStyles.BodyBase,
            color = KnotworkTheme.extended.onSurfaceMuted,
        )
        OnboardingSample.entries.forEach { sample ->
            val installed = sample.id in state.selectedSamples
            SampleCard(
                title = when (sample) {
                    OnboardingSample.LocalQa -> stringResource(R.string.knotwork_onboarding_samples_local_qa)
                    OnboardingSample.WebResearch -> stringResource(R.string.knotwork_onboarding_samples_web_research)
                    OnboardingSample.CodeHelper -> stringResource(R.string.knotwork_onboarding_samples_code_helper)
                },
                installed = installed,
                unlocked = sample.unlocked,
                onToggle = { callbacks.onSampleToggle(sample.id) },
            )
        }
    }
}

@Composable
private fun SampleCard(title: String, installed: Boolean, unlocked: Boolean, onToggle: () -> Unit) {
    Surface(
        shape = KnotworkTheme.shapes.md,
        color = KnotworkTheme.extended.surface1,
        modifier = Modifier
            .fillMaxWidth()
            .height(SampleCardHeight),
    ) {
        Column(
            verticalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.fillMaxSize().padding(KnotworkTheme.spacing.sp3),
        ) {
            Text(
                text = title,
                style = KnotworkTextStyles.TitleMd,
                color = MaterialTheme.colorScheme.onSurface,
            )
            // Mini-graph placeholder strip — node chips drawn as a striped illustration.
            StripedPlaceholder(modifier = Modifier.fillMaxWidth().height(KnotworkTheme.spacing.sp10))
            val chipLabel = when {
                !unlocked -> stringResource(R.string.knotwork_onboarding_samples_coming_soon)
                installed -> stringResource(R.string.knotwork_onboarding_samples_installed)
                else -> stringResource(R.string.knotwork_onboarding_samples_install)
            }
            KnotworkChip(
                label = chipLabel,
                selected = installed && unlocked,
                enabled = unlocked,
                onClick = if (unlocked) onToggle else null,
            )
        }
    }
}

/** Stable identifiers for the permission rows surfaced in step 3. */
object PermissionIds {
    /** Notifications permission id. */
    const val NOTIFICATIONS: String = "notifications"

    /** Microphone permission id. */
    const val MICROPHONE: String = "microphone"

    /** Foreground service permission id. */
    const val FOREGROUND: String = "foreground"

    /** Scoped storage permission id. */
    const val STORAGE: String = "storage"
}
