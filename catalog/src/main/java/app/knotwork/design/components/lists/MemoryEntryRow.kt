package app.knotwork.design.components.lists

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import app.knotwork.design.components.chips.ChipStyle
import app.knotwork.design.components.chips.KnotworkChip
import app.knotwork.design.theme.KnotworkTheme
import app.knotwork.design.tokens.KnotworkTextStyles

/** Maximum lines of body text rendered before ellipsis. */
private const val MEMORY_BODY_MAX_LINES = 3

/**
 * Knotwork memory-entry row.
 *
 * Visual contract (see `compose/components/README.md` §List items):
 *  - Variable height (no fixed `height`); rows expand to fit body + tags.
 *  - `TitleMd` title, `BodyBase` body clamped to 3 lines with ellipsis,
 *    footer `FlowRow` containing tag chips + `MonoSm` relevance score +
 *    last-accessed timestamp.
 *  - 16 dp horizontal / 12 dp vertical padding; tap to open the entry detail
 *    sheet.
 *
 * @param title entry display title; rendered in `TitleMd`.
 * @param body entry body; clamped to 3 lines with ellipsis.
 * @param tags optional tag list rendered as Outline chips (`ChipStyle.Outline`).
 * @param relevanceScore optional relevance score (e.g. `"0.93"`); rendered
 * in `MonoSm`.
 * @param lastAccessed human-readable last-accessed string ("3 days ago").
 * @param onClick invoked when the user taps the row.
 * @param modifier optional layout modifier applied to the row root.
 */
@Composable
@Suppress("LongParameterList") // Stable API; collapsing into a `Row` data class hurts call-site clarity.
fun MemoryEntryRow(
    title: String,
    body: String,
    tags: List<String>,
    relevanceScore: String?,
    lastAccessed: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(KnotworkTheme.spacing.sp2),
        modifier = modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface)
            .clickable(onClick = onClick, role = Role.Button)
            .padding(
                horizontal = KnotworkTheme.spacing.sp4,
                vertical = KnotworkTheme.spacing.sp3,
            ),
    ) {
        Text(
            text = title,
            style = KnotworkTextStyles.TitleMd,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = body,
            style = KnotworkTextStyles.BodyBase,
            color = KnotworkTheme.extended.onSurface2,
            maxLines = MEMORY_BODY_MAX_LINES,
            overflow = TextOverflow.Ellipsis,
        )
        if (tags.isNotEmpty()) {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(KnotworkTheme.spacing.sp1),
                verticalArrangement = Arrangement.spacedBy(KnotworkTheme.spacing.sp1),
            ) {
                tags.forEach { tag -> KnotworkChip(label = tag, style = ChipStyle.Outline) }
            }
        }
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(KnotworkTheme.spacing.sp2),
        ) {
            if (relevanceScore != null) {
                Text(
                    text = relevanceScore,
                    style = KnotworkTextStyles.MonoSm,
                    color = KnotworkTheme.extended.onSurfaceMuted,
                )
                Text(
                    text = "·",
                    style = KnotworkTextStyles.Caption,
                    color = KnotworkTheme.extended.onSurfaceDim,
                )
            }
            Text(
                text = lastAccessed,
                style = KnotworkTextStyles.Caption,
                color = KnotworkTheme.extended.onSurfaceMuted,
            )
        }
    }
}
