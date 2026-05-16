package app.knotwork.design.components.buttons

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import app.knotwork.design.theme.KnotworkTheme
import app.knotwork.design.tokens.KnotworkTextStyles

/** Primary-button visual height (touch target stays ≥ 48 dp via `defaultMinSize`). */
private val PrimaryButtonHeight = 48.dp

/** Diameter of the loading indicator overlaid on top of the label. */
private val LoadingIndicatorSize = 16.dp

/** Alpha applied to the label while the button is in the loading state. */
private const val LOADING_LABEL_ALPHA = 0.3f

/**
 * Knotwork brand primary button.
 *
 * Visual contract (see `compose/components/README.md` §Buttons):
 *  - 48 dp tall, container `MaterialTheme.colorScheme.primary`, label
 *    `LabelLg`, shape `KnotworkTheme.shapes.md`.
 *  - **Loading**: label fades to alpha 0.3, a 16 dp `CircularProgressIndicator`
 *    overlays the centre, click is a no-op (the underlying [Button] is
 *    disabled while loading and re-styled to keep the resting visual).
 *  - **Disabled**: container `extended.surface3`, label `extended.onSurfaceDim`,
 *    no elevation in either state.
 *  - Touch target stays 48 × 48 even when the visual is shorter.
 *
 * @param text user-visible label; passed verbatim to [Text].
 * @param onClick invoked on tap; gated to no-op when [enabled] is `false` or
 * [loading] is `true`.
 * @param modifier optional layout modifier applied to the underlying [Button].
 * @param enabled when `false`, the button is non-interactive and renders the
 * disabled palette.
 * @param loading when `true`, the button is non-interactive, the label fades,
 * and a circular progress indicator overlays the centre.
 * @param leadingIcon optional vector rendered before the label (16 dp); use
 * for "Send", "Save & continue", etc.
 */
@Composable
fun KnotworkPrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    loading: Boolean = false,
    leadingIcon: ImageVector? = null,
) {
    val effectiveEnabled = enabled && !loading
    Button(
        onClick = onClick,
        enabled = effectiveEnabled,
        shape = KnotworkTheme.shapes.md,
        elevation = ButtonDefaults.buttonElevation(
            defaultElevation = 0.dp,
            pressedElevation = 0.dp,
            focusedElevation = 0.dp,
            hoveredElevation = 0.dp,
            disabledElevation = 0.dp,
        ),
        colors = ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary,
            disabledContainerColor = KnotworkTheme.extended.surface3,
            disabledContentColor = KnotworkTheme.extended.onSurfaceDim,
        ),
        modifier = modifier
            .defaultMinSize(minHeight = PrimaryButtonHeight)
            .height(PrimaryButtonHeight)
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
                Text(text = text, style = KnotworkTextStyles.LabelLg)
            }
            if (loading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(LoadingIndicatorSize),
                    color = MaterialTheme.colorScheme.onPrimary,
                    strokeWidth = 2.dp,
                )
            }
        }
    }
}
