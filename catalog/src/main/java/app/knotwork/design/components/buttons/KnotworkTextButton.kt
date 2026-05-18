package app.knotwork.design.components.buttons

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import app.knotwork.design.theme.KnotworkTheme
import app.knotwork.design.tokens.KnotworkTextStyles

/** Text-button visual height; touch target stays ≥ 48 dp via `defaultMinSize`. */
private val TextButtonHeight = 48.dp

/** Diameter of the leading icon when supplied. */
private val LeadingIconSize = 16.dp

/**
 * Knotwork text button — chrome-less, label-only.
 *
 * Visual contract (see `compose/components/README.md` §Buttons):
 *  - Label `LabelLg`, colour defaults to `extended.onSurface2`.
 *  - When [destructive] is `true`, the label switches to
 *    `extended.riskDestructive` — used inline next to the HITL Sensitive
 *    "Always allow" chip and for "Delete" / "Cancel" affordances inside
 *    bottom sheets.
 *  - Touch target floors at 48 dp, the visual hugs the text.
 *
 * @param text user-visible label.
 * @param onClick invoked on tap; gated to no-op when [enabled] is `false`.
 * @param modifier optional layout modifier applied to the button root.
 * @param enabled when `false`, the button is non-interactive and renders the
 * disabled palette.
 * @param destructive when `true`, recolours the label with
 * `extended.riskDestructive`.
 * @param leadingIcon optional vector rendered before the label (16 dp).
 */
@Composable
fun KnotworkTextButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    destructive: Boolean = false,
    leadingIcon: ImageVector? = null,
) {
    val accent = if (destructive) {
        KnotworkTheme.extended.riskDestructive
    } else {
        KnotworkTheme.extended.onSurface2
    }
    TextButton(
        onClick = onClick,
        enabled = enabled,
        shape = KnotworkTheme.shapes.md,
        colors = ButtonDefaults.textButtonColors(
            containerColor = Color.Transparent,
            contentColor = accent,
            disabledContainerColor = Color.Transparent,
            disabledContentColor = KnotworkTheme.extended.onSurfaceDim,
        ),
        modifier = modifier.defaultMinSize(minHeight = TextButtonHeight),
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(KnotworkTheme.spacing.sp2),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (leadingIcon != null) {
                Icon(
                    imageVector = leadingIcon,
                    contentDescription = null,
                    modifier = Modifier.size(LeadingIconSize),
                )
            }
            Text(text = text, style = KnotworkTextStyles.LabelLg)
        }
    }
}
