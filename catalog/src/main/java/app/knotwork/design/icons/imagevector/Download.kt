package app.knotwork.design.icons.imagevector

import androidx.compose.ui.graphics.vector.ImageVector

/**
 * `I.download` glyph (download) — spec §0.7 single-stroke icon family.
 * Source: `project_docs/design/icons-src/download.svg`.
 */
internal val knotworkDownloadIcon: ImageVector by lazy { build() }

private fun build(): ImageVector = iconBuilder("Download")
    .strokePath("M12 4v12M6 12l6 6 6-6M4 20h16")
    .build()
