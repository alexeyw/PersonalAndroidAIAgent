package app.knotwork.design.components.buttons

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.knotwork.design.tokens.KnotworkTextStyles

/**
 * Per-spec sizing tier for every Knotwork brand button.
 *
 * Label typography is `ButtonLabel` (Inter 600 / 14 sp / +0.1) across all
 * tiers per spec §2.1 — only height and padding vary by size:
 *
 * |       | height | h-padding | text style    |
 * |-------|--------|-----------|---------------|
 * | [Sm]  | 32 dp  | 12 dp     | `ButtonLabel` |
 * | [Md]  | 40 dp  | 20 dp     | `ButtonLabel` |
 * | [Lg]  | 48 dp  | 24 dp     | `ButtonLabel` |
 *
 * The interactive touch-target floors at 48 × 48 dp regardless of
 * size — every concrete button composable applies
 * `Modifier.minimumInteractiveComponentSize()` so a 32 dp visual still
 * captures taps inside the accessible zone.
 */
enum class KnotworkButtonSize { Sm, Md, Lg }

/**
 * Numeric constants powering [KnotworkButtonSize]. Kept in lock-step with
 * `buttons.md § Размерные константы`; changing a value here is the single
 * source of truth for every button composable in `:catalog`.
 *
 * The `forXxx` helpers translate a [KnotworkButtonSize] into the
 * appropriate height / padding / text style without requiring callers
 * to re-derive the lookup table at every call site.
 */
object KnotworkButtonDefaults {
    /** Visual height of the `Sm` tier; touch target stays ≥ 48 dp. */
    val HeightSm: Dp = 32.dp

    /** Visual height of the `Md` (default) tier. */
    val HeightMd: Dp = 40.dp

    /** Visual height of the `Lg` tier. */
    val HeightLg: Dp = 48.dp

    /** Symmetrical horizontal padding around the label/icon at `Sm`. */
    val PaddingSm: PaddingValues = PaddingValues(horizontal = 12.dp)

    /** Symmetrical horizontal padding around the label/icon at `Md`. */
    val PaddingMd: PaddingValues = PaddingValues(horizontal = 20.dp)

    /** Symmetrical horizontal padding around the label/icon at `Lg`. */
    val PaddingLg: PaddingValues = PaddingValues(horizontal = 24.dp)

    /** Leading-icon glyph size at `Sm` (matches `Lg` only in `Lg`). */
    val IconSizeSm: Dp = 16.dp

    /** Leading-icon glyph size at `Md` / `Lg`. */
    val IconSizeMd: Dp = 18.dp

    /** Horizontal gap between the leading icon and the label. */
    val IconGap: Dp = 8.dp

    /** Diameter of the in-button progress spinner. */
    val LoadingIndicatorSize: Dp = 16.dp

    /** Stroke width of the in-button progress spinner. */
    val LoadingIndicatorStroke: Dp = 2.dp

    /** Alpha applied to the label while the button is in the `loading` state. */
    const val LOADING_LABEL_ALPHA: Float = 0.3f

    /** Returns the visual height for the supplied [size]. */
    fun heightFor(size: KnotworkButtonSize): Dp = when (size) {
        KnotworkButtonSize.Sm -> HeightSm
        KnotworkButtonSize.Md -> HeightMd
        KnotworkButtonSize.Lg -> HeightLg
    }

    /** Returns the horizontal content padding for the supplied [size]. */
    fun paddingFor(size: KnotworkButtonSize): PaddingValues = when (size) {
        KnotworkButtonSize.Sm -> PaddingSm
        KnotworkButtonSize.Md -> PaddingMd
        KnotworkButtonSize.Lg -> PaddingLg
    }

    /** Returns the leading-icon glyph size for the supplied [size]. */
    fun iconSizeFor(size: KnotworkButtonSize): Dp = when (size) {
        KnotworkButtonSize.Sm -> IconSizeSm
        KnotworkButtonSize.Md, KnotworkButtonSize.Lg -> IconSizeMd
    }

    /** Returns the label text style — `ButtonLabel` for every tier (spec §2.1). */
    fun textStyleFor(size: KnotworkButtonSize): TextStyle = when (size) {
        KnotworkButtonSize.Sm, KnotworkButtonSize.Md, KnotworkButtonSize.Lg ->
            KnotworkTextStyles.ButtonLabel
    }
}
