package app.knotwork.design.icons.imagevector

import androidx.compose.ui.graphics.vector.ImageVector

/**
 * Knotwork mark — 24×24 in-app version of the brand glyph. UI-grade simplification
 * of `brand/logo.svg`; renders cleanly at list-row sizes.
 *
 * Source: `project_docs/design/icons-src/mark.svg`.
 */
internal val knotworkMarkIcon: ImageVector by lazy { build() }

private fun build(): ImageVector = iconBuilder("Mark")
    .fillPath(circlePath(cx = 6f, cy = 6f, r = 2.5f))
    .fillPath(circlePath(cx = 6f, cy = 18f, r = 2.5f))
    .fillPath(circlePath(cx = 18f, cy = 18f, r = 2.5f))
    .strokePath("M 6 8.5 v 7")
    .strokePath("M 8.5 18 h 7")
    .strokePath("M 7.8 7.8 l 8.4 8.4")
    .build()
