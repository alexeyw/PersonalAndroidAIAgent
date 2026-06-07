package app.knotwork.design.icons.imagevector

import androidx.compose.ui.graphics.vector.ImageVector

/**
 * `I.send` glyph (send message) — single-stroke icon family.
 */
internal val knotworkSendIcon: ImageVector by lazy { build() }

private fun build(): ImageVector = iconBuilder("Send")
    .strokePath("M4 12l16-8-6 16-3-7-7-1z")
    .build()
