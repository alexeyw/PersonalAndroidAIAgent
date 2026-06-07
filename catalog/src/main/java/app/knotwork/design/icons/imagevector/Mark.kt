package app.knotwork.design.icons.imagevector

import androidx.compose.ui.graphics.vector.ImageVector

/**
 * Knotwork brand mark — the single canonical glyph: **two nodes joined by one
 * edge** (input → output). 24×24 grid, identical geometry to the launcher icon
 * and `brand/mark.svg` so About, splash, onboarding and the
 * launcher all show the same glyph.
 */
internal val knotworkMarkIcon: ImageVector by lazy { build() }

private const val MARK_EDGE_STROKE = 1.5f

private fun build(): ImageVector = iconBuilder("Mark")
    .fillPath(circlePath(cx = 6f, cy = 7f, r = 3f))
    .fillPath(circlePath(cx = 18f, cy = 17f, r = 3f))
    .strokePath("M 8 9 L 16 15", strokeWidth = MARK_EDGE_STROKE)
    .build()
