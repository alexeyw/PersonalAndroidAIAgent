@file:Suppress(
    "MatchingDeclarationName", // File hosts both `ComposerState` (sealed interface) and the `ChatComposer` composable.
)

package app.knotwork.design.components.chat

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.outlined.AttachFile
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material.icons.outlined.Stop
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import app.knotwork.design.components.buttons.KnotworkIconButton
import app.knotwork.design.theme.KnotworkTheme
import app.knotwork.design.tokens.KnotworkTextStyles

/**
 * Composer state machine — drives the send / stop morph, the input enabled
 * state, and the inline error banner.
 *
 * Mirrors `compose/components/README.md` §Chat surface §ChatComposer.
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
 * Knotwork chat composer — multiline input + voice / attach / send-or-stop.
 *
 * Visual contract (`compose/components/README.md` §Chat surface §ChatComposer):
 *  - Multiline `OutlinedTextField` (1..6 visible lines, internal scroll past 6).
 *  - Leading icon row: `Attach` + `Voice`; trailing: send (Idle / Error) or
 *    stop (Generating). The send ↔ stop morph is a 200 ms crossfade via
 *    `AnimatedContent`. Under reduced motion the swap is instant.
 *  - When [state] is [ComposerState.Error] an inline banner with
 *    `signalError` accent renders above the input.
 *
 * **Stateless** — `value` is hoisted to the caller; this composable never
 * stores text. The screen ViewModel owns persistence and history.
 *
 * @param value current input value.
 * @param onValueChange invoked with each keystroke.
 * @param onSend invoked when the user taps send (Idle / Error states).
 * @param onStop invoked when the user taps stop (Generating state).
 * @param onVoice invoked when the user taps the voice affordance.
 * @param onAttach invoked when the user taps the attach affordance.
 * @param state current state of the composer (drives morph + error banner).
 * @param modifier optional layout modifier applied to the composer root.
 */
@Composable
@Suppress("LongParameterList") // Public composer API; collapsing into a state object hides intent.
fun ChatComposer(
    value: String,
    onValueChange: (String) -> Unit,
    onSend: () -> Unit,
    onStop: () -> Unit,
    onVoice: () -> Unit,
    onAttach: () -> Unit,
    state: ComposerState,
    modifier: Modifier = Modifier,
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(KnotworkTheme.spacing.sp1),
        modifier = modifier
            .fillMaxWidth()
            .background(color = MaterialTheme.colorScheme.surface)
            .padding(KnotworkTheme.spacing.sp3),
    ) {
        if (state is ComposerState.Error) {
            ErrorBanner(message = state.message)
        }
        Row(
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = Arrangement.spacedBy(KnotworkTheme.spacing.sp1),
        ) {
            KnotworkIconButton(
                onClick = onAttach,
                contentDescription = "Attach file",
                icon = Icons.Outlined.AttachFile,
            )
            OutlinedTextField(
                value = value,
                onValueChange = onValueChange,
                placeholder = {
                    Text(
                        text = "Message…",
                        style = KnotworkTextStyles.BodyBase,
                        color = KnotworkTheme.extended.onSurfaceDim,
                    )
                },
                textStyle = KnotworkTextStyles.BodyBase,
                minLines = 1,
                maxLines = COMPOSER_MAX_VISIBLE_LINES,
                modifier = Modifier.weight(1f),
            )
            KnotworkIconButton(
                onClick = onVoice,
                contentDescription = "Start voice input",
                icon = Icons.Outlined.Mic,
            )
            SendOrStopButton(state = state, onSend = onSend, onStop = onStop)
        }
    }
}

/** Maximum visible rows in the composer before internal scroll kicks in. */
private const val COMPOSER_MAX_VISIBLE_LINES = 6

/** Send ↔ stop morph powered by `AnimatedContent` (200 ms crossfade). */
@Composable
private fun SendOrStopButton(state: ComposerState, onSend: () -> Unit, onStop: () -> Unit) {
    val reducedMotion = KnotworkTheme.a11y.reducedMotion()
    val durationMs = if (reducedMotion) 0 else COMPOSER_MORPH_MS
    AnimatedContent(
        targetState = state is ComposerState.Generating,
        transitionSpec = {
            fadeIn(androidx.compose.animation.core.tween(durationMs)) togetherWith
                fadeOut(androidx.compose.animation.core.tween(durationMs))
        },
        label = "composer_send_stop_morph",
    ) { isGenerating ->
        val icon: ImageVector
        val description: String
        val onClick: () -> Unit
        if (isGenerating) {
            icon = Icons.Outlined.Stop
            description = "Stop generating"
            onClick = onStop
        } else {
            icon = Icons.Filled.ArrowUpward
            description = "Send message"
            onClick = onSend
        }
        KnotworkIconButton(onClick = onClick, contentDescription = description, icon = icon)
    }
}

/** Duration of the send ↔ stop cross-fade per `compose/components/animations.md` §Chat. */
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
            imageVector = Icons.Outlined.ErrorOutline,
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
