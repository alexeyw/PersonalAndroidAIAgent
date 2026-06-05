package app.knotwork.design.icons.imagevector

import androidx.compose.ui.graphics.vector.ImageVector

/**
 * Editor toolbar "Auto layout" affordance — grid lines (decorative, dimmed)
 * plus four anchor dots.
 */
internal val knotworkAutoLayoutIcon: ImageVector by lazy { build() }

private fun build(): ImageVector = iconBuilder("AutoLayout")
    .strokePath(
        d = "M 3 8 h 18 M 3 16 h 18 M 8 3 v 18 M 16 3 v 18",
        strokeWidth = 1.25f,
        strokeAlpha = 0.3f,
    )
    .fillPath(circlePath(cx = 8f, cy = 8f, r = 2f))
    .fillPath(circlePath(cx = 16f, cy = 8f, r = 2f))
    .fillPath(circlePath(cx = 8f, cy = 16f, r = 2f))
    .fillPath(circlePath(cx = 16f, cy = 16f, r = 2f))
    .build()
