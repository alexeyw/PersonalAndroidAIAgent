package app.knotwork.design.icons.imagevector

import androidx.compose.ui.graphics.vector.ImageVector

/**
 * `I.battery` glyph (battery alert) — spec §0.7 single-stroke icon family (round 2).
 * Source: `project_docs/design/icons-src/battery.svg`.
 */
internal val knotworkBatteryIcon: ImageVector by lazy { build() }

private fun build(): ImageVector = iconBuilder("Battery")
    .strokePath("M4 7h13a2 2 0 0 1 2 2v6a2 2 0 0 1 -2 2h-13a2 2 0 0 1 -2 -2v-6a2 2 0 0 1 2 -2z")
    .strokePath("M20.5 10.5v3")
    .strokePath("M10.5 10v3")
    .strokePath("M10.5 15.5v.4")
    .build()
