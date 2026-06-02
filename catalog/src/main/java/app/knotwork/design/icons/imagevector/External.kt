package app.knotwork.design.icons.imagevector

import androidx.compose.ui.graphics.vector.ImageVector

/**
 * `I.external` glyph (open external link) — spec §0.7 single-stroke icon family (round 2).
 * Source: `project_docs/design/icons-src/external.svg`.
 */
internal val knotworkExternalIcon: ImageVector by lazy { build() }

private fun build(): ImageVector = iconBuilder("External")
    .strokePath("M14 4h6v6")
    .strokePath("M20 4l-9 9")
    .strokePath("M18 14v4a2 2 0 01-2 2H6a2 2 0 01-2-2V8a2 2 0 012-2h4")
    .build()
