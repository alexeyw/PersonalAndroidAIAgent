package app.knotwork.design.components.chips

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.GppMaybe
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import app.knotwork.design.theme.KnotworkTheme
import app.knotwork.design.tokens.KnotworkTextStyles

/** Pill visual height. */
private val RiskPillHeight = 24.dp

/** Diameter of the leading risk glyph. */
private val RiskPillIconSize = 14.dp

/**
 * Per-tool risk classification used by HITL prompts and agent dashboards.
 *
 * Maps to `extended.risk*` colours; never re-themed by accent (the colour
 * conveys state, not aesthetic — see `decisions.md §14`).
 */
enum class Risk {
    /** Auto-approved read-only tool. Visualised with `Visibility` glyph. */
    Readonly,

    /** User opt-in tool with a "Always allow" affordance. Visualised with `WarningAmber`. */
    Sensitive,

    /** High-risk tool requiring typed confirmation. Visualised with `GppMaybe`. */
    Destructive,
}

/**
 * Knotwork risk pill — colour + glyph + label triple identifying the risk
 * tier of an HITL prompt or a tool entry in the Tools screen.
 *
 * Visual contract (see `compose/components/README.md` §Chips & pills and
 * `icons/icon-mapping.md` for the glyph mapping):
 *  - 24 dp tall, shape `KnotworkTheme.shapes.full`, horizontal padding 8 dp.
 *  - Container colour from `extended.risk*`; foreground stays `onPrimary`
 *    (a light tone) so the label reads on every risk hue.
 *  - Glyph paired with the label so colour is never the only signal
 *    (`decisions.md §14`).
 *  - `contentDescription` reads `"Risk level: <level>"`.
 *
 * @param risk risk tier driving colour + glyph + label.
 * @param modifier optional layout modifier applied to the pill root.
 */
@Composable
fun RiskPill(risk: Risk, modifier: Modifier = Modifier) {
    val container = riskContainerColor(risk)
    val foreground = MaterialTheme.colorScheme.onPrimary
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(KnotworkTheme.spacing.sp1),
        modifier = modifier
            .height(RiskPillHeight)
            .background(color = container, shape = KnotworkTheme.shapes.full)
            .padding(horizontal = KnotworkTheme.spacing.sp2)
            .semantics { contentDescription = "Risk level: ${risk.label}" },
    ) {
        Icon(
            imageVector = riskIcon(risk),
            contentDescription = null,
            tint = foreground,
            modifier = Modifier.size(RiskPillIconSize),
        )
        Text(
            text = risk.label,
            style = KnotworkTextStyles.LabelSm,
            color = foreground,
        )
    }
}

/** Maps a [Risk] to the matching [androidx.compose.ui.graphics.vector.ImageVector] from the icon-mapping table. */
private fun riskIcon(risk: Risk): ImageVector = when (risk) {
    Risk.Readonly -> Icons.Outlined.Visibility
    Risk.Sensitive -> Icons.Outlined.WarningAmber
    Risk.Destructive -> Icons.Outlined.GppMaybe
}

/** Maps a [Risk] to the matching `extended.risk*` container colour. */
@Composable
private fun riskContainerColor(risk: Risk): Color = when (risk) {
    Risk.Readonly -> KnotworkTheme.extended.riskReadonly
    Risk.Sensitive -> KnotworkTheme.extended.riskSensitive
    Risk.Destructive -> KnotworkTheme.extended.riskDestructive
}

/** Human-readable label rendered inside the pill (also used in `contentDescription`). */
private val Risk.label: String
    get() = when (this) {
        Risk.Readonly -> "Read-only"
        Risk.Sensitive -> "Sensitive"
        Risk.Destructive -> "Destructive"
    }
