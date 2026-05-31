@file:Suppress(
    "MatchingDeclarationName", // File hosts `KnotworkExtendedColors` and `LocalKnotworkExtendedColors` plus factories.
)

package app.knotwork.design.tokens

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * Knotwork-specific colours that have no clean Material3 [androidx.compose.material3.ColorScheme]
 * slot — console surfaces, chat bubbles, the three risk levels and the 12-hue
 * node palette consumed by the pipeline editor.
 *
 * Access from a composition via the `KnotworkTheme.extended` accessor (defined
 * in `theme/KnotworkTheme.kt`) or directly through [LocalKnotworkExtendedColors].
 *
 * @property surface1 first surface step on top of the M3 `surface` (used by inline cards).
 * @property surface2 second surface step (chat bot bubble background, list rows).
 * @property surface3 third surface step (sheet headers, secondary container backdrops).
 * @property surface4 fourth surface step (modal sheet body, popovers).
 * @property outlineStrong strong outline used for selected / focused borders.
 * @property divider hairline divider colour for list separators.
 * @property onSurface2 secondary text colour on top of any surface step.
 * @property onSurfaceMuted muted text colour for captions and timestamps.
 * @property onSurfaceDim dim text colour for placeholder hints.
 * @property chatUserBg background colour of the user-side chat bubble.
 * @property chatBotBg background colour of the assistant-side chat bubble.
 * @property riskReadonly accent for `READ_ONLY` HITL prompts and risk pills.
 * @property riskSensitive accent for `SENSITIVE` HITL prompts and risk pills.
 * @property riskDestructive accent for `DESTRUCTIVE` HITL prompts and risk pills.
 * @property signalSuccess hue-locked success signal (status pills, validation OK).
 * @property signalWarn hue-locked warning signal (status pills, validation warn).
 * @property signalError hue-locked error signal (status pills, validation error).
 * @property consoleBg console / log surface background — always near-black even
 * in light theme so monospace logs read like a terminal.
 * @property consoleFg console / log foreground (off-white) — paired with [consoleBg].
 * @property nodeInput hue for `NodeType.INPUT` cards in the pipeline editor.
 * @property nodeIntentRouter hue for `NodeType.INTENT_ROUTER` cards.
 * @property nodeIfCondition hue for `NodeType.IF_CONDITION` cards.
 * @property nodeClarification hue for `NodeType.CLARIFICATION` cards.
 * @property nodeLiteRt hue for `NodeType.LITE_RT` cards.
 * @property nodeCloud hue for `NodeType.CLOUD` cards.
 * @property nodeTool hue for `NodeType.TOOL` cards.
 * @property nodeDecomposition hue for `NodeType.DECOMPOSITION` cards.
 * @property nodeQueueProcessor hue for `NodeType.QUEUE_PROCESSOR` cards.
 * @property nodeEvaluation hue for `NodeType.EVALUATION` cards.
 * @property nodeSummary hue for `NodeType.SUMMARY` cards.
 * @property nodeOutput hue for `NodeType.OUTPUT` cards.
 */
data class KnotworkExtendedColors(
    val surface1: Color,
    val surface2: Color,
    val surface3: Color,
    val surface4: Color,
    val outlineStrong: Color,
    val divider: Color,
    val onSurface2: Color,
    val onSurfaceMuted: Color,
    val onSurfaceDim: Color,
    val chatUserBg: Color,
    val chatBotBg: Color,
    val riskReadonly: Color,
    val riskSensitive: Color,
    val riskDestructive: Color,
    val signalSuccess: Color,
    val signalWarn: Color,
    val signalError: Color,
    val memoryAuto: Color,
    val memoryCompaction: Color,
    val consoleBg: Color,
    val consoleFg: Color,
    val nodeInput: Color,
    val nodeIntentRouter: Color,
    val nodeIfCondition: Color,
    val nodeClarification: Color,
    val nodeLiteRt: Color,
    val nodeCloud: Color,
    val nodeTool: Color,
    val nodeDecomposition: Color,
    val nodeQueueProcessor: Color,
    val nodeEvaluation: Color,
    val nodeSummary: Color,
    val nodeOutput: Color,
)

/**
 * Builds the [KnotworkExtendedColors] palette for the light Knotwork theme.
 *
 * Risk colours read the light-tone variants from [KnotworkLight]; node hues
 * and signal hues stay constant across themes — pipelines are expected to
 * read identically in light and dark.
 *
 * @return a fresh [KnotworkExtendedColors] instance.
 */
