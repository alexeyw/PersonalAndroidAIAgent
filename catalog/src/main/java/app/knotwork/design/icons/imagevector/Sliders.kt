package app.knotwork.design.icons.imagevector

import androidx.compose.ui.graphics.vector.ImageVector

/**
 * `I.sliders` glyph (tune (≠ filter funnel)) — single-stroke icon family.
 */
internal val knotworkSlidersIcon: ImageVector by lazy { build() }

private fun build(): ImageVector = iconBuilder("Sliders")
    .strokePath("M3 7h18")
    .strokePath("M3 12h18")
    .strokePath("M3 17h18")
    .strokePath("M13.8 7a2.2 2.2 0 1 0 4.4 0a2.2 2.2 0 1 0 -4.4 0")
    .strokePath("M6.8 12a2.2 2.2 0 1 0 4.4 0a2.2 2.2 0 1 0 -4.4 0")
    .strokePath("M11.8 17a2.2 2.2 0 1 0 4.4 0a2.2 2.2 0 1 0 -4.4 0")
    .build()
