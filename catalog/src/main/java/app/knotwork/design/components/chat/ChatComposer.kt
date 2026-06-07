@file:Suppress(
    "MatchingDeclarationName", // File hosts both `ComposerState` (sealed interface) and the `ChatComposer` composable.
)

package app.knotwork.design.components.chat

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.knotwork.design.R
import app.knotwork.design.icons.AppIcons
import app.knotwork.design.theme.KnotworkTheme
import app.knotwork.design.tokens.KnotworkTextStyles

/**
 * Composer state machine — drives the send / stop morph, the input enabled
 * state, and the inline error banner.
 *
 */
sealed interface ComposerState {

    /** Default. User can type and submit. */
    data object Idle : ComposerState

    /**
     * Assistant is currently generating; the send affordance morphs into a
     * stop button (200 ms cross-fade) and tapping it cancels the run. The
     * input field accepts text so the user can queue the next prompt.
     */
    data object Generating : ComposerState

    /**
     * Last submission errored. An inline banner renders above the input
     * showing [message]; the send affordance returns to its idle visual.
     *
     * @property message user-visible error description.
     */
    data class Error(val message: String) : ComposerState
}

/**
 * Knotwork chat composer — pill-shaped multiline input + circular brand
 * action button.
 *
 * Visual contract:
 *  - A single pill (`KnotworkTheme.shapes.full`) on `extended.surface1` hosts
 *    the borderless input on the left and a circular filled brand-color
 *    action button on the right.
 *  - The action button morphs Send ↔ Stop via a 200 ms `AnimatedContent`
 *    crossfade; reduced motion collapses the crossfade to instant.
 *  - In `Idle` / `Error` the button shows the paper-plane `Send` icon and
 *    fires [onSend]. In `Generating` it shows `Pause` and fires [onStop].
 *  - When [state] is [ComposerState.Error], an inline banner with
 *    `signalError` accent renders above the pill.
 *
 * **Stateless** — `value` is hoisted to the caller; this composable never
 * stores text. The screen ViewModel owns persistence and history.
 *
 * Trailing action-button state matrix:
 *
 * | composer state                                  | icon  | tint     |
 * |-------------------------------------------------|-------|----------|
 * | [ComposerState.Idle] && value empty && onMic≠null | mic   | surface3 |
 * | [ComposerState.Idle] && value non-empty (or no onMic) | send  | primary  |
 * | [ComposerState.Generating]                       | stop  | primary  |
 * | [ComposerState.Error]                            | retry | error    |
 *
 * @param value current input value.
 * @param onValueChange invoked with each keystroke.
 * @param onSend invoked when the user taps the action button in Idle (with value) / Error.
 * @param onStop invoked when the user taps the action button in Generating.
 * @param state current state of the composer (drives morph + error banner).
 * @param modifier optional layout modifier applied to the composer root.
 * @param onMic optional voice-input handler. When non-null **and** the
 *  composer is [ComposerState.Idle] with an empty [value], the trailing
 *  button shows a microphone icon on the muted `surface3` palette so the
 *  affordance reads as "press to talk" rather than "press to send". Pass
 *  `null` to suppress the mic state (the button stays on Send / disabled).
 */
@Composable
@Suppress("LongParameterList")
fun ChatComposer(
    value: String,
    onValueChange: (String) -> Unit,
    onSend: () -> Unit,
    onStop: () -> Unit,
    state: ComposerState,
    modifier: Modifier = Modifier,
    onMic: (() -> Unit)? = null,
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(KnotworkTheme.spacing.sp2),
        modifier = modifier
            .fillMaxWidth()
            .background(color = MaterialTheme.colorScheme.surface)
            .padding(KnotworkTheme.spacing.sp3),
    ) {
        if (state is ComposerState.Error) {
            ErrorBanner(message = state.message)
        }
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(KnotworkTheme.spacing.sp2),
            modifier = Modifier
                .fillMaxWidth()
                .clip(KnotworkTheme.shapes.full)
                .background(color = KnotworkTheme.extended.surface1)
                .padding(
                    start = KnotworkTheme.spacing.sp4,
                    end = KnotworkTheme.spacing.sp1,
                    top = KnotworkTheme.spacing.sp1,
                    bottom = KnotworkTheme.spacing.sp1,
                ),
        ) {
            ComposerInput(
                value = value,
                onValueChange = onValueChange,
                modifier = Modifier.weight(1f),
            )
            ActionButton(
                state = state,
                value = value,
                onSend = onSend,
                onStop = onStop,
                onMic = onMic,
            )
        }
    }
}

/**
 * Borderless multiline text field used inside the composer pill. Wraps a
 * [BasicTextField] (instead of `OutlinedTextField`) so the input visually
 * dissolves into the pill background — only the placeholder + caret + text
 * are visible, no outline or container.
 */
@Composable
private fun ComposerInput(value: String, onValueChange: (String) -> Unit, modifier: Modifier = Modifier) {
    val placeholder = stringResource(R.string.knotwork_composer_placeholder)
    val textStyle: TextStyle = KnotworkTextStyles.BodyBase.copy(
        color = MaterialTheme.colorScheme.onSurface,
    )
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        textStyle = textStyle,
        cursorBrush = androidx.compose.ui.graphics.SolidColor(MaterialTheme.colorScheme.primary),
        maxLines = COMPOSER_MAX_VISIBLE_LINES,
        modifier = modifier
            .heightIn(min = COMPOSER_INPUT_MIN_HEIGHT)
            .wrapContentHeight(Alignment.CenterVertically),
        decorationBox = { innerTextField ->
            Box(contentAlignment = Alignment.CenterStart) {
                if (value.isEmpty()) {
                    Text(
                        text = placeholder,
                        style = KnotworkTextStyles.BodyBase,
                        color = KnotworkTheme.extended.onSurfaceDim,
                    )
                }
                innerTextField()
            }
        },
    )
}

