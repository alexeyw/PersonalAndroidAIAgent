package app.knotwork.design.icons.imagevector

import androidx.compose.ui.graphics.vector.ImageVector

/**
 * Pipeline `TOOL` node glyph — wrench inside a hex frame.
 */
internal val knotworkNodeToolIcon: ImageVector by lazy { build() }

private fun build(): ImageVector = iconBuilder("NodeTool")
    .strokePath("M 12 3 l 7 4 v 10 l -7 4 -7 -4 V 7 z")
    .strokePath(
        "M 14 9.5 a 2.5 2.5 0 1 1 -3 4 .8 .8 0 0 0 -.8 .2 L 8 16 l -1.5 -1.5 " +
            "2.3 -2.2 a .8 .8 0 0 0 .2 -.8 2.5 2.5 0 0 1 4 -2.7 z",
    )
    .build()
