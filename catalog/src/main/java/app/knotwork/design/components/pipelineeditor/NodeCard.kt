package app.knotwork.design.components.pipelineeditor

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import app.knotwork.design.theme.KnotworkTheme
import app.knotwork.design.tokens.KnotworkTextStyles

/** Width of every NodeCard on the canvas (spec §NodeCard). */
private val NodeCardWidth = 168.dp

/** Minimum NodeCard height — body wraps within this floor. */
private val NodeCardMinHeight = 64.dp

/** Maximum NodeCard height — body clamps to two lines past this ceiling. */
private val NodeCardMaxHeight = 96.dp

/** Header strip height (spec §NodeCard). */
private val HeaderStripHeight = 28.dp

/** Selected outer-border stroke (spec). */
private val SelectedBorderWidth = 2.dp

/** Error outer-border stroke (spec). */
private val ErrorBorderWidth = 2.dp

/** Idle outer-border stroke (spec). */
private val IdleBorderWidth = 1.dp

/** Multi-select corner-chevron side (spec). */
private val MultiSelectChevronSize = 8.dp

/** Visual port-dot diameter (spec §NodeCard). */
private val PortDotVisualDiameter = 12.dp

/** Touch-target diameter for ports (spec §NodeCard). */
private val PortHitDiameter = 24.dp

/** Stroke width applied to the port-dot border. */
private val PortDotBorderWidth = 1.dp

/** Header glyph diameter. */
private val HeaderGlyphSize = 16.dp

/** Vertical inset that pulls the inbound dot up into the header strip. */
private val InboundDotInset = 6.dp

/** Header strip pulse range under [running] (lower bound). */
private const val PULSE_LOW = 0.85f

/** Header strip pulse range under [running] (upper bound). */
private const val PULSE_HIGH = 1.0f

/** Header strip pulse cycle in ms (spec §running). */
private const val PULSE_DURATION_MS = 1_200

/** Header label letter-tracking (spec §NodeCard). */
private const val HEADER_LABEL_TRACKING_EM = 0.08f

/**
 * Pipeline-editor node card — single composable covering idle, selected,
 * multi-selected, error (validation / runtime), and running states.
 *
 * The full visual contract lives in
 * `project_docs/design/compose/components/README.md` §NodeCard. Geometry
 * comes from the constants above; tints from
 * [NodeType.headerTint] / [headerOnColor]; reduced-motion handling reads
 * [KnotworkTheme.a11y] per `decisions.md §14` and collapses the running
 * pulse to a steady-state filled dot.
 *
 * **Stateless** — all interactivity (tap to select, long-press to multi-
 * select, drag from a port to enter connection mode) is owned by the
 * canvas in Task 9. This composable just renders. Selection / running /
 * error visuals are driven by the parameters below.
 *
 * @param type the node type. Drives header tint, glyph, and uppercase
 * label.
 * @param title the node's display title shown as `TitleMd` in the body.
 * @param subtitle optional one-line secondary text shown as `BodySm`
 * below the title (model id, expression, …). `null` hides the line and
 * frees vertical space for a one-line body.
 * @param selected `true` swaps the outline for the 2 dp `accent500`
 * selected border and bumps elevation to `el3`.
 * @param error `null` for idle; [NodeError.Validation] swaps the type
 * label for a warning glyph; [NodeError.Runtime] surfaces the cause
 * inline.
 * @param running `true` runs the header strip's 1.2 s pulse (gated by
 * reduced-motion).
 * @param multiSelected `true` swaps the outline for the 2 dp `accent300`
 * multi-select border and renders the top-right chevron marker.
 * @param ports inbound / outbound port descriptors. Multi-out nodes show
 * the per-port label under each dot in `LabelSm`.
 * @param modifier optional layout modifier applied to the card root.
 */
