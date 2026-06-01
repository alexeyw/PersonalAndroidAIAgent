package app.knotwork.design.icons.imagevector

import androidx.compose.ui.graphics.vector.ImageVector

/**
 * `I.trash` glyph (delete) — spec §0.7 single-stroke icon family.
 * Source: `project_docs/design/icons-src/trash.svg`.
 */
internal val knotworkTrashIcon: ImageVector by lazy { build() }

private fun build(): ImageVector = iconBuilder("Trash")
    .strokePath("M5 7h14M10 7V4h4v3M7 7v13h10V7")
    .build()
