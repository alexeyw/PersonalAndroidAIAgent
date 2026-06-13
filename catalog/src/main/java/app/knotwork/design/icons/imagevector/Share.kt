package app.knotwork.design.icons.imagevector

import androidx.compose.ui.graphics.vector.ImageVector

/**
 * `I.share` glyph (share sheet) — single-stroke icon family. Three stroked
 * nodes joined by two connectors, mirroring the source SVG's circles + lines.
 */
internal val knotworkShareIcon: ImageVector by lazy { build() }

private fun build(): ImageVector = iconBuilder("Share")
    .strokePath(circlePath(cx = 6f, cy = 12f, r = 2.4f))
    .strokePath(circlePath(cx = 17.5f, cy = 6f, r = 2.4f))
    .strokePath(circlePath(cx = 17.5f, cy = 18f, r = 2.4f))
    .strokePath("M8.2 11l7.1-3.7")
    .strokePath("M8.2 13l7.1 3.7")
    .build()
