package app.knotwork.design.icons.imagevector

import androidx.compose.ui.graphics.vector.ImageVector

/**
 * `I.fileAudio` glyph (an audio document — file outline + a mini waveform) —
 * single-stroke icon family. Used by the voice-input source chooser's
 * "Choose audio file" option.
 */
internal val knotworkFileAudioIcon: ImageVector by lazy { build() }

private fun build(): ImageVector = iconBuilder("FileAudio")
    .strokePath("M6 3h8l4 4v13a1 1 0 0 1 -1 1h-11a1 1 0 0 1 -1 -1v-16a1 1 0 0 1 1 -1z")
    .strokePath("M14 3v4h4")
    .strokePath("M8.5 14v2")
    .strokePath("M11 12v6")
    .strokePath("M13.5 13v4")
    .strokePath("M16 14.5v1")
    .build()
