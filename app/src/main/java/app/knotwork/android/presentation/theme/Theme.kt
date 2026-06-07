package app.knotwork.android.presentation.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import app.knotwork.design.theme.KnotworkTheme

/**
 * Thin alias that delegates to [KnotworkTheme] so every screen receives the
 * brand palette, typography, shapes, and extended tokens.
 *
 * Routing through [KnotworkTheme] avoids Material You's
 * `dynamicLightColorScheme(context)`, whose wallpaper-derived `primary` is
 * whatever the device picks — usually blue — instead of the brand amber, and
 * pins the entire surface to the documented design tokens.
 *
 * The `dynamicColor` flag is retained as a deprecated no-op so any
 * remaining call sites compile without modification — Material You is
 * intentionally disabled.
 *
 * @param darkTheme `true` to use the dark Knotwork palette; defaults to
 * the system theme via [isSystemInDarkTheme].
 * @param dynamicColor ignored. Material You is disabled (see KDoc above).
 * @param content composable tree wrapped by the theme.
 */
@Composable
fun AndroidAIAgentTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    @Suppress("UNUSED_PARAMETER") dynamicColor: Boolean = false,
    content: @Composable () -> Unit,
) {
    KnotworkTheme(darkTheme = darkTheme, content = content)
}
