package app.knotwork.design.icons.imagevector

import androidx.compose.ui.graphics.vector.ImageVector

/**
 * `I.lock` glyph (lock) — spec §0.7 single-stroke icon family.
 * Source: `project_docs/design/icons-src/lock.svg`.
 */
internal val knotworkLockIcon: ImageVector by lazy { build() }

private fun build(): ImageVector = iconBuilder("Lock")
    .strokePath("M7 11h10a2 2 0 0 1 2 2v5a2 2 0 0 1 -2 2h-10a2 2 0 0 1 -2 -2v-5a2 2 0 0 1 2 -2z M8 11V8a4 4 0 018 0v3")
    .build()
