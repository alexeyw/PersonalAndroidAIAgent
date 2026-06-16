package app.knotwork.design.icons.imagevector

import androidx.compose.ui.graphics.vector.ImageVector

/**
 * `I.skill` glyph — a star framed in a rounded square ("a packaged,
 * reusable capability"). Mixed glyph: the frame is stroked, the star is
 * filled (same convention as `theme`). Used for the Skill library entry.
 */
internal val knotworkSkillIcon: ImageVector by lazy { build() }

private fun build(): ImageVector = iconBuilder("Skill")
    .strokePath(
        "M6 4.5h12a1.5 1.5 0 0 1 1.5 1.5v12a1.5 1.5 0 0 1 -1.5 1.5h-12" +
            "a1.5 1.5 0 0 1 -1.5 -1.5v-12a1.5 1.5 0 0 1 1.5 -1.5z",
    )
    .fillPath(
        "M12 8.3l0.87 2.5 2.65 0.06-2.11 1.6 0.77 2.53L12 13.48l-2.18 1.51" +
            "0.77-2.53-2.11-1.6 2.65-0.06z",
    )
    .build()
