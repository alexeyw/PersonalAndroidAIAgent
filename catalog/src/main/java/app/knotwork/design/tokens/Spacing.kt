@file:Suppress(
    "MagicNumber", // Token file — the dp values ARE the spacing scale.
    "MatchingDeclarationName", // File hosts both `KnotworkSpacing` and `LocalKnotworkSpacing`.
)

package app.knotwork.design.tokens

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Knotwork spacing scale built on a 4 dp grid.
 *
 * Step naming follows the upstream OKLCH token spec (`--sp-N`). New spacing
 * steps must not be introduced ad-hoc — extend the upstream tokens first,
 * then re-port to keep the design system as the single source of truth.
 *
 * @property sp0 zero — used by `PaddingValues` defaults.
 * @property sp1 4 dp.
 * @property sp2 8 dp.
 * @property sp3 12 dp.
 * @property sp4 16 dp.
 * @property sp5 20 dp.
 * @property sp6 24 dp.
 * @property sp8 32 dp.
 * @property sp10 40 dp.
 * @property sp12 48 dp.
 * @property sp16 64 dp.
 */
@Immutable
data class KnotworkSpacing(
    val sp0: Dp = 0.dp,
    val sp1: Dp = 4.dp,
    val sp2: Dp = 8.dp,
    val sp3: Dp = 12.dp,
    val sp4: Dp = 16.dp,
    val sp5: Dp = 20.dp,
    val sp6: Dp = 24.dp,
    val sp8: Dp = 32.dp,
    val sp10: Dp = 40.dp,
    val sp12: Dp = 48.dp,
    val sp16: Dp = 64.dp,
)

/** Singleton instance of the default [KnotworkSpacing] scale. */
internal val DefaultKnotworkSpacing = KnotworkSpacing()

/**
 * Composition-local provider for [KnotworkSpacing].
 * Always set by the `KnotworkTheme` wrapper — read via `KnotworkTheme.spacing`.
 */
val LocalKnotworkSpacing = staticCompositionLocalOf { DefaultKnotworkSpacing }
