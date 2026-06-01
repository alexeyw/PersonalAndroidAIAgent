package app.knotwork.design.icons.imagevector

import androidx.compose.ui.graphics.vector.ImageVector

/**
 * Pipelines tab glyph — three connected nodes mirroring the visual identity of
 * the editor canvas. Source: `project_docs/design/icons-src/flow.svg`.
 */
internal val knotworkFlowIcon: ImageVector by lazy { build(IconStroke.DEFAULT) }

/** Active (2.0 stroke) variant — selected Pipelines bottom-nav tab. */
internal val knotworkFlowActiveIcon: ImageVector by lazy { build(IconStroke.ACTIVE) }

private fun build(strokeWidth: Float): ImageVector = iconBuilder("Flow")
    .strokePath(circlePath(cx = 5f, cy = 12f, r = 2.5f), strokeWidth = strokeWidth)
    .strokePath(circlePath(cx = 14f, cy = 6f, r = 2.5f), strokeWidth = strokeWidth)
    .strokePath(circlePath(cx = 14f, cy = 18f, r = 2.5f), strokeWidth = strokeWidth)
    .fillPath(circlePath(cx = 20.5f, cy = 12f, r = 1.5f))
    .strokePath("M 7.3 10.6 L 11.7 7.4", strokeWidth = strokeWidth)
    .strokePath("M 7.3 13.4 L 11.7 16.6", strokeWidth = strokeWidth)
    .strokePath("M 16.5 6.8 l 3 4.4", strokeWidth = strokeWidth)
    .strokePath("M 16.5 17.2 l 3 -4.4", strokeWidth = strokeWidth)
    .build()