fun knotworkExtendedColorsLight() = KnotworkExtendedColors(
    surface1 = KnotworkLight.Surface1,
    surface2 = KnotworkLight.Surface2,
    surface3 = KnotworkLight.Surface3,
    surface4 = KnotworkLight.Surface4,
    outlineStrong = KnotworkLight.OutlineStrong,
    divider = KnotworkLight.Divider,
    onSurface2 = KnotworkLight.OnSurface2,
    onSurfaceMuted = KnotworkLight.OnSurfaceMuted,
    onSurfaceDim = KnotworkLight.OnSurfaceDim,
    chatUserBg = KnotworkLight.ChatUserBg,
    chatBotBg = KnotworkLight.ChatBotBg,
    riskReadonly = KnotworkLight.RiskReadonly,
    riskSensitive = KnotworkLight.RiskSensitive,
    riskDestructive = KnotworkLight.RiskDestructive,
    signalSuccess = KnotworkPalette.SignalSuccess,
    signalWarn = KnotworkPalette.SignalWarn,
    signalError = KnotworkPalette.SignalError,
    memoryAuto = KnotworkPalette.MemoryAuto,
    memoryCompaction = KnotworkPalette.MemoryCompaction,
    consoleBg = KnotworkLight.ConsoleBg,
    consoleFg = KnotworkLight.ConsoleFg,
    nodeInput = KnotworkPalette.NodeInput,
    nodeIntentRouter = KnotworkPalette.NodeIntentRouter,
    nodeIfCondition = KnotworkPalette.NodeIfCondition,
    nodeClarification = KnotworkPalette.NodeClarification,
    nodeLiteRt = KnotworkPalette.NodeLiteRt,
    nodeCloud = KnotworkPalette.NodeCloud,
    nodeTool = KnotworkPalette.NodeTool,
    nodeDecomposition = KnotworkPalette.NodeDecomposition,
    nodeQueueProcessor = KnotworkPalette.NodeQueueProcessor,
    nodeEvaluation = KnotworkPalette.NodeEvaluation,
    nodeSummary = KnotworkPalette.NodeSummary,
    nodeOutput = KnotworkPalette.NodeOutput,
)

/**
 * Builds the [KnotworkExtendedColors] palette for the dark Knotwork theme.
 *
 * Surface and on-surface tokens use the dark ramp; risk colours use the
 * brighter dark-mode variants from [KnotworkDark]. Signal `warn`/`error`
 * are sourced from [KnotworkDark] for legibility on dark surfaces, while
 * `success` stays hue-locked from the central [KnotworkPalette] (its light
 * value reads correctly on both themes). Node hues are constant across
 * themes by design.
 *
 * @return a fresh [KnotworkExtendedColors] instance.
 */
fun knotworkExtendedColorsDark() = KnotworkExtendedColors(
    surface1 = KnotworkDark.Surface1,
    surface2 = KnotworkDark.Surface2,
    surface3 = KnotworkDark.Surface3,
    surface4 = KnotworkDark.Surface4,
    outlineStrong = KnotworkDark.OutlineStrong,
    divider = KnotworkDark.Divider,
    onSurface2 = KnotworkDark.OnSurface2,
    onSurfaceMuted = KnotworkDark.OnSurfaceMuted,
    onSurfaceDim = KnotworkDark.OnSurfaceDim,
    chatUserBg = KnotworkDark.ChatUserBg,
    chatBotBg = KnotworkDark.ChatBotBg,
    riskReadonly = KnotworkDark.RiskReadonly,
    riskSensitive = KnotworkDark.RiskSensitive,
    riskDestructive = KnotworkDark.RiskDestructive,
    signalSuccess = KnotworkPalette.SignalSuccess,
    signalWarn = KnotworkDark.RiskSensitive,
    signalError = KnotworkDark.RiskDestructive,
    memoryAuto = KnotworkPalette.MemoryAuto,
    memoryCompaction = KnotworkPalette.MemoryCompaction,
    consoleBg = KnotworkDark.ConsoleBg,
    consoleFg = KnotworkDark.ConsoleFg,
    nodeInput = KnotworkPalette.NodeInput,
    nodeIntentRouter = KnotworkPalette.NodeIntentRouter,
    nodeIfCondition = KnotworkPalette.NodeIfCondition,
    nodeClarification = KnotworkPalette.NodeClarification,
    nodeLiteRt = KnotworkPalette.NodeLiteRt,
    nodeCloud = KnotworkPalette.NodeCloud,
    nodeTool = KnotworkPalette.NodeTool,
    nodeDecomposition = KnotworkPalette.NodeDecomposition,
    nodeQueueProcessor = KnotworkPalette.NodeQueueProcessor,
    nodeEvaluation = KnotworkPalette.NodeEvaluation,
    nodeSummary = KnotworkPalette.NodeSummary,
    nodeOutput = KnotworkPalette.NodeOutput,
)

/**
 * Composition-local provider for [KnotworkExtendedColors].
 *
 * Defaults to the light palette so previews that forget to wrap in
 * `KnotworkTheme { ... }` still render rather than crashing. Always set by
 * the `KnotworkTheme` wrapper — read via `KnotworkTheme.extended`.
 */
val LocalKnotworkExtendedColors = staticCompositionLocalOf {
    knotworkExtendedColorsLight()
}
