package app.knotwork.design.icons.imagevector

import androidx.compose.ui.graphics.vector.ImageVector

/**
 * Pipeline `PIPELINE` node glyph — a two-node sub-flow framed in a box
 * ("a pipeline nested inside a node"). Mirrors the editor's own
 * node-and-edge vocabulary so it reads as "runs another pipeline", and
 * stays distinct from the three-circle `flow` tab glyph.
 */
internal val knotworkNodePipelineIcon: ImageVector by lazy { build() }

private fun build(): ImageVector = iconBuilder("NodePipeline")
    .strokePath("M 5 5.5 h 14 a 1 1 0 0 1 1 1 v 11 a 1 1 0 0 1 -1 1 h -14 a 1 1 0 0 1 -1 -1 v -11 a 1 1 0 0 1 1 -1 z")
    .strokePath("M 7.95 9.5 a 1.35 1.35 0 1 0 2.7 0 a 1.35 1.35 0 1 0 -2.7 0")
    .strokePath("M 13.35 14.5 a 1.35 1.35 0 1 0 2.7 0 a 1.35 1.35 0 1 0 -2.7 0")
    .strokePath("M 10.65 9.5 h 2.05 a 1.4 1.4 0 0 1 1.4 1.4 v 2.05")
    .build()
