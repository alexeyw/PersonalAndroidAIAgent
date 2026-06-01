package app.knotwork.design.icons.imagevector

import androidx.compose.ui.graphics.vector.ImageVector

/**
 * `I.edit` glyph (edit / rename) — spec §0.7 single-stroke icon family.
 * Source: `project_docs/design/icons-src/edit.svg`.
 */
internal val knotworkEditIcon: ImageVector by lazy { build() }

private fun build(): ImageVector = iconBuilder("Edit")
    .strokePath("M4 20h4l10-10-4-4L4 16v4z M14 6l4 4")
    .build()
