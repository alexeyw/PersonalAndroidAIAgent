package app.knotwork.design.icons.imagevector

import androidx.compose.ui.graphics.vector.ImageVector

/**
 * `I.trigger` glyph — a bolt enclosed in a rounded-square frame ("an automatic,
 * event-driven run"). Both the frame and the bolt are stroked centerline paths,
 * matching the single-color stroke convention of the icon family.
 *
 * Reuses the framed-entity motif (the same rounded-square frame as the Skill
 * badge) while staying distinct from the bare [knotworkBoltIcon] that the
 * Quick-Settings one-tap "duty" tile owns. Used for the Triggers management
 * entry row.
 */
internal val knotworkTriggerIcon: ImageVector by lazy { build() }

private fun build(): ImageVector = iconBuilder("Trigger")
    .strokePath(
        "M6 4.5h12a1.5 1.5 0 0 1 1.5 1.5v12a1.5 1.5 0 0 1 -1.5 1.5h-12" +
            "a1.5 1.5 0 0 1 -1.5 -1.5v-12a1.5 1.5 0 0 1 1.5 -1.5z",
    )
    .strokePath("M13.4 7.7l-4 5.1h2.8l-0.5 3.5 4-5.1h-2.8z")
    .build()
