package app.knotwork.design.icons.imagevector

import androidx.compose.ui.graphics.vector.ImageVector

/**
 * `I.add` glyph (add / FAB +) — spec §0.7 single-stroke icon family.
 * Source: `project_docs/design/icons-src/add.svg`.
 */
internal val knotworkAddIcon: ImageVector by lazy { build() }

private fun build(): ImageVector = iconBuilder("Add")
    .strokePath("M12 5v14M5 12h14")
    .build()
