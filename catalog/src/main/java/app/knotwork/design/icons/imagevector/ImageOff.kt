package app.knotwork.design.icons.imagevector

import androidx.compose.ui.graphics.vector.ImageVector

/**
 * `I.imageOff` glyph (broken / missing image — bubble + viewer fallback) —
 * single-stroke icon family. Image frame with a diagonal slash.
 */
internal val knotworkImageOffIcon: ImageVector by lazy { build() }

private fun build(): ImageVector = iconBuilder("ImageOff")
    .strokePath("M5 4.5h12.5")
    .strokePath("M19 5.6a1.5 1.5 0 0 1 1 1.4v11a1.5 1.5 0 0 1 -1.5 1.5h-13")
    .strokePath("M4.6 5.6a1.5 1.5 0 0 0 -1.1 1.4v11a1.5 1.5 0 0 0 1.5 1.5")
    .strokePath("M4 16.5l4.6-3.6 2.6 2")
    .strokePath("M3.5 3.5l17 17")
    .build()
