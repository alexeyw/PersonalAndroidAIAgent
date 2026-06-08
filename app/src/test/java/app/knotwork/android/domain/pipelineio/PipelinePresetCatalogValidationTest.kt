package app.knotwork.android.domain.pipelineio

import app.knotwork.android.domain.models.ConnectionModel
import app.knotwork.android.domain.models.NodeContextConfig
import app.knotwork.android.domain.models.NodeModel
import app.knotwork.android.domain.models.NodeType
import app.knotwork.android.domain.models.PipelineGraph
import app.knotwork.android.domain.models.PipelinePresetImportOutcome
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Catalogue-level validation for the bundled pipeline-preset JSON files that
 * ship under `app/src/main/assets/presets/pipelines/`. Pure JVM (no
 * Robolectric, no `Context`): each file is read directly from the
 * filesystem and fed through [PipelinePresetJsonSerializer.parse].
 *
 * Why a catalogue-level test exists separately from
 * [PipelinePresetJsonSerializerTest]: the serializer test pins the
 * round-trip contract using synthetic fixtures, while this test pins the
 * *shipped* artefacts — seven curated presets that the user sees the first
 * time they open the library (including the comprehensive
 * `showcase_full_agent`, which doubles as the first-launch seed). A broken
 * preset would otherwise only surface at runtime on a real device.
 *
 * The four assertions per file are intentionally narrow:
 * - the filename set is the promised one (catches typos /
 *   accidental deletion);
 * - every file parses to [PipelinePresetImportOutcome.Success] with
 *   `isBundled == true`;
 * - every embedded graph passes [PipelineGraph.validate] with zero errors;
 * - every `$VARIABLE` token used in any `systemPrompt` is one of the
 *   registered runtime providers from `di/PromptTemplateModule.kt`.
 *
 * The final test (`validate detects a broken preset`) is a regression
 * guard: it parses an intentionally-broken in-memory document and asserts
 * that `validate()` would surface at least one error — so a future commit
 * that accidentally widens the validator cannot silently break the gate.
 *
 * Gradle's `:app:test` task runs in the `app/` working directory, so the
 * relative path `src/main/assets/presets/pipelines` resolves correctly.
 */
class PipelinePresetCatalogValidationTest {

    /**
     * Expected bundled preset filenames. Adding a new bundled preset means
     * updating this set in the same PR — keeping the gate intentionally
     * tight prevents accidental deletions sliding into a release.
     */
    private val expectedFileNames: Set<String> = setOf(
        "local_only_qa.json",
        "cloud_assist.json",
        "tool_using_react.json",
        "multi_step_research.json",
        "clarify_then_act.json",
        "routed_local_cloud.json",
        "showcase_full_agent.json",
    )

    /**
     * Whitelist of `$VARIABLE` keys that may appear in a bundled preset
     * systemPrompt. Source of truth: `di/PromptTemplateModule.kt`. When a
     * new [app.knotwork.android.domain.prompt.PromptVariableProvider] is
     * registered, add its key here in the same commit.
     */
    private val knownVariableKeys: Set<String> = setOf(
        "DATE",
        "TIME",
        "TOOLS",
        "MODEL",
        "MEMORY_SUMMARY",
        "LANG",
        "LOCATION",
        "USER",
        "DEVICE",
    )

    private val catalogDir: File = File("src/main/assets/presets/pipelines")

    private val variableTokenRegex: Regex = Regex("(?<!\\\\)\\$([A-Z_][A-Z0-9_]*)")

    @Test
    fun `catalog directory contains exactly the expected bundled presets`() {
        assertTrue(
            "Bundled preset directory missing: ${catalogDir.absolutePath}",
            catalogDir.isDirectory,
        )
        val actual = catalogDir.listFiles { _, name -> name.endsWith(".json", ignoreCase = true) }
            ?.map { it.name }
            ?.toSet()
            .orEmpty()
        assertEquals(expectedFileNames, actual)
    }