@Composable
@Suppress("LongMethod", "LongParameterList") // Spec mandates the parameter shape; layout is intentionally inlined.
fun NodeCard(
    type: NodeType,
    title: String,
    subtitle: String?,
    selected: Boolean,
    error: NodeError?,
    running: Boolean,
    multiSelected: Boolean,
    ports: NodePorts,
    modifier: Modifier = Modifier,
) {
    val headerColor = type.headerTint()
    val onHeader = headerOnColor(strip = headerColor)
    val borderColor = nodeBorderColor(
        selected = selected,
        multiSelected = multiSelected,
        error = error,
    )
    val borderWidth = when {
        error != null || selected || multiSelected -> SelectedBorderWidth
        else -> IdleBorderWidth
    }
    val elevation = when {
        selected -> KnotworkTheme.elevation.el3
        else -> KnotworkTheme.elevation.el1
    }
    val headerAlpha = headerStripAlpha(running = running)
    Surface(
        modifier = modifier.width(NodeCardWidth),
        color = MaterialTheme.colorScheme.surface,
        shape = KnotworkTheme.shapes.md,
        tonalElevation = elevation,
        shadowElevation = elevation,
        border = BorderStroke(width = borderWidth, color = borderColor),
    ) {
        Box {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = NodeCardMinHeight, max = NodeCardMaxHeight),
            ) {
                HeaderStrip(
                    type = type,
                    strip = headerColor,
                    onStrip = onHeader,
                    error = error,
                    alpha = headerAlpha,
                )
                NodeBody(title = title, subtitle = subtitle, error = error)
            }
            if (ports.inbound > 0) {
                InboundDot(color = headerColor)
            }
            if (ports.outbound.isNotEmpty()) {
                OutboundPortRow(ports = ports.outbound, color = headerColor)
            }
            if (multiSelected) {
                MultiSelectChevron()
            }
        }
    }
}

