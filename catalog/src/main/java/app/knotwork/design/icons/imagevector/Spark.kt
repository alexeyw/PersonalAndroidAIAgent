package app.knotwork.design.icons.imagevector

import androidx.compose.ui.graphics.vector.ImageVector

/**
 * `I.spark` glyph (auto / AI action) — spec §0.7 single-stroke icon family.
 * Source: `project_docs/design/icons-src/spark.svg`.
 */
internal val knotworkSparkIcon: ImageVector by lazy { build() }

private fun build(): ImageVector = iconBuilder("Spark")
    .strokePath("M12 3v4M12 17v4M3 12h4M17 12h4M5.6 5.6l2.8 2.8M15.6 15.6l2.8 2.8M5.6 18.4l2.8-2.8M15.6 8.4l2.8-2.8")
    .build()
