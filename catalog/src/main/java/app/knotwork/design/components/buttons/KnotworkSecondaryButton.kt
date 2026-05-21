package app.knotwork.design.components.buttons

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import app.knotwork.design.theme.KnotworkTheme
import app.knotwork.design.tokens.KnotworkTextStyles

/** Secondary-button visual height; touch target stays ≥ 48 dp via `defaultMinSize`. */
private val SecondaryButtonHeight = 48.dp

/** Diameter of the loading indicator overlaid on top of the label. */
private val LoadingIndicatorSize = 16.dp

/** Alpha applied to the label while the button is in the loading state. */
private const val LOADING_LABEL_ALPHA = 0.3f

/**
 * Knotwork secondary button — transparent container with a 1 dp outline.
 *
 * Visual contract (see `compose/components/README.md` §Buttons):
 *  - 48 dp tall, container `Color.Transparent`, 1 dp `outline` border, label
 *    `LabelLg`, shape `KnotworkTheme.shapes.md`.
 *  - Pressed state inherits Material's ripple over `extended.surface2`.
 *  - **destructive** swaps the label and outline to `extended.riskDestructive`
 *    so it can host the HITL "Reject" action.
 *  - Loading and disabled states mirror [KnotworkPrimaryButton].
 *
 * @param text user-visible label.
 * @param onClick invoked on tap; gated to no-op when [enabled] is `false` or
 * [loading] is `true`.
 * @param modifier optional layout modifier applied to the button root.
 * @param enabled when `false`, the button is non-interactive and renders the
 * disabled palette.
 * @param loading when `true`, the button is non-interactive, the label fades,
 * and a circular progress indicator overlays the centre.
 * @param destructive when `true`, recolours the label and outline with
 * `extended.riskDestructive`; reserved for explicit destructive actions
 * (HITL `Reject`, "Delete pipeline", …).
 * @param leadingIcon optional vector rendered before the label (16 dp).
 */
@Composable
fun KnotworkSecondaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    loading: Boolean = false,
    destructive: Boolean = false,
    leadingIcon: ImageVector? = null,
) {
    val effectiveEnabled = enabled && !loading
    val accent = if (destructive) {
        KnotworkTheme.extended.riskDestructive
    } else {
        KnotworkTheme.extended.onSurface2
    }
    val borderColor = if (effectiveEnabled) accent else KnotworkTheme.extended.outlineStrong
    OutlinedButton(
        onClick = onClick,
        enabled = effectiveEnabled,
        shape = KnotworkTheme.shapes.md,
        border = BorderStroke(width = 1.dp, color = borderColor),
        colors = ButtonDefaults.outlinedButtonColors(
            containerColor = Color.Transparent,
            contentColor = accent,
            disabledContainerColor = Color.Transparent,
            disabledContentColor = KnotworkTheme.extended.onSurfaceDim,
        ),
        modifier = modifier
            .defaultMinSize(minHeight = SecondaryButtonHeight)
            .height(SecondaryButtonHeight)
            .semantics { if (loading) stateDescription = "Loading" },
    ) {
        Box(contentAlignment = Alignment.Center) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(KnotworkTheme.spacing.sp2),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.alpha(if (loading) LOADING_LABEL_ALPHA else 1f),
            ) {
                if (leadingIcon != null) {
                    Icon(
                        imageVector = leadingIcon,
                        contentDescription = null,
                        modifier = Modifier.size(LoadingIndicatorSize),
                    )
                }
                Text(
                    text = text,
                    style = KnotworkTextStyles.LabelLg,
                    maxLines = 1,
                    softWrap = false,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                )
            }
            if (loading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(LoadingIndicatorSize),
                    color = accent,
                    strokeWidth = 2.dp,
                )
            }
        }
    }
}
