package app.knotwork.design.icons.imagevector

import androidx.compose.ui.graphics.vector.ImageVector

/**
 * Pipeline `SUMMARY` node glyph — document with bullet lines.
 */
internal val knotworkNodeSummaryIcon: ImageVector by lazy { build() }

private fun build(): ImageVector = iconBuilder("NodeSummary")
    .strokePath("M 6 3 h 9 l 4 4 v 14 H 6 z")
    .strokePath("M 14 3 v 4 h 5")
    .fillPath(circlePath(cx = 8.75f, cy = 11f, r = 0.6f))
    .fillPath(circlePath(cx = 8.75f, cy = 14f, r = 0.6f))
    .fillPath(circlePath(cx = 8.75f, cy = 17f, r = 0.6f))
    .strokePath("M 11 11 h 5")
    .strokePath("M 11 14 h 5")
    .strokePath("M 11 17 h 3.5")
    .build()
