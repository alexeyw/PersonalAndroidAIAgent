package app.knotwork.design.icons.imagevector

import androidx.compose.ui.graphics.vector.ImageVector

/**
 * `I.hub` glyph (hub / integrations) — spec §0.7 single-stroke icon family (round 2).
 * Source: `project_docs/design/icons-src/hub.svg`.
 */
internal val knotworkHubIcon: ImageVector by lazy { build() }

private fun build(): ImageVector = iconBuilder("Hub")
    .strokePath("M9.6 12a2.4 2.4 0 1 0 4.8 0a2.4 2.4 0 1 0 -4.8 0")
    .strokePath("M3 5a2 2 0 1 0 4 0a2 2 0 1 0 -4 0")
    .strokePath("M17 5a2 2 0 1 0 4 0a2 2 0 1 0 -4 0")
    .strokePath("M3 19a2 2 0 1 0 4 0a2 2 0 1 0 -4 0")
    .strokePath("M17 19a2 2 0 1 0 4 0a2 2 0 1 0 -4 0")
    .strokePath("M6.7 6.7l3 3")
    .strokePath("M17.3 6.7l-3 3")
    .strokePath("M6.7 17.3l3-3")
    .strokePath("M17.3 17.3l-3-3")
    .build()
