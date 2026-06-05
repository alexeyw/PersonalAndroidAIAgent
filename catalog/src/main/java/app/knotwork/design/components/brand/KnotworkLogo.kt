@file:Suppress("MatchingDeclarationName") // Hosts KnotworkLogo + KnotworkLogoSize enum + the icon-tile variant.

package app.knotwork.design.components.brand

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.knotwork.design.icons.AppIcons
import app.knotwork.design.theme.KnotworkTheme

/**
 * Visual size of the Knotwork brand mark. Translates to a square canvas
 * whose side length is [size]; the mark is drawn centred inside it.
 */
enum class KnotworkLogoSize(val size: Dp) {
    /** 32 dp — inline rows (e.g. TopAppBar leading). */
    Sm(LOGO_SM),

    /** 64 dp — default for screen hero / drawer header. */
    Md(LOGO_MD),

    /** 128 dp — large splash / onboarding hero. */
    Lg(LOGO_LG),
}

private val LOGO_SM = 32.dp
private val LOGO_MD = 64.dp
private val LOGO_LG = 128.dp

/** Tile corner radius as a fraction of the tile side (~0.25·size). */
private const val TILE_CORNER_FRACTION = 0.25f

/** Mark inset inside the icon tile (fraction of side ≈ launcher safe-zone 66/108). */
private const val TILE_MARK_FRACTION = 0.6f

/**
 * Knotwork brand **logo** — the bare canonical mark ([AppIcons.Mark]: two nodes
 * joined by one edge), no plate, tinted [tint] (defaults to `primary`).
 *
 * This is the single brand glyph — splash, onboarding, empty
 * states and headers all render it via this composable, so there is exactly one
 * mark across the app and it matches the launcher icon. Use [KnotworkAppIconTile]
 * for the plated app-icon presentation (About row, share cards).
 *
 * @param modifier additional layout modifier applied to the glyph.
 * @param size visual size token; defaults to [KnotworkLogoSize.Md].
 * @param tint mark colour; defaults to `MaterialTheme.colorScheme.primary`.
 */
@Composable
fun KnotworkLogo(
    modifier: Modifier = Modifier,
    size: KnotworkLogoSize = KnotworkLogoSize.Md,
    tint: Color = MaterialTheme.colorScheme.primary,
) {
    Icon(
        imageVector = AppIcons.Mark,
        contentDescription = null,
        tint = tint,
        modifier = modifier.size(size.size),
    )
}

/**
 * Knotwork **app-icon** presentation — the white [AppIcons.Mark] on a rounded
 * `primary` tile, mirroring the launcher adaptive icon 1:1.
 * Used in the About hero / row and share cards.
 *
 * @param modifier additional layout modifier applied to the tile.
 * @param size visual size token; defaults to [KnotworkLogoSize.Md].
 */
@Composable
fun KnotworkAppIconTile(modifier: Modifier = Modifier, size: KnotworkLogoSize = KnotworkLogoSize.Md) {
    val sideDp = size.size
    Box(
        modifier = modifier
            .size(sideDp)
            .clip(RoundedCornerShape(sideDp * TILE_CORNER_FRACTION))
            .background(MaterialTheme.colorScheme.primary),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = AppIcons.Mark,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onPrimary,
            modifier = Modifier.size(sideDp * TILE_MARK_FRACTION),
        )
    }
}

/** Convenience preview default for non-snapshot call sites that just want a tinted brand. */
@Suppress("unused") // Public API for downstream consumers (about / splash mappers).
@Composable
fun knotworkBrandTint(): Color = KnotworkTheme.extended.onSurface2