    @Test
    fun `every bundled preset parses as Success`() {
        forEachBundledFile { file ->
            val outcome = PipelinePresetJsonSerializer.parse(file.readText(), isBundled = true)
            assertTrue(
                "Bundled preset ${file.name} did not parse cleanly: $outcome",
                outcome is PipelinePresetImportOutcome.Success,
            )
            val success = outcome as PipelinePresetImportOutcome.Success
            assertTrue(
                "Bundled preset ${file.name} must be marked isBundled = true",
                success.preset.isBundled,
            )
            assertEquals(
                "Preset id should equal the filename stem for ${file.name}",
                file.nameWithoutExtension,
                success.preset.id,
            )
        }
    }

    @Test
    fun `every bundled graph passes validate with zero errors`() {
        forEachBundledFile { file ->
            val preset = parseAsSuccess(file).preset
            val errors = preset.graph.validate()
            assertTrue(
                "Bundled preset ${file.name} has validation errors: $errors",
                errors.isEmpty(),
            )
        }
    }

    @Test
    fun `every system prompt uses only registered VARIABLE tokens`() {
        forEachBundledFile { file ->
            val preset = parseAsSuccess(file).preset
            preset.graph.nodes.forEach { node ->
                val prompt = node.systemPrompt ?: return@forEach
                val usedKeys = variableTokenRegex.findAll(prompt)
                    .map { it.groupValues[1] }
                    .toSet()
                val unknown = usedKeys - knownVariableKeys
                assertTrue(
                    "Bundled preset ${file.name} node \"${node.id}\" references " +
                        "unknown prompt variable(s) $unknown — register them in " +
                        "di/PromptTemplateModule.kt or fix the typo.",
                    unknown.isEmpty(),
                )
            }
        }
    }

    @Test
    fun `validate detects a broken preset (regression guard)`() {
        // Built directly in code so the assertion does not depend on a
        // bundled fixture: an INPUT node whose only successor is OUTPUT but
        // which is wired backwards (OUTPUT -> INPUT) should fail because
        // INPUT has no outgoing edge and OUTPUT has no incoming edge.
        val brokenGraph = PipelineGraph(
            id = "broken",
            name = "Broken",
            nodes = listOf(
                NodeModel(
                    id = "in",
                    type = NodeType.INPUT,
                    x = 0f,
                    y = 0f,
                    systemPrompt = null,
                    contextConfig = NodeContextConfig.defaultForType(NodeType.INPUT),
                ),
                NodeModel(
                    id = "out",
                    type = NodeType.OUTPUT,
                    x = 100f,
                    y = 0f,
                    systemPrompt = null,
                    contextConfig = NodeContextConfig.defaultForType(NodeType.OUTPUT),
                ),
            ),
            connections = listOf(
                ConnectionModel(id = "c1", sourceNodeId = "out", targetNodeId = "in"),
            ),
        )
        val errors = brokenGraph.validate()
        assertTrue(
            "Regression guard: validate() must surface at least one error on a clearly invalid graph",
            errors.isNotEmpty(),
        )
    }

    private fun parseAsSuccess(file: File): PipelinePresetImportOutcome.Success {
        val outcome = PipelinePresetJsonSerializer.parse(file.readText(), isBundled = true)
        assertNotNull("Parse outcome was null for ${file.name}", outcome)
        assertTrue(
            "Expected Success for ${file.name} but got $outcome",
            outcome is PipelinePresetImportOutcome.Success,
        )
        return outcome as PipelinePresetImportOutcome.Success
    }

    private inline fun forEachBundledFile(block: (File) -> Unit) {
        val files = catalogDir.listFiles { _, name -> name.endsWith(".json", ignoreCase = true) }
            ?.sortedBy { it.name }
            .orEmpty()
        assertTrue(
            "No bundled preset files found in ${catalogDir.absolutePath}",
            files.isNotEmpty(),
        )
        files.forEach(block)
    }
}
