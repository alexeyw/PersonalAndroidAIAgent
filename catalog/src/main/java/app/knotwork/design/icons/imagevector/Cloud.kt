package app.knotwork.design.icons.imagevector

import androidx.compose.ui.graphics.vector.ImageVector

/**
 * `I.cloud` glyph (cloud LLM) — spec §0.7 single-stroke icon family.
 * Source: `project_docs/design/icons-src/cloud.svg`.
 */
internal val knotworkCloudIcon: ImageVector by lazy { build() }

private fun build(): ImageVector = iconBuilder("Cloud")
    .strokePath("M7 19h11a4 4 0 000-8 6 6 0 00-11.7-1.5A4 4 0 007 19z")
    .build()
