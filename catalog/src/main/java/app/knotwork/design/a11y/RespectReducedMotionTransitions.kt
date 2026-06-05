package app.knotwork.design.a11y

import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.runtime.Composable

/**
 * Pair of [EnterTransition] / [ExitTransition] honouring the reduced-motion
 * gate from [KnotworkA11y]. When the user has reduced motion enabled the
 * caller's enter/exit pair is replaced with an alpha-only crossfade
 * (80 ms).
 *
 * Used by `AnimatedVisibility(enter = .., exit = ..)` call sites that need
 * a single switch — pass the resulting pair components individually:
 *
 * ```kotlin
 * val transitions = respectReducedMotionTransitions(
 *     enter = slideInVertically() + fadeIn(),
 *     exit = slideOutVertically() + fadeOut(),
 * )
 * AnimatedVisibility(visible = …, enter = transitions.enter, exit = transitions.exit)
 * ```
 *
 * @property enter resolved enter transition.
 * @property exit resolved exit transition.
 */
data class RespectReducedMotionTransitions(val enter: EnterTransition, val exit: ExitTransition)

/** Constant duration for the reduced-motion crossfade fallback. */
private const val REDUCED_MOTION_CROSSFADE_MS = 80

/**
 * Builds a [RespectReducedMotionTransitions] pair: returns ([enter], [exit])
 * when reduced motion is OFF, and a 80 ms alpha-only crossfade otherwise.
 *
 * Reads [LocalKnotworkA11y] so tests can inject [FixedKnotworkA11y] to verify
 * the reduced-motion branch deterministically.
 *
 * @param enter enter transition used when reduced motion is OFF.
 * @param exit exit transition used when reduced motion is OFF.
 * @return resolved transition pair honouring the user's a11y preference.
 */
@Composable
fun respectReducedMotionTransitions(enter: EnterTransition, exit: ExitTransition): RespectReducedMotionTransitions {
    val a11y = LocalKnotworkA11y.current
    return if (a11y.reducedMotion()) {
        RespectReducedMotionTransitions(
            enter = fadeIn(animationSpec = androidx.compose.animation.core.tween(REDUCED_MOTION_CROSSFADE_MS)),
            exit = fadeOut(animationSpec = androidx.compose.animation.core.tween(REDUCED_MOTION_CROSSFADE_MS)),
        )
    } else {
        RespectReducedMotionTransitions(enter = enter, exit = exit)
    }
}
