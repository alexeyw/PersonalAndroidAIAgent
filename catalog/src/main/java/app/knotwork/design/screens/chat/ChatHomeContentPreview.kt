package app.knotwork.design.screens.chat

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import app.knotwork.design.theme.KnotworkTheme

/**
 * Android Studio previews for every documented [ChatHomeVisualState]. The
 * Roborazzi snapshot suite re-runs the same setups via
 * `ChatHomeContent(state = …)` so any regression surfaces in one diff.
 *
 * Light + dark variants share the same fixtures; the preview group label
 * doubles as the snapshot file name suffix in [ChatHomeContentSnapshotTest].
 */
@Preview(name = "Empty — Light", group = "Light", showBackground = true, heightDp = 760)
@Composable
private fun ChatHomeEmptyLightPreview() = KnotworkTheme(darkTheme = false) {
    ChatHomeContent(state = ChatHomePreview.empty())
}

@Preview(name = "Empty — Dark", group = "Dark", showBackground = true, heightDp = 760)
@Composable
private fun ChatHomeEmptyDarkPreview() = KnotworkTheme(darkTheme = true) {
    ChatHomeContent(state = ChatHomePreview.empty())
}

@Preview(name = "Idle — Light", group = "Light", showBackground = true, heightDp = 760)
@Composable
private fun ChatHomeIdleLightPreview() = KnotworkTheme(darkTheme = false) {
    ChatHomeContent(state = ChatHomePreview.idle())
}

@Preview(name = "Idle — Dark", group = "Dark", showBackground = true, heightDp = 760)
@Composable
private fun ChatHomeIdleDarkPreview() = KnotworkTheme(darkTheme = true) {
    ChatHomeContent(state = ChatHomePreview.idle())
}

@Preview(name = "Generating — Light", group = "Light", showBackground = true, heightDp = 760)
@Composable
private fun ChatHomeGeneratingLightPreview() = KnotworkTheme(darkTheme = false) {
    ChatHomeContent(state = ChatHomePreview.generating())
}

@Preview(name = "Generating — Dark", group = "Dark", showBackground = true, heightDp = 760)
@Composable
private fun ChatHomeGeneratingDarkPreview() = KnotworkTheme(darkTheme = true) {
    ChatHomeContent(state = ChatHomePreview.generating())
}

@Preview(name = "HITL Confirm — Light", group = "Light", showBackground = true, heightDp = 900)
@Composable
private fun ChatHomeHitlLightPreview() = KnotworkTheme(darkTheme = false) {
    ChatHomeContent(state = ChatHomePreview.hitlConfirm())
}

@Preview(name = "HITL Confirm — Dark", group = "Dark", showBackground = true, heightDp = 900)
@Composable
private fun ChatHomeHitlDarkPreview() = KnotworkTheme(darkTheme = true) {
    ChatHomeContent(state = ChatHomePreview.hitlConfirm())
}

@Preview(name = "Clarification — Light", group = "Light", showBackground = true, heightDp = 800)
@Composable
private fun ChatHomeClarificationLightPreview() = KnotworkTheme(darkTheme = false) {
    ChatHomeContent(state = ChatHomePreview.clarification())
}

@Preview(name = "Clarification — Dark", group = "Dark", showBackground = true, heightDp = 800)
@Composable
private fun ChatHomeClarificationDarkPreview() = KnotworkTheme(darkTheme = true) {
    ChatHomeContent(state = ChatHomePreview.clarification())
}

@Preview(name = "Error — Light", group = "Light", showBackground = true, heightDp = 800)
@Composable
private fun ChatHomeErrorLightPreview() = KnotworkTheme(darkTheme = false) {
    ChatHomeContent(state = ChatHomePreview.error())
}

@Preview(name = "Error — Dark", group = "Dark", showBackground = true, heightDp = 800)
@Composable
private fun ChatHomeErrorDarkPreview() = KnotworkTheme(darkTheme = true) {
    ChatHomeContent(state = ChatHomePreview.error())
}

@Preview(name = "Drawer Open — Light", group = "Light", showBackground = true, heightDp = 760)
@Composable
private fun ChatHomeDrawerLightPreview() = KnotworkTheme(darkTheme = false) {
    ChatHomeContent(state = ChatHomePreview.drawerOpen())
}

@Preview(name = "Drawer Open — Dark", group = "Dark", showBackground = true, heightDp = 760)
@Composable
private fun ChatHomeDrawerDarkPreview() = KnotworkTheme(darkTheme = true) {
    ChatHomeContent(state = ChatHomePreview.drawerOpen())
}

@Preview(name = "Console Expanded — Light", group = "Light", showBackground = true, heightDp = 760)
@Composable
private fun ChatHomeConsoleLightPreview() = KnotworkTheme(darkTheme = false) {
    ChatHomeContent(state = ChatHomePreview.consoleExpanded())
}

@Preview(name = "Console Expanded — Dark", group = "Dark", showBackground = true, heightDp = 760)
@Composable
private fun ChatHomeConsoleDarkPreview() = KnotworkTheme(darkTheme = true) {
    ChatHomeContent(state = ChatHomePreview.consoleExpanded())
}
