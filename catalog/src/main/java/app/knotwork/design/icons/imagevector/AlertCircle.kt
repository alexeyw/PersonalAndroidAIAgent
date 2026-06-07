package app.knotwork.design.icons.imagevector

import androidx.compose.ui.graphics.vector.ImageVector

/**
 * `I.alertCircle` glyph (error/info in circle (≠ warn triangle)) — single-stroke icon family.
 */
internal val knotworkAlertCircleIcon: ImageVector by lazy { build() }

private fun build(): ImageVector = iconBuilder("AlertCircle")
    .strokePath("M3 12a9 9 0 1 0 18 0a9 9 0 1 0 -18 0")
    .strokePath("M12 7.5v6")
    .strokePath("M12 16.5v.4")
    .build()
