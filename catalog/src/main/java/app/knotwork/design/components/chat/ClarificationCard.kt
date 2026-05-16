package app.knotwork.design.components.chat

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import app.knotwork.design.R
import app.knotwork.design.components.chips.ChipStyle
import app.knotwork.design.components.chips.KnotworkChip
import app.knotwork.design.theme.KnotworkTheme
import app.knotwork.design.tokens.KnotworkTextStyles

/**
 * Knotwork clarification card — surfaces a clarifying question inside the
 * assistant bubble with quick-reply chips and a free-form text field.
 *
 * Visual contract (`compose/components/README.md` §Chat surface
 * §ClarificationCard):
 *  - **Question** — `BodyBase`, wraps full text (no clamp).
 *  - **Quick replies** — `FlowRow` of `KnotworkChip(style = Tonal)` rendered
 *    when `model.quickReplies` is non-empty. Tapping a chip submits its
 *    label as the reply.
 *  - **Free-form row** — single-line `OutlinedTextField` with
 *    `ImeAction.Send` and a trailing arrow-up icon button.
 *  - When `model.replied` is non-null the card collapses to a one-line
 *    `"Replied: …"` summary and stops accepting input.
 *
 * **Stateless** — the free-form value is hoisted internally to a `remember`
 * because the catalog component does not need to persist drafts across the
 * `replied → reset` transition (the screen rebuilds the card with a fresh
 * model after the user submits). The `onReply` callback is the single
 * source of truth for the submitted answer.
 *
 * @param model immutable card payload.
 * @param onReply invoked with the user's chosen / typed answer.
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
        modifier = modifier.fillMaxWidth(),
    ) {
        Text(
            text = model.question,
            style = KnotworkTextStyles.BodyBase,
            color = MaterialTheme.colorScheme.onSurface,
        )
        if (model.quickReplies.isNotEmpty()) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(KnotworkTheme.spacing.sp2)) {
                model.quickReplies.forEach { reply ->
                    KnotworkChip(
                        label = reply,
                        onClick = { onReply(reply) },
                        style = ChipStyle.Tonal,
                    )
                }
            }
        }
        val placeholder = model.freeformPlaceholder
            ?: stringResource(R.string.knotwork_clarification_freeform_default)
        FreeformRow(placeholder = placeholder, onSubmit = onReply)
    }
}

/** Bottom row of the unanswered card — text input + trailing send affordance. */
@Composable
private fun FreeformRow(placeholder: String, onSubmit: (String) -> Unit) {
    var draft by remember { mutableStateOf("") }
    val canSubmit = draft.isNotBlank()
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        OutlinedTextField(
            value = draft,
            onValueChange = { draft = it },
            placeholder = {
                Text(text = placeholder, color = KnotworkTheme.extended.onSurfaceDim)
            },
            singleLine = true,
            textStyle = KnotworkTextStyles.BodyBase,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
            keyboardActions = KeyboardActions(
                onSend = {
                    if (canSubmit) {
                        onSubmit(draft.trim())
                        draft = ""
                    }
                },
            ),
            modifier = Modifier.weight(1f),
        )
        IconButton(
            onClick = {
                if (canSubmit) {
                    onSubmit(draft.trim())
                    draft = ""
                }
            },
            enabled = canSubmit,
        ) {
            Icon(
                imageVector = Icons.Filled.ArrowUpward,
                contentDescription = stringResource(R.string.knotwork_clarification_send),
                tint = if (canSubmit) {
                    MaterialTheme.colorScheme.primary
                } else {
                    KnotworkTheme.extended.onSurfaceDim
                },
            )
        }
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
