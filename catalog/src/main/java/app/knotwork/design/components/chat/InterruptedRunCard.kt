package app.knotwork.design.components.chat

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import app.knotwork.design.components.buttons.KnotworkPrimaryButton
import app.knotwork.design.components.buttons.KnotworkSecondaryButton
import app.knotwork.design.icons.AppIcons
import app.knotwork.design.theme.KnotworkTheme
import app.knotwork.design.tokens.KnotworkTextStyles

/** Card horizontal/vertical padding inside the container. */
private val CardPadding = 16.dp

/** Diameter of the leading status glyph rendered next to the header label. */
private val HeaderIconSize = 18.dp

/**
 * Knotwork interrupted-run card — status surface rendered inline in the chat
 * stream when the session's most recent pipeline run died with the process
 * (Doze, OOM kill, swipe from recents) instead of finishing:
 *
 *  - **Container.** Rounded `surface1` tile with a muted outline so the card
 *    reads as a system status, not an agent message — unlike the cream
 *    clarification card or the risk-tinted HITL card, an interruption is
 *    neither a question nor a permission gate.
 *  - **Header row.** Hourglass glyph tinted `onSurfaceMuted`, followed by the
 *    bold "Run interrupted" label.
 *  - **Body.** "Execution was interrupted at node …" with the host-resolved
 *    node label.
 *  - **Action row.** Primary **Resume** CTA (continue from the last completed
 *    node) and a secondary **Discard** CTA (dismiss the run for good). When
 *    [InterruptedRunCardModel.resumable] is `false` the Resume CTA is hidden,
 *    an explanatory "can no longer be resumed" note is appended to the body,
 *    and Discard remains the only action.
 *
 * Stateless: both CTAs only dispatch the supplied callbacks; the host owns
 * the resume/discard semantics and removes the card by dropping the row from
 * the message list.
 *
 * @param model immutable card payload (resolved node label + resumability).
 * @param onResume invoked when the user taps the Resume CTA.
 * @param onDiscard invoked when the user taps the Discard CTA.
 * @param modifier optional layout modifier applied to the card root.
 */
@Composable
fun InterruptedRunCard(
    model: InterruptedRunCardModel,
    onResume: () -> Unit,
    onDiscard: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(KnotworkTheme.spacing.sp2),
        modifier = modifier
            .fillMaxWidth()
            .clip(KnotworkTheme.shapes.md)
            .background(color = KnotworkTheme.extended.surface1)
            .border(
                border = BorderStroke(width = 1.dp, color = KnotworkTheme.extended.outlineStrong),
                shape = KnotworkTheme.shapes.md,
            )
            .padding(CardPadding),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(KnotworkTheme.spacing.sp2),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Icon(
                imageVector = AppIcons.Hourglass,
                contentDescription = null,
                tint = KnotworkTheme.extended.onSurfaceMuted,
                modifier = Modifier.size(HeaderIconSize),
            )
            Text(
                text = stringResource(R.string.knotwork_interrupted_run_header),
                style = KnotworkTextStyles.TitleMd.copy(fontWeight = FontWeight.SemiBold),
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
        Text(
            text = stringResource(R.string.knotwork_interrupted_run_body, model.nodeLabel),
            style = KnotworkTextStyles.BodyBase,
            color = MaterialTheme.colorScheme.onSurface,
        )
        if (!model.resumable) {
            Text(
                text = stringResource(R.string.knotwork_interrupted_run_expired_note),
                style = KnotworkTextStyles.BodyBase,
                color = KnotworkTheme.extended.onSurfaceMuted,
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(KnotworkTheme.spacing.sp2)) {
            if (model.resumable) {
                KnotworkPrimaryButton(
                    text = stringResource(R.string.knotwork_interrupted_run_resume),
                    onClick = onResume,
                )
            }
            KnotworkSecondaryButton(
                text = stringResource(R.string.knotwork_interrupted_run_discard),
                onClick = onDiscard,
            )
        }
    }
}
