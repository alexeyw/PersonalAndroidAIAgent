package app.knotwork.design.icons.imagevector

import androidx.compose.ui.graphics.vector.ImageVector

/**
 * `I.pause` glyph (pause (solid)) — single-stroke icon family.
 */
internal val knotworkPauseIcon: ImageVector by lazy { build() }

private fun build(): ImageVector = iconBuilder("Pause")
    .fillPath("M7 4h4v16H7zM13 4h4v16h-4z")
    .build()
