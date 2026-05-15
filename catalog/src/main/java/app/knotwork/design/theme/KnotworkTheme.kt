package app.knotwork.design.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable

/**
 * Root theme of the Knotwork design system.
 *
 * Phase 21 / Task 1/11 scaffold: this is intentionally a pass-through to
 * [MaterialTheme] so the `:catalog` module compiles and downstream callers
 * can already wrap their content in `KnotworkTheme { ... }`. Token-driven
 * `colorScheme` / `typography` / `shapes` land in Task 2/11 along with the
 * port of the design-system tokens into Kotlin sources.
 *
 * The [darkTheme] parameter is exposed (and defaults to the system value)
 * for forward compatibility — once Task 2/11 wires the light / dark
 * `knotwork*ColorScheme()` factories, the signature stays unchanged.
 *
 * Material You / dynamic color is **not** exposed as a parameter: the
 * design system pins its own accent ramp.
 *
 * @param darkTheme `true` to use the dark palette; defaults to
 * [isSystemInDarkTheme]. Pinned to `false` / `true` in catalog previews to
 * snapshot both palettes deterministically.
 * @param content composable tree wrapped by the theme. Receives
 * `MaterialTheme.colorScheme` / `typography` / `shapes` from the parent
 * theme until the token port lands.
 */
@Composable
fun KnotworkTheme(
    @Suppress("UNUSED_PARAMETER") darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(content = content)
}
