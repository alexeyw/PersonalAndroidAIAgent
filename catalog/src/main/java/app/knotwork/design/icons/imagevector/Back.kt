package app.knotwork.design.icons.imagevector

import androidx.compose.ui.graphics.vector.ImageVector

/**
 * `I.back` glyph (back) — spec §0.7 single-stroke icon family.
 * Source: `project_docs/design/icons-src/back.svg`.
 */
internal val knotworkBackIcon: ImageVector by lazy { build() }

private fun build(): ImageVector = iconBuilder("Back")
    .strokePath("M15 6l-6 6 6 6")
    .build()
