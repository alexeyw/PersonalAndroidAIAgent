package app.knotwork.design.icons.imagevector

import androidx.compose.ui.graphics.vector.ImageVector

/**
 * `I.monitor` glyph (monitoring / metrics) — spec §0.7 single-stroke icon family.
 * Source: `project_docs/design/icons-src/monitor.svg`.
 */
internal val knotworkMonitorIcon: ImageVector by lazy { build() }

private fun build(): ImageVector = iconBuilder("Monitor")
    .strokePath("M3 5h18v12H3z M8 21h8 M12 17v4")
    .build()
