@file:Suppress(
    "MagicNumber", // Token file — dp values ARE the elevation scale.
    "MatchingDeclarationName", // File hosts `KnotworkElevation` and `LocalKnotworkElevation`.
)

package app.knotwork.design.tokens

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Knotwork elevation tokens — Material3 levels 0..5, monochrome-safe.
 *
 * The upstream design tokens are CSS box-shadow stacks; the closest Compose
 * primitive is [androidx.compose.material3.Surface]'s `tonalElevation` /
 * `shadowElevation`. The dp values below approximate the visual depth of the
 * corresponding shadow stack rather than reproducing the shadow literally —
 * Compose paints shadows via RenderNode, not CSS, so a 1-to-1 port is
 * neither possible nor desirable.
 *
 * @property el0 0 dp — flat (sits in the surface).
 * @property el1 1 dp — list rows, inline cards.
 * @property el2 3 dp — chips, pills.
 * @property el3 6 dp — bottom sheets, popovers.
 * @property el4 12 dp — selected node card, focused dialog.
 * @property el5 24 dp — modal overlays, drag-pickup.
 */
@Immutable
data class KnotworkElevation(
    val el0: Dp = 0.dp,
    val el1: Dp = 1.dp,
    val el2: Dp = 3.dp,
    val el3: Dp = 6.dp,
    val el4: Dp = 12.dp,
    val el5: Dp = 24.dp,
)

/** Singleton instance of the default [KnotworkElevation] scale. */
internal val DefaultKnotworkElevation = KnotworkElevation()

/**
 * Composition-local provider for [KnotworkElevation].
 * Always set by the `KnotworkTheme` wrapper — read via `KnotworkTheme.elevation`.
 */
val LocalKnotworkElevation = staticCompositionLocalOf { DefaultKnotworkElevation }
