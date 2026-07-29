package app.knotwork.design.screens.chatarchive

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import app.knotwork.design.theme.KnotworkTheme

/**
 * Android Studio previews for every documented [ChatArchiveVisualState]. The
 * Roborazzi suite re-runs the same [ChatArchivePreview] fixtures, so a
 * regression shows up once, in one diff.
 */
@Preview(name = "Populated — Light", group = "Light", showBackground = true, heightDp = 760)
@Composable
private fun ChatArchivePopulatedLightPreview() = KnotworkTheme(darkTheme = false) {
    ChatArchiveContent(state = ChatArchivePreview.populated())
}

@Preview(name = "Populated — Dark", group = "Dark", showBackground = true, heightDp = 760)
@Composable
private fun ChatArchivePopulatedDarkPreview() = KnotworkTheme(darkTheme = true) {
    ChatArchiveContent(state = ChatArchivePreview.populated())
}

@Preview(name = "Swipe open — Light", group = "Light", showBackground = true, heightDp = 760)
@Composable
private fun ChatArchiveSwipeOpenLightPreview() = KnotworkTheme(darkTheme = false) {
    ChatArchiveContent(state = ChatArchivePreview.swipeOpen())
}

@Preview(name = "Row menu — Light", group = "Light", showBackground = true, heightDp = 760)
@Composable
private fun ChatArchiveRowMenuLightPreview() = KnotworkTheme(darkTheme = false) {
    ChatArchiveContent(state = ChatArchivePreview.rowMenu())
}

@Preview(name = "Delete forever — Light", group = "Light", showBackground = true, heightDp = 760)
@Composable
private fun ChatArchiveDeleteConfirmLightPreview() = KnotworkTheme(darkTheme = false) {
    ChatArchiveContent(state = ChatArchivePreview.deleteConfirm())
}

@Preview(name = "Empty — Light", group = "Light", showBackground = true, heightDp = 760)
@Composable
private fun ChatArchiveEmptyLightPreview() = KnotworkTheme(darkTheme = false) {
    ChatArchiveContent(state = ChatArchivePreview.empty())
}

@Preview(name = "Empty — Dark", group = "Dark", showBackground = true, heightDp = 760)
@Composable
private fun ChatArchiveEmptyDarkPreview() = KnotworkTheme(darkTheme = true) {
    ChatArchiveContent(state = ChatArchivePreview.empty())
}

@Preview(name = "Loading — Light", group = "Light", showBackground = true, heightDp = 760)
@Composable
private fun ChatArchiveLoadingLightPreview() = KnotworkTheme(darkTheme = false) {
    ChatArchiveContent(state = ChatArchivePreview.loading())
}

@Preview(name = "Error — Light", group = "Light", showBackground = true, heightDp = 760)
@Composable
private fun ChatArchiveErrorLightPreview() = KnotworkTheme(darkTheme = false) {
    ChatArchiveContent(state = ChatArchivePreview.error())
}
