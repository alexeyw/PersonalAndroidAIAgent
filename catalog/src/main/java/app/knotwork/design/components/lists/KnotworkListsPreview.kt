package app.knotwork.design.components.lists

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import app.knotwork.design.components.chips.Status
import app.knotwork.design.icons.AppIcons
import app.knotwork.design.theme.KnotworkTheme

/**
 * Composable harness exercising every [PipelineListRow], [ToolListRow], and
 * [MemoryEntryRow] variant. Used by the Android Studio preview pane and by
 * the Roborazzi snapshot baseline so list-row regressions surface in the
 * same diff. Pipeline rows render one in idle and one in the swipe-revealed
 * state via the `revealed = true` programmatic override.
 *
 * Renders inside the parent [KnotworkTheme]; callers (preview / test) pin
 * `darkTheme` deterministically.
 */
@Composable
fun KnotworkListsCatalogContent() {
    Surface(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = KnotworkTheme.spacing.sp4),
            verticalArrangement = Arrangement.spacedBy(KnotworkTheme.spacing.sp2),
        ) {
            PipelineListRow(
                title = "Daily standup summariser",
                subtitle = "Run 12 min ago",
                status = Status.Success,
                leadingTint = KnotworkTheme.extended.nodeLiteRt,
                leadingIcon = AppIcons.NodeLite,
                onClick = {},
                onOverflow = {},
                onAction = {},
            )
            HorizontalDivider(color = KnotworkTheme.extended.divider)
            PipelineListRow(
                title = "Translate inbox",
                subtitle = "Run failed · 2h ago",
                status = Status.Error,
                leadingTint = KnotworkTheme.extended.nodeIntentRouter,
                leadingIcon = AppIcons.NodeIntentRouter,
                onClick = {},
                onOverflow = {},
                onAction = {},
                revealed = true,
            )
            HorizontalDivider(color = KnotworkTheme.extended.divider)
            ToolListRow(
                title = "Vector search",
                serverName = "knotwork.local · embeddings",
                connection = ConnectionStatus.Connected,
                leadingIcon = AppIcons.Brain,
                leadingTint = KnotworkTheme.extended.nodeLiteRt,
                onClick = {},
            )
            ToolListRow(
                title = "GitHub issues",
                serverName = "github · mcp",
                connection = ConnectionStatus.Syncing,
                leadingIcon = AppIcons.NodeTool,
                leadingTint = KnotworkTheme.extended.nodeTool,
                onClick = {},
            )
            ToolListRow(
                title = "Calendar",
                serverName = "google · cal-mcp",
                connection = ConnectionStatus.Disconnected,
                leadingIcon = AppIcons.NodeCloud,
                leadingTint = KnotworkTheme.extended.nodeCloud,
                onClick = {},
            )
            HorizontalDivider(color = KnotworkTheme.extended.divider)
            MemoryEntryRow(
                title = "User prefers dark theme",
                body = "Mentioned twice during onboarding; reaffirmed when asked about Settings.",
                tags = listOf("preference", "ui"),
                relevanceScore = "0.93",
                lastAccessed = "3 days ago",
                onClick = {},
            )
            MemoryEntryRow(
                title = "Reading list",
                body = "User keeps a per-week reading list of long-form articles, prefers to be reminded " +
                    "on Sunday evenings before the new week starts.",
                tags = listOf("habit", "reminder", "weekly"),
                relevanceScore = "0.81",
                lastAccessed = "today",
                onClick = {},
            )
        }
    }
}

/** Light-theme preview of list rows. */
@Preview(name = "List rows — Light", showBackground = true, heightDp = 1100)
@Composable
private fun KnotworkListsLightPreview() {
    KnotworkTheme(darkTheme = false) { KnotworkListsCatalogContent() }
}

/** Dark-theme preview of list rows. */
@Preview(name = "List rows — Dark", showBackground = true, heightDp = 1100)
@Composable
private fun KnotworkListsDarkPreview() {
    KnotworkTheme(darkTheme = true) { KnotworkListsCatalogContent() }
}
