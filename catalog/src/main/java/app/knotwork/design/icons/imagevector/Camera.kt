package app.knotwork.design.icons.imagevector

import androidx.compose.ui.graphics.vector.ImageVector

/**
 * `I.camera` glyph (camera capture source) — single-stroke icon family.
 * Body with a top hump + lens.
 */
internal val knotworkCameraIcon: ImageVector by lazy { build() }

private fun build(): ImageVector = iconBuilder("Camera")
    .strokePath(
        "M3 8.5a1.5 1.5 0 0 1 1.5 -1.5h2.4l1.3 -2h7.6l1.3 2h2.4" +
            "a1.5 1.5 0 0 1 1.5 1.5v9a1.5 1.5 0 0 1 -1.5 1.5h-15a1.5 1.5 0 0 1 -1.5 -1.5z",
    )
    .strokePath(circlePath(cx = 12f, cy = 13f, r = 3.3f))
    .build()
