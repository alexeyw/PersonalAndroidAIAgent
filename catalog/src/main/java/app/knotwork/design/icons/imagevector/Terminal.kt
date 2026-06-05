package app.knotwork.design.icons.imagevector

import androidx.compose.ui.graphics.vector.ImageVector

/**
 * `I.terminal` glyph (console >_) — single-stroke icon family.
 */
internal val knotworkTerminalIcon: ImageVector by lazy { build() }

private fun build(): ImageVector = iconBuilder("Terminal")
    .strokePath("M5 4h14a2 2 0 0 1 2 2v12a2 2 0 0 1 -2 2h-14a2 2 0 0 1 -2 -2v-12a2 2 0 0 1 2 -2z")
    .strokePath("M7 9l3 3-3 3")
    .strokePath("M13 15h4")
    .build()
