package app.knotwork.design.icons.imagevector

import androidx.compose.ui.graphics.vector.ImageVector

/**
 * `I.gridOff` glyph (snap grid off) — single-stroke icon family.
 */
internal val knotworkGridOffIcon: ImageVector by lazy { build() }

private fun build(): ImageVector = iconBuilder("GridOff")
    .strokePath("M5 3h14a2 2 0 0 1 2 2v14a2 2 0 0 1 -2 2h-14a2 2 0 0 1 -2 -2v-14a2 2 0 0 1 2 -2z")
    .strokePath("M3 9h18")
    .strokePath("M9 3v18")
    .strokePath("M5 5l14 14")
    .build()
