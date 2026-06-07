package app.knotwork.design.icons.imagevector

import androidx.compose.ui.graphics.vector.ImageVector

/**
 * `I.pin` glyph (pin / keep, outline) — single-stroke icon family.
 */
internal val knotworkPinIcon: ImageVector by lazy { build() }

/**
 * `I.pin` in its **pin-on** state — a distinct solid-filled pushpin (a solid
 * exception), not just the outline filled. Used for the "pinned" toggle.
 */
internal val knotworkPinOnIcon: ImageVector by lazy { buildOn() }

private fun build(): ImageVector = iconBuilder("Pin")
    .strokePath("M9 3h6")
    .strokePath("M10 3v4l-1.5 4h7l-1.5-4V3")
    .strokePath("M12 11v7")
    .build()

private fun buildOn(): ImageVector = iconBuilder("PinOn")
    .fillPath("M9 2.6h6v1.6H9z")
    .fillPath("M10 4.2v3l-1.5 4h7l-1.5-4v-3z")
    .fillPath("M11.4 11h1.2l-.6 7z")
    .build()
