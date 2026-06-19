package app.knotwork.design.icons.imagevector

import androidx.compose.ui.graphics.vector.ImageVector

/**
 * `I.gauge` glyph (a speedometer — a semicircular arc, a needle, and a filled
 * hub) — single-stroke icon family. Used as the header glyph of the model
 * Performance card, reading as "speed" where `monitor` / `bolt` / `history`
 * would not.
 */
internal val knotworkGaugeIcon: ImageVector by lazy { build() }

private fun build(): ImageVector = iconBuilder("Gauge")
    .strokePath("M4 17a8 8 0 0 1 16 0")
    .strokePath("M12 17l4.4 -4.8")
    .fillPath(circlePath(cx = 12f, cy = 17f, r = 1.3f))
    .build()
