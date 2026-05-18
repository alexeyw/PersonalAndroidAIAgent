package app.knotwork.design.foundations

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import app.knotwork.design.theme.KnotworkTheme

/**
 * Light-theme preview of [FoundationsCatalogPage] used by the Android Studio
 * preview pane.
 *
 * The preview pins `darkTheme = false` so palette swatches and typography
 * always render against the light surface ramp regardless of the IDE's
 * current `uiMode`.
 */
@Preview(name = "Foundations — Light", showBackground = true, heightDp = 1200)
@Composable
private fun FoundationsCatalogPageLightPreview() {
    KnotworkTheme(darkTheme = false) {
        FoundationsCatalogPage()
    }
}

/**
 * Dark-theme preview of [FoundationsCatalogPage].
 *
 * Pins `darkTheme = true` so the dark-surface variants of every token render
 * deterministically.
 */
@Preview(name = "Foundations — Dark", showBackground = true, heightDp = 1200)
@Composable
private fun FoundationsCatalogPageDarkPreview() {
    KnotworkTheme(darkTheme = true) {
        FoundationsCatalogPage()
    }
}
