package app.knotwork.design.icons.imagevector

import androidx.compose.ui.graphics.vector.ImageVector

/**
 * Pipeline `CLARIFICATION` node glyph — chat bubble with question mark.
 */
internal val knotworkNodeClarifyIcon: ImageVector by lazy { build() }

private fun build(): ImageVector = iconBuilder("NodeClarify")
    .strokePath("M 4 5 h 16 v 11 H 10 l -4 4 v -4 H 4 z")
    .strokePath("M 9.5 9.5 a 2.5 2.5 0 0 1 4.8 1 c 0 1.5 -2.3 1.5 -2.3 3")
    .fillPath(circlePath(cx = 12f, cy = 15.5f, r = 0.75f))
    .build()
