package app.knotwork.design.icons.imagevector

import androidx.compose.ui.graphics.vector.ImageVector

/**
 * `I.star` glyph (favorite / pin-list) — single-stroke icon family.
 */
internal val knotworkStarIcon: ImageVector by lazy { build() }

private fun build(): ImageVector = iconBuilder("Star")
    .strokePath("M12 4l2.6 5.3 5.9.8-4.3 4.1 1 5.8L12 17.3l-5.2 2.7 1-5.8L3.5 10l5.9-.8z")
    .build()
