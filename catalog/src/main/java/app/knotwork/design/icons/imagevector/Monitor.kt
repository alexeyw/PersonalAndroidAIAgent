package app.knotwork.design.icons.imagevector

import androidx.compose.ui.graphics.vector.ImageVector

/**
 * `I.monitor` glyph (monitoring / metrics) — single-stroke icon family.
 */
internal val knotworkMonitorIcon: ImageVector by lazy { build() }

private fun build(): ImageVector = iconBuilder("Monitor")
    .strokePath("M3 5h18v12H3z M8 21h8 M12 17v4")
    .build()
