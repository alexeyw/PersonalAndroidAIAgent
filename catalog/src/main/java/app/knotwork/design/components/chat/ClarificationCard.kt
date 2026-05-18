package app.knotwork.design.components.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.knotwork.design.R
import app.knotwork.design.components.chips.ChipStyle
import app.knotwork.design.components.chips.KnotworkChip
import app.knotwork.design.theme.KnotworkTheme
import app.knotwork.design.tokens.KnotworkPalette
import app.knotwork.design.tokens.KnotworkTextStyles

/** Card horizontal/vertical padding inside the cream container. */
private val CardPadding = 16.dp

/** Diameter of the leading spinner glyph rendered next to the header label. */
private val HeaderIconSize = 18.dp

/**
 * Knotwork clarification card — pinned-prompt surface rendered inline in
 * the assistant message stream while the agent is waiting on a typed
 * answer. Mirrors the spec mockup (Phase 21 / Task 10 follow-up):
 *
 *  - **Container.** Rounded `Accent50` tile so the prompt stands apart
 *    from the surrounding assistant bubble without competing with the
 *    risk-coloured HITL surface.
 *  - **Header row.** Spinner-shaped glyph (`AutoAwesome`) tinted brand
 *    primary, followed by the bold "Quick question from the agent"
 *    label.
 *  - **Question.** `BodyBase`, full-width, no clamp.
 *  - **Quick-reply chips.** `FlowRow` of `KnotworkChip(style = Tonal)`.
 *    The chip matching the user's already-submitted answer renders in
 *    its `selected` state; the rest stay inactive.
 *
 * The free-form text field has been removed — the user composes typed
 * answers in the main composer below the chat. Once the user replies
 * the card collapses to the existing `Replied: …` summary so the
 * conversation history reads cleanly.
 *
 * @param model immutable card payload.
 * @param onReply invoked with the chosen quick-reply label.
 * @param modifier optional layout modifier applied to the card root.
 */
@Composable
fun ClarificationCard(model: ClarificationCardModel, onReply: (String) -> Unit, modifier: Modifier = Modifier) {
    if (model.replied != null) {
        RepliedSummary(text = model.replied, modifier = modifier)
        return
    }
    Column(
        verticalArrangement = Arrangement.spacedBy(KnotworkTheme.spacing.sp2),
        modifier = modifier
            .fillMaxWidth()
            .clip(KnotworkTheme.shapes.md)
            .background(color = KnotworkPalette.Accent50)
            .padding(CardPadding),
    ) {
        HeaderRow()
        Text(
            text = model.question,
            style = KnotworkTextStyles.BodyBase,
            color = MaterialTheme.colorScheme.onSurface,
        )
        if (model.quickReplies.isNotEmpty()) {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(KnotworkTheme.spacing.sp2),
                verticalArrangement = Arrangement.spacedBy(KnotworkTheme.spacing.sp2),
            ) {
                model.quickReplies.forEach { reply ->
                    KnotworkChip(
                        label = reply,
                        onClick = { onReply(reply) },
                        style = ChipStyle.Tonal,
                    )
                }
            }
        }
    }
}

/** Spinner glyph + header label aligned at the top of the card. */
@Composable
private fun HeaderRow() {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(KnotworkTheme.spacing.sp2),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.size(HeaderIconSize),
        ) {
            Icon(
                imageVector = Icons.Outlined.AutoAwesome,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(HeaderIconSize),
            )
        }
        Text(
            text = stringResource(R.string.knotwork_clarification_header),
            style = KnotworkTextStyles.TitleMd.copy(fontWeight = FontWeight.SemiBold),
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

/** Post-reply summary — `"Replied: …"` with a check glyph, no input affordance. */
@Composable
private fun RepliedSummary(text: String, modifier: Modifier = Modifier) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(KnotworkTheme.spacing.sp1),
        modifier = modifier.padding(vertical = KnotworkTheme.spacing.sp1),
    ) {
        Icon(
            imageVector = Icons.Outlined.Check,
            contentDescription = null,
            tint = KnotworkTheme.extended.onSurfaceMuted,
            modifier = Modifier.size(KnotworkTheme.spacing.sp4),
        )
        Text(
            text = stringResource(R.string.knotwork_clarification_replied, text),
            style = KnotworkTextStyles.BodySm,
            color = KnotworkTheme.extended.onSurfaceMuted,
        )
    }
}
