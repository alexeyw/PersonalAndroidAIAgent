package app.knotwork.android.presentation.ui.pipeline.editor.config

import app.knotwork.android.domain.models.PipelinePresetImportOutcome
import app.knotwork.android.domain.pipelineio.PipelinePresetJsonSerializer
import app.knotwork.design.components.pipelineeditor.NodeConfigValidation
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Proves every node of every bundled pipeline preset opens **cleanly** in the
 * editor's `NodeConfigSheet` — decoded by [NodeConfigCodec] and accepted by
 * [NodeConfigValidation] with zero field errors.
 *
 * Why this exists as a separate gate from
 * [app.knotwork.android.domain.pipelineio.PipelinePresetCatalogValidationTest]:
 * that test proves a preset is a well-formed, *runnable* graph, which is a
 * strictly weaker property than being *editable*. A preset shipped without a
 * `nodeConfig` envelope ran fine — the engine reads only the flat `config`
 * fields — while `deriveFromLegacy` reconstructed an `INTENT_ROUTER` with no
 * classes at all. The form requires 2..6, so the node opened with an empty
 * class list and a validation error that blocked Save, and nothing in the
 * build caught it. This test closes that gap for every node type at once,
 * rather than re-deriving per-type reasoning by hand each time a preset is
 * authored.
 *
 * Titles are validated against their real peers (uniqueness is a per-pipeline
 * rule), so a preset with two identically-named nodes also fails here.
 *
 * Gradle's `:app:test` task runs in the `app/` working directory, so the
 * relative asset path resolves.
 */
class BundledPresetEditabilityTest {

    private val catalogDir: File = File("src/main/assets/presets/pipelines")

    @Test
    fun `every bundled preset node decodes into a config the editor accepts`() {
        val files = catalogDir.listFiles { _, name -> name.endsWith(".json", ignoreCase = true) }
            ?.sortedBy { it.name }
            .orEmpty()
        assertTrue("No bundled preset files found in ${catalogDir.absolutePath}", files.isNotEmpty())

        val failures = mutableListOf<String>()
        files.forEach { file ->
            val outcome = PipelinePresetJsonSerializer.parse(file.readText(), isBundled = true)
            val preset = (outcome as? PipelinePresetImportOutcome.Success)?.preset
                ?: error("Bundled preset ${file.name} did not parse: $outcome")

            val titlesById = preset.graph.nodes.associate { node ->
                node.id to NodeConfigCodec.decode(node).title
            }
            preset.graph.nodes.forEach { node ->
                val config = NodeConfigCodec.decode(node)
                val peerTitles = titlesById.filterKeys { it != node.id }.values.toSet()
                val errors = NodeConfigValidation.validate(config, peerTitles)
                if (errors.isNotEmpty()) {
                    failures += "${file.name} node \"${node.id}\" (${node.type}): $errors"
                }
            }
        }

        assertTrue(
            "These bundled preset nodes would open in the editor with a validation error " +
                "blocking Save:\n" + failures.joinToString("\n"),
            failures.isEmpty(),
        )
    }
}
