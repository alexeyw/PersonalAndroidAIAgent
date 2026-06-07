package app.knotwork.design.icons.imagevector

import androidx.compose.ui.graphics.vector.ImageVector

/**
 * `I.warn` glyph (warning) — single-stroke icon family.
 */
internal val knotworkWarnIcon: ImageVector by lazy { build() }

private fun build(): ImageVector = iconBuilder("Warn")
    .strokePath("M12 4l10 17H2z M12 10v5 M12 17v.5")
    .build()
