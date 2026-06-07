package app.knotwork.design.icons.imagevector

import androidx.compose.ui.graphics.vector.ImageVector

/**
 * `I.download2` glyph (import / export (alt)) — single-stroke icon family.
 */
internal val knotworkDownload2Icon: ImageVector by lazy { build() }

private fun build(): ImageVector = iconBuilder("Download2")
    .strokePath("M12 4v10M7 11l5 5 5-5M5 18h14")
    .build()
