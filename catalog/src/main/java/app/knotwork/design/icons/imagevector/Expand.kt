package app.knotwork.design.icons.imagevector

import androidx.compose.ui.graphics.vector.ImageVector

/**
 * `I.expand` glyph (fullscreen / open in full) — spec §0.7 single-stroke icon family (round 2).
 * Source: `project_docs/design/icons-src/expand.svg`.
 */
internal val knotworkExpandIcon: ImageVector by lazy { build() }

private fun build(): ImageVector = iconBuilder("Expand")
    .strokePath("M14 4h6v6")
    .strokePath("M20 4l-7 7")
    .strokePath("M10 20H4v-6")
    .strokePath("M4 20l7-7")
    .build()
