package app.knotwork.android.domain.pipelineio

import app.knotwork.android.domain.constants.BundledPresetCatalog
import app.knotwork.android.domain.models.ConnectionModel
import app.knotwork.android.domain.models.NodeContextConfig
import app.knotwork.android.domain.models.NodeModel
import app.knotwork.android.domain.models.NodeType
import app.knotwork.android.domain.models.PipelineGraph
import app.knotwork.android.domain.models.PipelinePresetImportOutcome
import org.json.JSONObject
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
 * *shipped* artefacts — the curated presets the user sees the first time they
 * open the library (including the comprehensive `showcase_full_agent`, which
 * doubles as the first-launch seed) plus the four internal sub-pipelines it
 * composes. A broken preset would otherwise only surface at runtime on a real
 * device.
 *
 * The assertions per file are intentionally narrow:
 * - the filename set is the promised one (catches typos /
 *   accidental deletion);
 * - every file parses to [PipelinePresetImportOutcome.Success] with
 *   `isBundled == true`;
 * - every embedded graph passes [PipelineGraph.validate] with zero errors;
 * - every `$VARIABLE` token used in any `systemPrompt` is one of the
 *   registered runtime providers from `di/PromptTemplateModule.kt`;
 * - exactly the composed sub-pipelines are flagged `internal`, and every
 *   user-facing preset declares starter prompts;
 * - every node carries a `nodeConfig` envelope agreeing with its flat type,
 *   every `INTENT_ROUTER`'s declared classes match its outgoing edge labels,
 *   and every `PIPELINE` target resolves to a preset in this same catalogue.
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
        "showcase_research_to_file.json",
        "subtask_clarify.json",
        "subtask_lookup.json",
        "subtask_act.json",
        "subtask_process.json",
        "styled_translation.json",
        "share_handler.json",
        "virtual_companion_mood_router.json",
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

    /**
     * Preset ids that are building blocks of a composed preset rather than
     * catalogue entries. They are hidden from the picker
     * (`PipelinePresetRepository.getBundledPresets`) but must stay resolvable
     * by id, because `showcase_full_agent`'s `PIPELINE` nodes target them.
     */
    private val expectedInternalIds: Set<String> = setOf(
        "subtask_clarify",
        "subtask_lookup",
        "subtask_act",
        "subtask_process",
    )

    /**
     * Node types whose output is read by the user (directly, or after a
     * pass-through OUTPUT). These must answer in the device language, not in
     * whichever language the input happened to use — which is what an LLM does
     * unprompted. Control-signal types are excluded on purpose:
     * `INTENT_ROUTER` and `EVALUATION` emit a token matched against edge
     * labels / verdicts, and `IF_CONDITION` emits `true` / `false`; localising
     * any of them would break routing.
     */
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
    fun `every user-facing preset declares its place in the display order`() {
        val userFacing = mutableSetOf<String>()
        forEachBundledFile { file ->
            val preset = parseAsSuccess(file).preset
            if (!preset.isInternal) userFacing += preset.id
        }
        // An unranked preset would silently sort to the end of the gallery
        // rather than to a chosen position. Fail the build instead.
        val unranked = userFacing.filterNot { it in BundledPresetCatalog.DISPLAY_ORDER }
        assertTrue(
            "Bundled preset(s) $unranked are missing from BundledPresetCatalog.DISPLAY_ORDER — " +
                "they would be demoted to the end of the picker.",
            unranked.isEmpty(),
        )
        // The converse: a rank for a preset that no longer ships is dead weight
        // that silently shifts nothing, so it should not linger either.
        val stale = BundledPresetCatalog.DISPLAY_ORDER.filterNot { it in userFacing }
        assertTrue(
            "BundledPresetCatalog.DISPLAY_ORDER ranks $stale, which are not user-facing bundled " +
                "presets (deleted, renamed, or now internal).",
            stale.isEmpty(),
        )
    }

    @Test
    fun `exactly the composed sub-pipelines are flagged internal`() {
        val actual = mutableSetOf<String>()
        forEachBundledFile { file ->
            if (parseAsSuccess(file).preset.isInternal) actual += file.nameWithoutExtension
        }
        assertEquals(
            "The internal flag hides a preset from the picker — it belongs only on " +
                "sub-pipelines another preset composes.",
            expectedInternalIds,
            actual,
        )
    }

    @Test
    fun `every user-facing preset declares starter prompts`() {
        forEachBundledFile { file ->
            val preset = parseAsSuccess(file).preset
            if (preset.isInternal) return@forEachBundledFile
            assertTrue(
                "Bundled preset ${file.name} declares no samplePrompts — a preset-spawned " +
                    "pipeline would show an empty quick-action row on the new-chat empty state.",
                preset.graph.samplePrompts.isNotEmpty(),
            )
        }
    }

    @Test
    fun `every node carries a nodeConfig envelope matching its type`() {
        forEachBundledFile { file ->
            val preset = parseAsSuccess(file).preset
            preset.graph.nodes.forEach { node ->
                val raw = node.configJson
                assertTrue(
                    "Bundled preset ${file.name} node \"${node.id}\" has no nodeConfig envelope — " +
                        "the editor would fall back to legacy derivation and lose editor-only fields.",
                    !raw.isNullOrBlank(),
                )
                val envelope = JSONObject(raw)
                assertEquals(
                    "nodeConfig type disagrees with the node type in ${file.name} node \"${node.id}\"",
                    node.type.name,
                    envelope.optString("type"),
                )
            }
        }
    }

    @Test
    fun `every INTENT_ROUTER declares classes matching its outgoing edge labels`() {
        forEachBundledFile { file ->
            val preset = parseAsSuccess(file).preset
            preset.graph.nodes
                .filter { it.type == NodeType.INTENT_ROUTER }
                .forEach { node ->
                    // The runtime constrains the model to the labels of the node's
                    // own outgoing edges and falls back to the FIRST edge when
                    // nothing matches, so the editor-facing declaration has to
                    // agree with the wiring on both counts.
                    val edges = preset.graph.connections.filter { it.sourceNodeId == node.id }
                    // Every branch must be labelled first: the runtime's fallback is
                    // the first *edge*, not the first labelled one, so an unlabelled
                    // branch would make the fallback assertion below claim something
                    // that is not true of the wiring.
                    assertTrue(
                        "INTENT_ROUTER \"${node.id}\" in ${file.name} has an unlabelled outgoing edge — " +
                            "the model cannot be constrained to a branch that has no label.",
                        edges.all { !it.label.isNullOrBlank() },
                    )
                    val edgeLabels = edges.mapNotNull { it.label }
                    val envelope = JSONObject(node.configJson.orEmpty())
                    val declared = envelope.optJSONArray("classes")
                        ?.let { array -> (0 until array.length()).map { array.getJSONObject(it).optString("name") } }
                        .orEmpty()
                    assertEquals(
                        "INTENT_ROUTER \"${node.id}\" in ${file.name} declares classes that do not " +
                            "match its outgoing edge labels",
                        edgeLabels,
                        declared,
                    )
                    assertEquals(
                        "INTENT_ROUTER \"${node.id}\" in ${file.name} must declare its first outgoing " +
                            "edge as the fallback class — that is the branch the runtime takes",
                        edgeLabels.firstOrNull(),
                        envelope.optString("fallbackClass").takeIf { it.isNotBlank() },
                    )
                }
        }
    }

    @Test
    fun `every PIPELINE target resolves to a preset in this catalogue`() {
        val availableIds = mutableSetOf<String>()
        val targets = mutableListOf<Pair<String, String>>()
        forEachBundledFile { file ->
            val preset = parseAsSuccess(file).preset
            availableIds += preset.id
            preset.graph.nodes
                .filter { it.type == NodeType.PIPELINE }
                .mapNotNull { it.targetPipelineId?.takeIf(String::isNotBlank) }
                .forEach { targets += file.name to it }
        }
        targets.forEach { (fileName, targetId) ->
            assertTrue(
                "Preset $fileName composes \"$targetId\", which is not a bundled preset — the " +
                    "composition would materialise with a dangling PIPELINE target.",
                targetId in availableIds,
            )
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

    // ─── Prompt language rule ──────────────────────────────────────────────

    /**
     * What language each prompted node must work in.
     *
     * The rule exists because a model handed a Russian question answers in
     * Russian and keeps doing so downstream. For prose that is right; for a
     * routing keyword, a subtask that becomes a tool call, or a file path it is
     * a silent break — the text stops matching what the code compares against,
     * so the router falls through and the tool is never called.
     */
    private enum class PromptLanguage {
        /** Machine-facing output: routed, parsed, or turned into tool calls. */
        ENGLISH,

        /** The user reads this text: a final answer, a question, a persona reply. */
        USER,

        /**
         * The language is part of the data, so neither rule applies: a Wikipedia
         * search term must match the `lang` argument it is sent with, and a
         * translation node's target language is the whole point of the node.
         */
        DATA,
    }

    /**
     * Every bundled node that carries a `systemPrompt`, and the language it must
     * work in. Keyed `<file stem>/<node id>`.
     *
     * This table **is** the rule as it applies to the shipped catalogue — it is
     * meant to be read and argued with in review. A prompted node missing from
     * it fails the test rather than defaulting to anything, so a new preset
     * cannot quietly reintroduce the defect this table was written to close.
     */
    private val promptLanguageExpectations: Map<String, PromptLanguage> = mapOf(
        // Simple presets: the LLM node IS the answer the user reads.
        "clarify_then_act/clarify" to PromptLanguage.USER,
        "clarify_then_act/lite_rt" to PromptLanguage.USER,
        "cloud_assist/cloud" to PromptLanguage.USER,
        "local_only_qa/lite_rt" to PromptLanguage.USER,

        // Research: the plan is machine-facing, the findings are what OUTPUT forwards.
        "multi_step_research/decomposition" to PromptLanguage.ENGLISH,
        "multi_step_research/cloud" to PromptLanguage.USER,

        // Routing keyword, then two answering branches.
        "routed_local_cloud/router" to PromptLanguage.ENGLISH,
        "routed_local_cloud/lite_rt" to PromptLanguage.USER,
        "routed_local_cloud/cloud" to PromptLanguage.USER,

        // The captured note is read by the user; the file path is a fixed literal.
        "share_handler/node-2" to PromptLanguage.USER,
        "share_handler/node-4" to PromptLanguage.USER,

        // Full agent. The intake restatement and the task plan feed routers and
        // tool calls — those are the ones that broke tool calling.
        "showcase_full_agent/node-23" to PromptLanguage.ENGLISH,
        "showcase_full_agent/node-3" to PromptLanguage.ENGLISH,
        "showcase_full_agent/node-4" to PromptLanguage.USER,
        "showcase_full_agent/node-7" to PromptLanguage.DATA,
        "showcase_full_agent/node-8" to PromptLanguage.USER,
        "showcase_full_agent/node-9" to PromptLanguage.DATA,
        "showcase_full_agent/node-12" to PromptLanguage.USER,
        "showcase_full_agent/node-13" to PromptLanguage.ENGLISH,
        "showcase_full_agent/node-15" to PromptLanguage.ENGLISH,
        "showcase_full_agent/node-16" to PromptLanguage.USER,
        "showcase_full_agent/node-22" to PromptLanguage.ENGLISH,

        // Research to file: the report body is read by the user, the slug is a path.
        "showcase_research_to_file/query_builder" to PromptLanguage.DATA,
        "showcase_research_to_file/distiller" to PromptLanguage.ENGLISH,
        "showcase_research_to_file/synthesis" to PromptLanguage.USER,
        "showcase_research_to_file/output" to PromptLanguage.USER,

        // The target language is the node's entire purpose.
        "styled_translation/node-2" to PromptLanguage.DATA,

        // Sub-pipelines composed by the full agent.
        "subtask_clarify/node-2" to PromptLanguage.USER,
        "subtask_lookup/node-2" to PromptLanguage.DATA,
        "subtask_process/node-2" to PromptLanguage.ENGLISH,

        // Tool-using agent.
        "tool_using_react/query_builder" to PromptLanguage.DATA,
        "tool_using_react/summary" to PromptLanguage.USER,
        "tool_using_react/direct_answer" to PromptLanguage.USER,

        // Companion. Only the mood keyword is machine-facing; the private note is
        // read solely by the persona nodes, which reply in the user's language
        // anyway — translating personal facts to English and back would lose
        // nuance for no correctness gain.
        "virtual_companion_mood_router/node-2" to PromptLanguage.USER,
        "virtual_companion_mood_router/node-3" to PromptLanguage.ENGLISH,
        "virtual_companion_mood_router/node-4" to PromptLanguage.USER,
        "virtual_companion_mood_router/node-5" to PromptLanguage.USER,
        "virtual_companion_mood_router/node-6" to PromptLanguage.USER,
        "virtual_companion_mood_router/node-7" to PromptLanguage.USER,
        "virtual_companion_mood_router/node-8" to PromptLanguage.USER,
    )

    @Test
    fun `every prompted bundled node states which language it works in`() {
        val problems = mutableListOf<String>()
        val seen = mutableSetOf<String>()

        forEachBundledFile { file ->
            val stem = file.nameWithoutExtension
            parseAsSuccess(file).preset.graph.nodes.forEach { node ->
                val prompt = node.systemPrompt?.takeIf { it.isNotBlank() } ?: return@forEach
                val key = "$stem/${node.id}"
                seen += key
                when (promptLanguageExpectations[key]) {
                    null ->
                        problems += "$key has a systemPrompt but no entry in promptLanguageExpectations — " +
                            "decide whether its output is read by the pipeline (ENGLISH), by the user (USER), " +
                            "or is language-bearing data (DATA)"
                    PromptLanguage.ENGLISH -> if (!prompt.contains("in English")) {
                        problems += "$key is machine-facing but does not tell the model to work in English"
                    }
                    PromptLanguage.USER -> if (!prompt.contains("\$LANG")) {
                        problems += "$key is read by the user but does not reference \$LANG"
                    }
                    PromptLanguage.DATA -> Unit
                }
            }
        }

        val stale = promptLanguageExpectations.keys - seen
        if (stale.isNotEmpty()) {
            problems += "promptLanguageExpectations lists nodes that no longer carry a prompt: $stale"
        }
        assertTrue(problems.joinToString("\n"), problems.isEmpty())
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
