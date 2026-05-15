package app.knotwork.design.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import app.knotwork.design.tokens.DefaultKnotworkElevation
import app.knotwork.design.tokens.DefaultKnotworkMotion
import app.knotwork.design.tokens.DefaultKnotworkShapes
import app.knotwork.design.tokens.DefaultKnotworkSpacing
import app.knotwork.design.tokens.KnotworkElevation
import app.knotwork.design.tokens.KnotworkExtendedColors
import app.knotwork.design.tokens.KnotworkMotion
import app.knotwork.design.tokens.KnotworkShapes
import app.knotwork.design.tokens.KnotworkSpacing
import app.knotwork.design.tokens.LocalKnotworkElevation
import app.knotwork.design.tokens.LocalKnotworkExtendedColors
import app.knotwork.design.tokens.LocalKnotworkMotion
import app.knotwork.design.tokens.LocalKnotworkShapes
import app.knotwork.design.tokens.LocalKnotworkSpacing
import app.knotwork.design.tokens.MaterialKnotworkShapes
import app.knotwork.design.tokens.knotworkDarkColorScheme
import app.knotwork.design.tokens.knotworkExtendedColorsDark
import app.knotwork.design.tokens.knotworkExtendedColorsLight
import app.knotwork.design.tokens.knotworkLightColorScheme
import app.knotwork.design.tokens.knotworkTypography

/**
 * Root theme of the Knotwork design system.
 *
 * Wires the Knotwork tokens into a [MaterialTheme]:
 *  - `colorScheme` ← `knotworkLightColorScheme()` / `knotworkDarkColorScheme()`
 *  - `typography`  ← `knotworkTypography()`
 *  - `shapes`      ← `MaterialKnotworkShapes`
 *
 * In addition, this composable installs the Knotwork-specific tokens that
 * have no Material3 slot (extended palette, spacing, elevation, motion and a
 * mirror of the shape scale) into composition locals so they can be reached
 * via the [KnotworkTheme] accessor object below.
 *
 * Material You / dynamic colour is intentionally **not** exposed as a
 * parameter: the design system pins its own accent ramp (see the design
 * decisions log §8 — "Material You disabled"). If a user ever asks for a
 * wallpaper-derived primary, that ships as a separate Settings flag, never
 * as a constructor parameter on this theme.
 *
 * @param darkTheme `true` to use the dark Knotwork palette; defaults to
 * [isSystemInDarkTheme]. Pinned to `false` / `true` in catalog previews to
 * snapshot both palettes deterministically.
 * @param content composable tree wrapped by the theme.
 */
@Composable
fun KnotworkTheme(darkTheme: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    val colorScheme = if (darkTheme) knotworkDarkColorScheme() else knotworkLightColorScheme()
    val extended = if (darkTheme) knotworkExtendedColorsDark() else knotworkExtendedColorsLight()
    CompositionLocalProvider(
        LocalKnotworkExtendedColors provides extended,
        LocalKnotworkSpacing provides DefaultKnotworkSpacing,
        LocalKnotworkShapes provides DefaultKnotworkShapes,
        LocalKnotworkElevation provides DefaultKnotworkElevation,
        LocalKnotworkMotion provides DefaultKnotworkMotion,
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = knotworkTypography(),
            shapes = MaterialKnotworkShapes,
            content = content,
        )
    }
}

/**
 * Thin accessor object for Knotwork-specific tokens that do not have a
 * direct Material3 slot.
 *
 * Mirrors the shape of `MaterialTheme.colorScheme` / `.typography` etc:
 * call sites read `KnotworkTheme.extended.nodeIntentRouter` or
 * `KnotworkTheme.spacing.sp4` from any `@Composable` scope wrapped by the
 * [KnotworkTheme] composable above.
 *
 * The properties are `@ReadOnlyComposable` so they have zero per-call
 * overhead beyond the composition-local lookup; the underlying locals are
 * `staticCompositionLocalOf`, so changes invalidate the entire subtree
 * (acceptable — Knotwork tokens flip only on theme switches).
 */
object KnotworkTheme {
    /** Extended palette (chat surfaces, console, risk pills, 12 node hues). */
    val extended: KnotworkExtendedColors
        @Composable
        @ReadOnlyComposable
        get() = LocalKnotworkExtendedColors.current

    /** Spacing scale (4 dp grid). */
    val spacing: KnotworkSpacing
        @Composable
        @ReadOnlyComposable
        get() = LocalKnotworkSpacing.current

    /** Shape scale (xs..xl + full). Mirror of `MaterialTheme.shapes`. */
    val shapes: KnotworkShapes
        @Composable
        @ReadOnlyComposable
        get() = LocalKnotworkShapes.current

    /** Elevation scale (el0..el5). */
    val elevation: KnotworkElevation
        @Composable
        @ReadOnlyComposable
        get() = LocalKnotworkElevation.current

    /** Motion tokens (duration + easing). */
    val motion: KnotworkMotion
        @Composable
        @ReadOnlyComposable
        get() = LocalKnotworkMotion.current
}
