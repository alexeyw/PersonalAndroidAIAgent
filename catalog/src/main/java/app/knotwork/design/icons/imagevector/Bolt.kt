package app.knotwork.design.icons.imagevector

import androidx.compose.ui.graphics.vector.ImageVector

/**
 * `I.bolt` glyph (quick action / energy) — single-stroke icon family.
 */
internal val knotworkBoltIcon: ImageVector by lazy { build() }

private fun build(): ImageVector = iconBuilder("Bolt")
    .strokePath("M13 3L5 14h6l-1 7 8-11h-6z")
    .build()
