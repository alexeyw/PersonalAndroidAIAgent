package app.knotwork.design.icons

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import app.knotwork.design.theme.KnotworkTheme

/**
 * Light-theme preview of [IconCatalogPage] used by the Android Studio
 * preview pane.
 *
 * Pins `darkTheme = false` so glyphs always render against the light surface
 * ramp regardless of the IDE's current `uiMode`.
 */
@Preview(name = "Icons — Light", showBackground = true, heightDp = 2800)
@Composable
private fun IconCatalogPageLightPreview() {
    KnotworkTheme(darkTheme = false) {
        IconCatalogPage()
    }
}

/**
 * Dark-theme preview of [IconCatalogPage].
 */
@Preview(name = "Icons — Dark", showBackground = true, heightDp = 2800)
@Composable
private fun IconCatalogPageDarkPreview() {
    KnotworkTheme(darkTheme = true) {
        IconCatalogPage()
    }
}
