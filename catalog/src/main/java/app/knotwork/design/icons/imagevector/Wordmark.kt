package app.knotwork.design.icons.imagevector

import androidx.compose.ui.graphics.vector.ImageVector

/**
 * Knotwork wordmark glyph — 24×24 mark used by `AppIcons.Wordmark`. Note that
 * the literal "Knotwork" wordmark is rendered via Compose [androidx.compose.material3.Text]
 * with the bundled Inter font; this glyph is the iconographic fallback (see
 * `decisions.md §13`).
 *
 * Source: `project_docs/design/icons-src/wordmark.svg`.
 */
internal val knotworkWordmarkIcon: ImageVector by lazy { build() }

private fun build(): ImageVector = iconBuilder("Wordmark")
    .strokePath("M 6 4 v 16")
    .strokePath("M 6 12 l 8 -8")
    .strokePath("M 6 12 l 8 8")
    .fillPath(circlePath(cx = 6f, cy = 4f, r = 1.25f))
    .fillPath(circlePath(cx = 6f, cy = 20f, r = 1.25f))
    .fillPath(circlePath(cx = 14f, cy = 4f, r = 1.25f))
    .fillPath(circlePath(cx = 14f, cy = 20f, r = 1.25f))
    .fillPath(circlePath(cx = 6f, cy = 12f, r = 1.5f))
    .build()