/** Maximum visible rows in the composer before internal scroll kicks in. */
private const val COMPOSER_MAX_VISIBLE_LINES = 6

/**
 * Minimum height of the composer input row. Picked so the pill stays at the
 * canonical 48 dp visual height when the action button (48 dp circle)
 * dominates vertical layout — keeps the placeholder centered.
 */
private val COMPOSER_INPUT_MIN_HEIGHT = 40.dp

/**
 * Trailing action button — 200 ms cross-fades between mic / send / stop /
 * retry depending on the [ComposerState] × [value] cross-product. The
 * morph respects reduced motion (zero-duration fade when on).
 */
@Composable
@Suppress("LongParameterList")
private fun ActionButton(
    state: ComposerState,
    value: String,
    onSend: () -> Unit,
    onStop: () -> Unit,
    onMic: (() -> Unit)?,
) {
    val reducedMotion = KnotworkTheme.a11y.reducedMotion()
    val durationMs = if (reducedMotion) 0 else COMPOSER_MORPH_MS
    val target = resolveActionTarget(state = state, value = value, onMic = onMic)
    AnimatedContent(
        targetState = target,
        transitionSpec = {
            fadeIn(androidx.compose.animation.core.tween(durationMs)) togetherWith
                fadeOut(androidx.compose.animation.core.tween(durationMs))
        },
        label = "composer_action_morph",
    ) { current ->
        val icon: ImageVector
        val descriptionRes: Int
        val onClick: () -> Unit
        val container: Color
        val content: Color
        when (current) {
            ActionTarget.Mic -> {
                icon = AppIcons.Mic
                descriptionRes = R.string.knotwork_composer_mic
                onClick = onMic ?: {}
                container = KnotworkTheme.extended.surface3
                content = MaterialTheme.colorScheme.onSurface
            }
            ActionTarget.Send -> {
                icon = AppIcons.Send
                descriptionRes = R.string.knotwork_composer_send
                onClick = onSend
                container = MaterialTheme.colorScheme.primary
                content = MaterialTheme.colorScheme.onPrimary
            }
            ActionTarget.Stop -> {
                icon = AppIcons.Pause
                descriptionRes = R.string.knotwork_composer_stop
                onClick = onStop
                container = MaterialTheme.colorScheme.primary
                content = MaterialTheme.colorScheme.onPrimary
            }
            ActionTarget.Retry -> {
                icon = AppIcons.Refresh
                descriptionRes = R.string.knotwork_composer_send
                onClick = onSend
                container = KnotworkTheme.extended.riskDestructive
                content = MaterialTheme.colorScheme.onPrimary
            }
        }
        ComposerActionButton(
            icon = icon,
            contentDescription = stringResource(descriptionRes),
            onClick = onClick,
            container = container,
            content = content,
        )
    }
}

/** Discrete target state for the trailing action button. */
private enum class ActionTarget { Mic, Send, Stop, Retry }

private fun resolveActionTarget(state: ComposerState, value: String, onMic: (() -> Unit)?): ActionTarget =
    when (state) {
        is ComposerState.Generating -> ActionTarget.Stop
        is ComposerState.Error -> ActionTarget.Retry
        is ComposerState.Idle -> if (value.isEmpty() && onMic != null) ActionTarget.Mic else ActionTarget.Send
    }

/** Diameter of the circular send / stop action button (matches Knotwork primary-button visual height). */
private val COMPOSER_ACTION_BUTTON_SIZE = 48.dp

/** Size of the glyph rendered inside the circular action button. */
private val COMPOSER_ACTION_ICON_SIZE = 20.dp

/**
 * Circular filled brand-color action button used inside the composer pill
 * for Send / Stop. Stays at 48 × 48 so the touch target meets a11y minimums
 * even though the visual is a tight circle.
 */
@Composable
@Suppress("LongParameterList")
private fun ComposerActionButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    container: Color,
    content: Color,
) {
    val interactionSource = remember { MutableInteractionSource() }
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .minimumInteractiveComponentSize()
            .size(COMPOSER_ACTION_BUTTON_SIZE)
            .clip(CircleShape)
            .background(color = container)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                role = Role.Button,
                onClick = onClick,
            )
            .semantics {
                this.contentDescription = contentDescription
                this.role = Role.Button
            },
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = content,
            modifier = Modifier.size(COMPOSER_ACTION_ICON_SIZE),
        )
    }
}

/** Duration of the send ↔ stop cross-fade. */
private const val COMPOSER_MORPH_MS = 200

/** Inline error banner stacked above the input row. */
@Composable
private fun ErrorBanner(message: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(KnotworkTheme.spacing.sp2),
        modifier = Modifier
            .fillMaxWidth()
            .background(
                color = KnotworkTheme.extended.signalError.copy(alpha = ERROR_BANNER_BG_ALPHA),
                shape = KnotworkTheme.shapes.sm,
            )
            .padding(
                horizontal = KnotworkTheme.spacing.sp3,
                vertical = KnotworkTheme.spacing.sp2,
            ),
    ) {
        Icon(
            imageVector = AppIcons.AlertCircle,
            contentDescription = null,
            tint = KnotworkTheme.extended.signalError,
        )
        Text(
            text = message,
            style = KnotworkTextStyles.BodySm,
            color = errorBannerForeground(),
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** Alpha applied to the error banner background tint. */
private const val ERROR_BANNER_BG_ALPHA = 0.12f

/** Foreground colour for the error banner label — keeps body legible on the tinted strip. */
@Composable
private fun errorBannerForeground(): Color = MaterialTheme.colorScheme.onSurface
