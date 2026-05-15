package app.knotwork.design.icons.imagevector

import androidx.compose.ui.graphics.vector.ImageVector

/**
 * Pipeline `QUEUE_PROCESSOR` node glyph — stacked items plus a return-loop
 * arrow. Source: `project_docs/design/icons-src/node-queue.svg`.
 */
internal val knotworkNodeQueueIcon: ImageVector by lazy { build() }

private fun build(): ImageVector = iconBuilder("NodeQueue")
    .strokePath(roundedRectPath(x = 3f, y = 4f, w = 12f, h = 3.5f, rx = 0.75f))
    .strokePath(roundedRectPath(x = 3f, y = 10.25f, w = 12f, h = 3.5f, rx = 0.75f))
    .strokePath(roundedRectPath(x = 3f, y = 16.5f, w = 12f, h = 3.5f, rx = 0.75f))
    .strokePath("M 18 7 a 4 4 0 0 1 4 4 v 2 a 4 4 0 0 1 -4 4")
    .strokePath("M 20 15 l -2 2 2 2")
    .build()