/** Header strip — 28 dp tall, hue-tinted, with glyph + uppercase type label. */
@Composable
private fun HeaderStrip(type: NodeType, strip: Color, onStrip: Color, error: NodeError?, alpha: Float) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .graphicsLayer { this.alpha = alpha }
            .background(color = strip)
            .padding(horizontal = KnotworkTheme.spacing.sp3),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(KnotworkTheme.spacing.sp2),
    ) {
        Icon(
            imageVector = type.glyph(),
            contentDescription = null,
            tint = onStrip,
            modifier = Modifier.size(HeaderGlyphSize),
        )
        if (error is NodeError.Validation) {
            Icon(
                imageVector = Icons.Outlined.WarningAmber,
                contentDescription = null,
                tint = onStrip,
                modifier = Modifier.size(HeaderGlyphSize),
            )
        } else {
            Text(
                text = type.displayLabel(),
                style = KnotworkTextStyles.LabelSm.copy(
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = HEADER_LABEL_TRACKING_EM.em,
                ),
                color = onStrip,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** Body — title + optional subtitle (or runtime error cause). */
@Composable
private fun NodeBody(title: String, subtitle: String?, error: NodeError?) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = KnotworkTheme.spacing.sp3, vertical = KnotworkTheme.spacing.sp2),
        verticalArrangement = Arrangement.spacedBy(KnotworkTheme.spacing.sp1),
    ) {
        Text(
            text = title,
            style = KnotworkTextStyles.TitleMd,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        when (error) {
            is NodeError.Runtime -> Text(
                text = error.message,
                style = KnotworkTextStyles.BodySm,
                color = KnotworkTheme.extended.signalError,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            else -> if (!subtitle.isNullOrBlank()) {
                Text(
                    text = subtitle,
                    style = KnotworkTextStyles.BodySm,
                    color = KnotworkTheme.extended.onSurfaceMuted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

/** Inbound dot at the top-center, baseline pulled 6 dp up into the header. */
@Composable
private fun androidx.compose.foundation.layout.BoxScope.InboundDot(color: Color) {
    PortDot(
        color = color,
        modifier = Modifier
            .align(Alignment.TopCenter)
            .offset(y = -InboundDotInset),
    )
}

/**
 * Outbound port row pinned to the bottom edge. Single ports render the dot
 * only; multi-port nodes render each dot with a `LabelSm` underneath so the
 * canvas can identify each branch without consulting the edge labels.
 */
@Composable
private fun androidx.compose.foundation.layout.BoxScope.OutboundPortRow(ports: List<OutboundPort>, color: Color) {
    val showLabels = ports.size > 1
    Row(
        modifier = Modifier
            .align(Alignment.BottomCenter)
            .fillMaxWidth()
            .offset(y = InboundDotInset)
            .padding(horizontal = KnotworkTheme.spacing.sp3),
        horizontalArrangement = Arrangement.spacedBy(
            space = KnotworkTheme.spacing.sp2,
            alignment = Alignment.CenterHorizontally,
        ),
        verticalAlignment = Alignment.Top,
    ) {
        ports.forEach { port ->
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(KnotworkTheme.spacing.sp1),
            ) {
                PortDot(color = color, modifier = Modifier)
                if (showLabels) {
                    Text(
                        text = port.label,
                        style = KnotworkTextStyles.LabelSm,
                        color = KnotworkTheme.extended.onSurfaceMuted,
                        textAlign = TextAlign.Center,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

/** Single port dot — 12 dp visual inside a 24 dp invisible touch target. */
@Composable
private fun PortDot(color: Color, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .size(PortHitDiameter)
            .padding(PaddingValues(all = (PortHitDiameter - PortDotVisualDiameter) / 2)),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .size(PortDotVisualDiameter)
                .clip(CircleShape)
                .background(color = color)
                .border(width = PortDotBorderWidth, color = MaterialTheme.colorScheme.surface, shape = CircleShape),
        )
    }
}

/** 4 dp accent-300 chevron rendered in the top-right when [multiSelected]. */
@Composable
private fun androidx.compose.foundation.layout.BoxScope.MultiSelectChevron() {
    Spacer(
        modifier = Modifier
            .align(Alignment.TopEnd)
            .padding(KnotworkTheme.spacing.sp1)
            .size(MultiSelectChevronSize)
            .clip(CircleShape)
            .background(color = KnotworkTheme.extended.outlineStrong),
    )
}

/** Border colour resolution — error wins over selected wins over multi-select. */
@Composable
private fun nodeBorderColor(selected: Boolean, multiSelected: Boolean, error: NodeError?): Color {
    val extended = KnotworkTheme.extended
    return when {
        error != null -> extended.signalError
        selected -> MaterialTheme.colorScheme.primary
        multiSelected -> extended.outlineStrong
        else -> extended.divider
    }
}

/**
 * Drives the header-strip 1.2 s pulse for the running state. Returns
 * `1.0f` when reduced-motion is on or when [running] is `false` — both
 * cases yield a steady strip per `decisions.md §14`.
 */
@Composable
private fun headerStripAlpha(running: Boolean): Float {
    if (!running) return PULSE_HIGH
    if (KnotworkTheme.a11y.reducedMotion()) return PULSE_HIGH
    val transition = rememberInfiniteTransition(label = "node-running-pulse")
    val alpha by transition.animateFloat(
        initialValue = PULSE_LOW,
        targetValue = PULSE_HIGH,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = PULSE_DURATION_MS, easing = KnotworkTheme.motion.easeStd),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "node-running-pulse-alpha",
    )
    return alpha
}

/** Light-theme idle preview. */
@Preview(name = "NodeCard — LiteRT idle", showBackground = true)
@Composable
private fun NodeCardLiteRtIdlePreview() {
    PreviewWrapper(darkTheme = false) {
        NodeCard(
            type = NodeType.LITE_RT,
            title = "Local response",
            subtitle = "gemma-2b-it",
            selected = false,
            error = null,
            running = false,
            multiSelected = false,
            ports = NodePorts.forType(NodeType.LITE_RT),
        )
    }
}

/** Light-theme selected preview. */
@Preview(name = "NodeCard — selected", showBackground = true)
@Composable
private fun NodeCardSelectedPreview() {
    PreviewWrapper(darkTheme = false) {
        NodeCard(
            type = NodeType.INTENT_ROUTER,
            title = "Route the request",
            subtitle = "5 classes",
            selected = true,
            error = null,
            running = false,
            multiSelected = false,
            ports = NodePorts.forType(
                NodeType.INTENT_ROUTER,
                intentClasses = listOf("simple", "complex"),
            ),
        )
    }
}

/** Dark-theme running preview. */
@Preview(name = "NodeCard — running (dark)", showBackground = true)
@Composable
private fun NodeCardRunningPreview() {
    PreviewWrapper(darkTheme = true) {
        NodeCard(
            type = NodeType.QUEUE_PROCESSOR,
            title = "Process items",
            subtitle = "parallelism = 2",
            selected = false,
            error = null,
            running = true,
            multiSelected = false,
            ports = NodePorts.forType(NodeType.QUEUE_PROCESSOR),
        )
    }
}

/** Wrap each preview in `KnotworkTheme` with a deterministic palette. */
@Composable
private fun PreviewWrapper(darkTheme: Boolean, content: @Composable () -> Unit) {
    app.knotwork.design.theme.KnotworkTheme(darkTheme = darkTheme) {
        Surface(modifier = Modifier.padding(KnotworkTheme.spacing.sp4)) {
            content()
        }
    }
}
