package app.knotwork.design.icons.imagevector

import androidx.compose.ui.graphics.vector.ImageVector

/**
 * Pipeline `INTENT_ROUTER` node glyph — input dot fanning out to three target
 * circles. Source: `project_docs/design/icons-src/node-intent-router.svg`.
 */
internal val knotworkNodeIntentRouterIcon: ImageVector by lazy { build() }

private fun build(): ImageVector = iconBuilder("NodeIntentRouter")
    .fillPath(circlePath(cx = 6f, cy = 12f, r = 2.25f))
    .strokePath(circlePath(cx = 19f, cy = 5f, r = 1.75f))
    .strokePath(circlePath(cx = 19f, cy = 12f, r = 1.75f))
    .strokePath(circlePath(cx = 19f, cy = 19f, r = 1.75f))
    .strokePath("M 8 11 l 9.5 -5.4")
    .strokePath("M 8.25 12 h 9")
    .strokePath("M 8 13 l 9.5 5.4")
    .build()
