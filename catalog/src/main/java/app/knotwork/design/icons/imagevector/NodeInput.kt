package app.knotwork.design.icons.imagevector

import androidx.compose.ui.graphics.vector.ImageVector

/**
 * Pipeline `INPUT` node glyph — inbound arrow into a rounded square.
 */
internal val knotworkNodeInputIcon: ImageVector by lazy { build() }

private fun build(): ImageVector = iconBuilder("NodeInput")
    .strokePath(roundedRectPath(x = 11f, y = 4f, w = 9f, h = 16f, rx = 2f))
    .strokePath("M 3 12 h 11")
    .strokePath("M 9 8 l 5 4 -5 4")
    .build()
