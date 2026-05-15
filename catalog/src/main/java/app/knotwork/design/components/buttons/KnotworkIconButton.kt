package app.knotwork.design.components.buttons

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import app.knotwork.design.theme.KnotworkTheme
import app.knotwork.design.tokens.KnotworkPalette
import app.knotwork.design.tokens.KnotworkTextStyles

/** Visual size of the icon-button target (touch target stays 48 dp via Material's default). */
private val IconButtonVisualSize = 40.dp

/** Glyph diameter inside the icon-button. */
private val IconGlyphSize = 24.dp

/** Diameter of the badge bubble overlaid on the top-right of the icon-button. */
private val BadgeDiameter = 16.dp

/** Maximum integer rendered inside the badge before it switches to "9+". */
private const val BADGE_OVERFLOW_THRESHOLD = 9

/**
 * Knotwork icon button — square 40 dp visual with an optional badge.
 *
 * Visual contract (see `compose/components/README.md` §Buttons):
 *  - 40 dp square visual; touch target stays 48 × 48 via Material's default
 *    `IconButton` minimum interactive size.
 *  - Badge is rendered top-right when [badge] is non-null and `> 0`. Background
 *    `KnotworkPalette.Accent500`, label `Caption` in `onPrimary`. Values
 *    above [BADGE_OVERFLOW_THRESHOLD] render as `"9+"`.
 *  - The badge ignores touches; the underlying [IconButton] receives the click.
 *
 * @param onClick invoked on tap; gated to no-op when [enabled] is `false`.
 * @param contentDescription required short description of the action ("Open
 * console", "Send message"). Forwarded to the [Icon].
 * @param icon vector glyph rendered at 24 dp.
 * @param modifier optional layout modifier applied to the button root.
 * @param enabled when `false`, the button is non-interactive and the glyph
 * uses Material's disabled tint.
 * @param badge optional unread / pending count rendered as a top-right
 * bubble. Hidden when `null` or `<= 0`.
 */
@Composable
fun KnotworkIconButton(
    onClick: () -> Unit,
    contentDescription: String,
    icon: ImageVector,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    badge: Int? = null,
) {
    Box(modifier = modifier) {
        IconButton(
            onClick = onClick,
            enabled = enabled,
            colors = IconButtonDefaults.iconButtonColors(
                contentColor = MaterialTheme.colorScheme.onSurface,
                disabledContentColor = KnotworkTheme.extended.onSurfaceDim,
            ),
            modifier = Modifier
                .defaultMinSize(minWidth = IconButtonVisualSize, minHeight = IconButtonVisualSize)
                .size(IconButtonVisualSize),
        ) {
            Icon(
                imageVector = icon,
                contentDescription = contentDescription,
                modifier = Modifier.size(IconGlyphSize),
            )
        }
        if (badge != null && badge > 0) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .sizeIn(minWidth = BadgeDiameter, minHeight = BadgeDiameter)
                    .background(color = KnotworkPalette.Accent500, shape = CircleShape)
                    .padding(horizontal = 4.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = if (badge > BADGE_OVERFLOW_THRESHOLD) "9+" else badge.toString(),
                    style = KnotworkTextStyles.Caption,
                    color = MaterialTheme.colorScheme.onPrimary,
                )
            }
        }
    }
}
