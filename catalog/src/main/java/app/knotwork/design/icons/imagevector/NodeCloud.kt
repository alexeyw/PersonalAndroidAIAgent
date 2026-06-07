package app.knotwork.design.icons.imagevector

import androidx.compose.ui.graphics.vector.ImageVector

/**
 * Pipeline `CLOUD` node glyph — cloud shape with up-arrow.
 */
internal val knotworkNodeCloudIcon: ImageVector by lazy { build() }

private fun build(): ImageVector = iconBuilder("NodeCloud")
    .strokePath("M 7 19 h 11 a 3.5 3.5 0 1 0 0 -7 a 5.5 5.5 0 0 0 -10.7 -1 A 3.5 3.5 0 0 0 7 19 z")
    .strokePath("M 12 16 v -5 M 9.5 13.5 L 12 11 l 2.5 2.5")
    .build()
