package app.knotwork.design.components.buttons

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.knotwork.design.a11y.MinTouchTarget
import app.knotwork.design.theme.KnotworkTheme
import app.knotwork.design.tokens.KnotworkIconSizes
import app.knotwork.design.tokens.KnotworkTextStyles

/** Glyph diameter inside the icon-button (22 dp). */
private val IconGlyphSize = KnotworkIconSizes.AppBar

/** Diameter of the badge bubble overlaid on the top-right of the icon-button (14 dp). */
private val BadgeDiameter = 14.dp

/** Maximum integer rendered inside the badge before it switches to "9+". */
private const val BADGE_OVERFLOW_THRESHOLD = 9

/**
 * Knotwork icon button — square 48 dp with an optional badge.
 *
 * Visual contract:
 *  - 48 × 48 dp square, which is both the visual and the touch target. This
 *    Material line lays `IconButton` out at 40 dp and applies no minimum
 *    interactive size of its own, so the size is pinned here explicitly.
 *  - Badge is rendered top-right when [badge] is non-null and `> 0`. Background
 *    `primary`, label Inter 700 10 px in `onPrimary`. Values
 *    above [BADGE_OVERFLOW_THRESHOLD] render as `"9+"`.
 *  - The badge ignores touches; the underlying [IconButton] receives the click.
 *
 * @param onClick invoked on tap; gated to no-op when [enabled] is `false`.
 * @param contentDescription required short description of the action ("Open
 * console", "Send message"). Forwarded to the [Icon].
 * @param icon vector glyph rendered at 22 dp.
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
            // Pinned to the floor. This used to be `.size(40.dp)` while the
            // KDoc promised "touch target stays 48 dp via Material's default" —
            // but this Material line lays `IconButton` out at 40 dp and applies
            // no minimum of its own, so the promise was never kept.
            //
            // The same is true of every *bare* `IconButton` in the catalog: 40
            // dp, 8 dp short, and not fixed here because that is a design-system
            // decision about which component screens should reach for.
            modifier = Modifier.size(MinTouchTarget),
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
                    .background(color = MaterialTheme.colorScheme.primary, shape = CircleShape)
                    .padding(horizontal = 4.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    // Inter 700, 10 px.
                    text = if (badge > BADGE_OVERFLOW_THRESHOLD) "9+" else badge.toString(),
                    style = KnotworkTextStyles.LabelSm.copy(fontSize = 10.sp, fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onPrimary,
                )
            }
        }
    }
}
