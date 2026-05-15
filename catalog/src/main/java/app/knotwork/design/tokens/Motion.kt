@file:Suppress(
    "MagicNumber", // Token file — durations and easing control points ARE the motion scale.
    "MatchingDeclarationName", // File hosts `KnotworkMotion` and `LocalKnotworkMotion`.
)

package app.knotwork.design.tokens

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf

/**
 * Knotwork motion tokens — durations + easings.
 *
 * Naming aligns with the Material3 motion vocabulary:
 *
 *  - [dur1] 100 ms — micro (selection state changes).
 *  - [dur2] 180 ms — small (button press, chip toggle).
 *  - [dur3] 280 ms — medium (sheet expand, on-the-spot screen entry).
 *  - [dur4] 420 ms — large (full-screen transitions, complex containers).
 *
 *  - [easeStd] standard easing (matches M3 emphasised).
 *  - [easeEmph] emphasised easing — currently identical curve to [easeStd];
 *    kept as a distinct token so the two can diverge without editing call sites.
 *  - [easeDecel] decelerate (enter motion).
 *  - [easeAccel] accelerate (exit motion).
 *
 * Components must read these tokens through `KnotworkTheme.motion` rather than
 * inlining magic numbers, so the design system can rebalance motion without a
 * round of grep-and-replace.
 *
 * @property dur1 100 ms duration token.
 * @property dur2 180 ms duration token.
 * @property dur3 280 ms duration token.
 * @property dur4 420 ms duration token.
 * @property easeStd standard easing curve.
 * @property easeEmph emphasised easing curve.
 * @property easeDecel decelerate easing curve (enter motion).
 * @property easeAccel accelerate easing curve (exit motion).
 */
@Immutable
data class KnotworkMotion(
    val dur1: Int = 100,
    val dur2: Int = 180,
    val dur3: Int = 280,
    val dur4: Int = 420,
    val easeStd: Easing = StandardEasing,
    val easeEmph: Easing = EmphasisedEasing,
    val easeDecel: Easing = DecelerateEasing,
    val easeAccel: Easing = AccelerateEasing,
)

private val StandardEasing: Easing = CubicBezierEasing(0.2f, 0f, 0f, 1f)
private val EmphasisedEasing: Easing = CubicBezierEasing(0.2f, 0f, 0f, 1f)
private val DecelerateEasing: Easing = CubicBezierEasing(0f, 0f, 0f, 1f)
private val AccelerateEasing: Easing = CubicBezierEasing(0.3f, 0f, 1f, 1f)

/** Singleton instance of the default [KnotworkMotion] tokens. */
internal val DefaultKnotworkMotion = KnotworkMotion()

/**
 * Composition-local provider for [KnotworkMotion].
 * Always set by the `KnotworkTheme` wrapper — read via `KnotworkTheme.motion`.
 */
val LocalKnotworkMotion = staticCompositionLocalOf { DefaultKnotworkMotion }
