package app.knotwork.design.icons.imagevector

import androidx.compose.ui.graphics.vector.ImageVector

/**
 * `I.x` glyph (close / clear) — spec §0.7 single-stroke icon family.
 * Source: `project_docs/design/icons-src/x.svg`.
 */
internal val knotworkXIcon: ImageVector by lazy { build() }

private fun build(): ImageVector = iconBuilder("X")
    .strokePath("M6 6l12 12M18 6L6 18")
    .build()
