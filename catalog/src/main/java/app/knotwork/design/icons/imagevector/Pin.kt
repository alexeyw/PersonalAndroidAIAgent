package app.knotwork.design.icons.imagevector

import androidx.compose.ui.graphics.vector.ImageVector

/**
 * `I.pin` glyph (pin, outline) — spec §0.7 single-stroke icon family.
 * Source: `project_docs/design/icons-src/pin.svg`.
 */
internal val knotworkPinIcon: ImageVector by lazy { build() }

/**
 * `I.pin` in its **pin-on** state — same path as [knotworkPinIcon] rendered as a
 * solid fill (spec §0.7 solid exception). Used for the "pinned" toggle state.
 */
internal val knotworkPinOnIcon: ImageVector by lazy { buildOn() }

private const val PIN_PATH = "M9 4h6l-1 7 3 3v2H7v-2l3-3-1-7z M12 16v4"

private fun build(): ImageVector = iconBuilder("Pin").strokePath(PIN_PATH).build()

private fun buildOn(): ImageVector = iconBuilder("PinOn").fillPath(PIN_PATH).build()
