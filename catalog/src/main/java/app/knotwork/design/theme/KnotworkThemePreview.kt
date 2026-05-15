package app.knotwork.design.theme

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview

/**
 * Smallest sanity-preview of [KnotworkTheme] rendering a single [Text].
 *
 * Used by `:catalog`'s Android Studio preview pane to verify the theme
 * scaffold compiles and lays out a child composable at all. Task 2/11
 * replaces this with the proper Foundations catalog page (palette + type
 * scale + spacing) and the snapshot-test baseline.
 */
@Preview(name = "Knotwork — Light", showBackground = true)
@Composable
private fun KnotworkThemeLightPreview() {
    KnotworkTheme(darkTheme = false) {
        Text(text = "Knotwork")
    }
}

@Preview(name = "Knotwork — Dark", showBackground = true)
@Composable
private fun KnotworkThemeDarkPreview() {
    KnotworkTheme(darkTheme = true) {
        Text(text = "Knotwork")
    }
}
