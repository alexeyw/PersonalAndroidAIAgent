package ai.agent.android.presentation.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import app.knotwork.design.theme.KnotworkTheme

/**
 * Thin alias that delegates to [KnotworkTheme] so every screen receives the
 * brand palette, typography, shapes, and extended tokens.
 *
 * Phase 21 / Task 10 review fix: the previous implementation built its own
 * `MaterialTheme` from `dynamicLightColorScheme(context)` (Material You).
 * Wallpaper-derived `primary` is whatever the device picks — usually blue
 * — which is why the FAB, "Import JSON" link, and bottom-nav indicator
 * arrived blue in the QA build despite the catalog tokens shipping the
 * brand amber. Routing through [KnotworkTheme] pins the entire surface to
 * the documented design tokens.
 *
 * The `dynamicColor` flag is retained as a deprecated no-op so any
 * remaining call sites compile without modification — Material You is
 * intentionally disabled per `compose/decisions.md §8`.
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
