package app.knotwork.design.components.pipelineeditor

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import app.knotwork.design.theme.KnotworkTheme
import app.knotwork.design.tokens.KnotworkTextStyles

/**
 * Scrollable harness that exercises every pipeline-editor base component
 * in a single column. Used by the Studio preview pane and by the
 * Roborazzi snapshot baseline so a regression in any one component
 * surfaces in a single diff.
 *
 * Renders inside the parent [KnotworkTheme]; callers (preview / test)
 * pin `darkTheme` deterministically.
 */
@Composable
fun PipelineEditorCatalogContent() {
    Surface(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(KnotworkTheme.spacing.sp4),
            verticalArrangement = Arrangement.spacedBy(KnotworkTheme.spacing.sp4),
        ) {
            SectionLabel(text = "NodeCard — every type (idle)")
            NodeCardGrid(rotation = NodeCardRotation.Idle)

            SectionLabel(text = "NodeCard — selected / error / running")
            NodeCardStateMatrix()

            SectionLabel(text = "EdgeLabel — variants")
            EdgeLabelRow()

            SectionLabel(text = "EditorToolbar — editing")
            EditorToolbar(
                name = "Weekly digest",
                onNameChange = {},
                onNavigateUp = {},
                onPrimaryAction = {},
                onOverflow = {},
                subtitle = "Editing · 4 nodes · 3 edges",
                primaryAction = EditorPrimaryAction.Run,
            )

            SectionLabel(text = "EditorToolbar — last run done")
            EditorToolbar(
                name = "research-deepdive",
                onNameChange = {},
                onNavigateUp = {},
                onPrimaryAction = {},
                onOverflow = {},
                subtitle = "Last run · 12.8 s · 2 408 tok",
                primaryAction = EditorPrimaryAction.Rerun,
            )

            SectionLabel(text = "NodeConfigSheet body — LiteRT (idle)")
            NodeConfigSheetBody(
                config = sampleLiteRtConfig(),
                errors = emptyMap(),
                onChange = {},
                onCancel = {},
                onSave = {},
            )

            SectionLabel(text = "NodeConfigSheet body — IntentRouter (invalid)")
            NodeConfigSheetBody(
                config = invalidIntentRouterConfig(),
                errors = NodeConfigValidation.validate(
                    config = invalidIntentRouterConfig(),
                    peerTitles = setOf("router"), // forces TITLE_DUPLICATE on the invalid sample
                ),
                onChange = {},
                onCancel = {},
                onSave = {},
            )
        }
    }
}

/** Section title rendered above each subsection. */
@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text,
        style = KnotworkTextStyles.LabelMd,
        color = KnotworkTheme.extended.onSurfaceMuted,
    )
}

/** Rotation policy for the per-type [NodeCard] grid in the catalog. */
private enum class NodeCardRotation { Idle, StateMatrix }

/** 12-entry [NodeCard] grid; every type rendered in its idle state. */
@Composable
private fun NodeCardGrid(rotation: NodeCardRotation) {
    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(KnotworkTheme.spacing.sp3),
        verticalArrangement = Arrangement.spacedBy(KnotworkTheme.spacing.sp3),
    ) {
        NodeType.entries.forEach { type ->
            val maxRetries = if (type == NodeType.EVALUATION) 2 else 0
            val intentClasses = if (type == NodeType.INTENT_ROUTER) {
                listOf("simple", "complex", "tool")
            } else {
                emptyList()
            }
            NodeCard(
                type = type,
                title = type.displayLabel().lowercase().replaceFirstChar { it.uppercase() },
                subtitle = sampleSubtitle(type),
                selected = false,
                error = null,
                running = false,
                multiSelected = false,
                ports = NodePorts.forType(
                    type = type,
                    intentClasses = intentClasses,
                    maxRetries = maxRetries,
                ),
            )
        }
        if (rotation == NodeCardRotation.StateMatrix) {
            // Reserved for future variants if the catalog needs to inline both
            // grids inside the same FlowRow; today only `Idle` is used.
        }
    }
}

/** Selected / runtime-error / running state cards side by side. */
@Composable
private fun NodeCardStateMatrix() {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(KnotworkTheme.spacing.sp3),
    ) {
        NodeCard(
            type = NodeType.CLOUD,
            title = "Selected",
            subtitle = "gpt-5",
            selected = true,
            error = null,
            running = false,
            multiSelected = false,
            ports = NodePorts.forType(NodeType.CLOUD),
        )
        NodeCard(
            type = NodeType.TOOL,
            title = "Runtime error",
            subtitle = "fs.write_file",
            selected = false,
            error = NodeError.Runtime(message = "Tool timed out"),
            running = false,
            multiSelected = false,
            ports = NodePorts.forType(NodeType.TOOL),
        )
        NodeCard(
            type = NodeType.EVALUATION,
            title = "Validating…",
            subtitle = null,
            selected = false,
            error = NodeError.Validation(message = "Missing criteria"),
            running = false,
            multiSelected = false,
            ports = NodePorts.forType(NodeType.EVALUATION, maxRetries = 2),
        )
    }
}

/** Every canonical edge label kind in a single row. */
@Composable
private fun EdgeLabelRow() {
    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(KnotworkTheme.spacing.sp2),
        verticalArrangement = Arrangement.spacedBy(KnotworkTheme.spacing.sp2),
    ) {
        listOf("True", "False", "Item", "Done", "Pass", "Retry", "Fail", "simple-query").forEach {
            EdgeLabel(text = it)
        }
    }
}

/** Sample subtitle for the per-type grid. */
private fun sampleSubtitle(type: NodeType): String? = when (type) {
    NodeType.INPUT -> "user.message"
    NodeType.OUTPUT -> "markdown"
    NodeType.LITE_RT -> "gemma-2b-it"
    NodeType.CLOUD -> "openai/gpt-5"
    NodeType.INTENT_ROUTER -> "3 classes"
    NodeType.IF_CONDITION -> "score > 0.8"
    NodeType.CLARIFICATION -> "Ask the user"
    NodeType.TOOL -> "fs.write_file"
    NodeType.DECOMPOSITION -> "max 5 steps"
    NodeType.QUEUE_PROCESSOR -> "parallelism = 2"
    NodeType.EVALUATION -> "retries = 2"
    NodeType.SUMMARY -> "bullets"
}

/** Idle [LiteRtConfig] used by the catalog form preview. */
private fun sampleLiteRtConfig(): LiteRtConfig = LiteRtConfig(
    title = "Local response",
    modelId = "gemma-2b-it",
    systemPrompt = "Answer concisely as of \$DATE.",
)

/** Invalid [IntentRouterConfig] (one class, blank prompt) used to surface inline errors. */
private fun invalidIntentRouterConfig(): IntentRouterConfig = IntentRouterConfig(
    title = "router",
    classes = listOf(IntentClass(name = "simple")),
    classifierPrompt = "",
)

/** Light-theme preview. */
@Preview(name = "Pipeline editor — Light", showBackground = true, heightDp = 3_200)
@Composable
private fun PipelineEditorCatalogLightPreview() {
    KnotworkTheme(darkTheme = false) {
        Surface(color = MaterialTheme.colorScheme.background) {
            PipelineEditorCatalogContent()
        }
    }
}

/** Dark-theme preview. */
@Preview(name = "Pipeline editor — Dark", showBackground = true, heightDp = 3_200)
@Composable
private fun PipelineEditorCatalogDarkPreview() {
    KnotworkTheme(darkTheme = true) {
        Surface(color = MaterialTheme.colorScheme.background) {
            PipelineEditorCatalogContent()
        }
    }
}
