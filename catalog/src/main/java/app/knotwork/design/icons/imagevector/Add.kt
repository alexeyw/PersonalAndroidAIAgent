package app.knotwork.design.icons.imagevector

import androidx.compose.ui.graphics.vector.ImageVector

/**
 * `I.add` glyph (add / FAB +) — single-stroke icon family.
 */
internal val knotworkAddIcon: ImageVector by lazy { build() }

private fun build(): ImageVector = iconBuilder("Add")
    .strokePath("M12 5v14M5 12h14")
    .build()
