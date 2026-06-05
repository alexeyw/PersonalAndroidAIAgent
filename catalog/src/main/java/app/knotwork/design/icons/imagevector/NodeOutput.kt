package app.knotwork.design.icons.imagevector

import androidx.compose.ui.graphics.vector.ImageVector

/**
 * Pipeline `OUTPUT` node glyph — outbound arrow from a rounded square.
 */
internal val knotworkNodeOutputIcon: ImageVector by lazy { build() }

private fun build(): ImageVector = iconBuilder("NodeOutput")
    .strokePath(roundedRectPath(x = 4f, y = 4f, w = 9f, h = 16f, rx = 2f))
    .strokePath("M 10 12 h 11")
    .strokePath("M 16 8 l 5 4 -5 4")
    .build()
