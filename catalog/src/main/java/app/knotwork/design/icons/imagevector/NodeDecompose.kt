package app.knotwork.design.icons.imagevector

import androidx.compose.ui.graphics.vector.ImageVector

/**
 * Pipeline `DECOMPOSITION` node glyph — one-to-many fan from a single dot to
 * three small bins. Source: `project_docs/design/icons-src/node-decompose.svg`.
 */
internal val knotworkNodeDecomposeIcon: ImageVector by lazy { build() }

private fun build(): ImageVector = iconBuilder("NodeDecompose")
    .fillPath(circlePath(cx = 5f, cy = 12f, r = 2f))
    .strokePath(roundedRectPath(x = 15.5f, y = 3.5f, w = 6f, h = 4f, rx = 0.75f))
    .strokePath(roundedRectPath(x = 15.5f, y = 10f, w = 6f, h = 4f, rx = 0.75f))
    .strokePath(roundedRectPath(x = 15.5f, y = 16.5f, w = 6f, h = 4f, rx = 0.75f))
    .strokePath("M 7 11 L 15 6")
    .strokePath("M 7 12 h 8.5")
    .strokePath("M 7 13 L 15 18")
    .build()
