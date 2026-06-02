package app.knotwork.design.icons.imagevector

import androidx.compose.ui.graphics.vector.ImageVector

/**
 * `I.stop` glyph (stop run (solid square)) — spec §0.7 single-stroke icon family (round 2).
 * Source: `project_docs/design/icons-src/stop.svg`.
 */
internal val knotworkStopIcon: ImageVector by lazy { build() }

private fun build(): ImageVector = iconBuilder("Stop")
    .fillPath("M8 6h8a2 2 0 0 1 2 2v8a2 2 0 0 1 -2 2h-8a2 2 0 0 1 -2 -2v-8a2 2 0 0 1 2 -2z")
    .build()
