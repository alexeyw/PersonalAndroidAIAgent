package app.knotwork.design.icons.imagevector

import androidx.compose.ui.graphics.vector.ImageVector

/**
 * Memory-screen entry glyph (two-lobed brain).
 */
internal val knotworkBrainIcon: ImageVector by lazy { build() }

private fun build(): ImageVector = iconBuilder("Brain")
    .strokePath(
        "M 9 4 a 3 3 0 0 0 -3 3 a 3 3 0 0 0 -2 5.2 A 3 3 0 0 0 5 17 a 3 3 0 0 0 3 3 " +
            "a 2.5 2.5 0 0 0 2.5 -2.5 V 5.5 A 1.5 1.5 0 0 0 9 4 z",
    )
    .strokePath(
        "M 15 4 a 3 3 0 0 1 3 3 a 3 3 0 0 1 2 5.2 A 3 3 0 0 1 19 17 a 3 3 0 0 1 -3 3 " +
            "a 2.5 2.5 0 0 1 -2.5 -2.5 V 5.5 A 1.5 1.5 0 0 1 15 4 z",
    )
    .strokePath("M 9 9 c 1 0 1.5 .5 1.5 1.5 M 15 9 c -1 0 -1.5 .5 -1.5 1.5")
    .strokePath("M 9 14.5 c 1 0 1.5 -.5 1.5 -1.5 M 15 14.5 c -1 0 -1.5 -.5 -1.5 -1.5")
    .build()
