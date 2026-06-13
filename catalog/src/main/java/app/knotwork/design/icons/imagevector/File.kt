package app.knotwork.design.icons.imagevector

import androidx.compose.ui.graphics.vector.ImageVector

/**
 * `I.file` glyph (generic file) — single-stroke icon family.
 */
internal val knotworkFileIcon: ImageVector by lazy { build() }

private fun build(): ImageVector = iconBuilder("File")
    .strokePath("M6 3h8l4 4v13a1 1 0 0 1 -1 1h-11a1 1 0 0 1 -1 -1v-16a1 1 0 0 1 1 -1z")
    .strokePath("M14 3v4h4")
    .build()
