package ai.agent.android.domain.pipelineio

import ai.agent.android.domain.models.PipelineGraph
import ai.agent.android.domain.models.PipelineImportOutcome
import ai.agent.android.domain.models.PipelinePreset
import ai.agent.android.domain.models.PipelinePresetImportOutcome
import ai.agent.android.domain.models.PresetCategory
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

/**
 * Two-way mapper between [PipelinePreset] and the schema-versioned JSON
 * format used by the bundled `assets/presets/pipelines/*.json` files and
 * by the browser-side editor's `*.preset.json` export (introduced in
 * Phase 24 / Task 7).
 *
 * The preset format is **a strict superset** of the pipeline format owned
 * by [PipelineJsonSerializer]: it embeds the same `schemaVersion` / `id` /
 * `name` / `updatedAt` / `nodes` / `connections` document and adds three
 * preset-only top-level fields (`category`, `tags`, `description`). Delegating
 * graph (de)serialisation to [PipelineJsonSerializer] keeps the two formats
 * forever in sync without duplicating the node-shape mapping.
 *
 * ### Schema (version 1)
 *
 * ```
 * {
 *   "schemaVersion": 1,
 *   "id": "local_only_qa",
 *   "name": "Local-only Q&A",
 *   "description": "INPUT → LITE_RT → OUTPUT, no network",
 *   "category": "local",
 *   "tags": ["offline", "qa"],
 *   "updatedAt": 1730000000000,
 *   "nodes": [ ... ],
 *   "connections": [ ... ]
 * }
 * ```
 *
 * Uses `org.json` per the project's API conventions.
 */
object PipelinePresetJsonSerializer {

    /**
     * Renders [preset] into the schema-versioned JSON form.
     *
     * The embedded graph is serialised through [PipelineJsonSerializer.serialize]
     * and merged with the preset-only fields, so any future change to the
     * pipeline schema is picked up here for free.
     *
     * @param preset The preset to serialise.
     * @return JSON text suitable for writing to disk or shipping in
     *   `assets/presets/pipelines/`.
     */
    fun serialize(preset: PipelinePreset): String {
        // Delegate graph serialisation to PipelineJsonSerializer so we never
        // have to mirror the node-shape mapping. The returned document
        // already carries the canonical schemaVersion / id / name /
        // updatedAt / nodes / connections layout.
        val root = JSONObject(PipelineJsonSerializer.serialize(preset.graph))

        // Overwrite the inner graph id / name with the preset's own id /
        // name so the on-disk file presents the preset identity to the
        // reader (the inner graph is only a *template* — its ids will be
        // regenerated on instantiation anyway).
        root.put("id", preset.id)
        root.put("name", preset.name)

        root.put("description", preset.description)
        root.put("category", preset.category.key)
        val tagsJson = JSONArray()
        preset.tags.forEach { tagsJson.put(it) }
        root.put("tags", tagsJson)

        return root.toString()
    }

    /**
     * Parses [jsonText] into a [PipelinePreset] and reports the outcome.
     *
     * The function never throws — every parse error is converted into a
     * [PipelinePresetImportOutcome.Failure] with a human-readable message
     * so callers (the bundled-catalogue loader and any UI import flow)
     * can surface it without try/catch boilerplate.
     *
     * @param jsonText Raw JSON text.
     * @param isBundled `true` when [jsonText] originates from
     *   `assets/presets/pipelines/`, `false` when imported from disk or
     *   from the browser editor. Stored verbatim on the resulting
     *   [PipelinePreset].
     */
    fun parse(jsonText: String, isBundled: Boolean): PipelinePresetImportOutcome {
        val root: JSONObject = try {
            JSONObject(jsonText)
        } catch (e: JSONException) {
            return PipelinePresetImportOutcome.Failure("Invalid JSON: ${e.message}")
        }

        // Delegate the pipeline-graph half to PipelineJsonSerializer. We
        // pass the full document — PipelineJsonSerializer ignores any
        // top-level fields it does not recognise, so the preset-only
        // `category` / `tags` / `description` slots survive untouched.
        return when (val graphOutcome = PipelineJsonSerializer.parse(jsonText)) {
            is PipelineImportOutcome.Failure -> PipelinePresetImportOutcome.Failure(graphOutcome.message)

            is PipelineImportOutcome.Success -> PipelinePresetImportOutcome.Success(
                preset = buildPreset(root = root, graph = graphOutcome.graph, isBundled = isBundled),
            )

            is PipelineImportOutcome.SchemaMismatch -> PipelinePresetImportOutcome.SchemaMismatch(
                preset = buildPreset(root = root, graph = graphOutcome.graph, isBundled = isBundled),
                foundVersion = graphOutcome.foundVersion,
                expectedVersion = graphOutcome.expectedVersion,
            )
        }
    }

    private fun buildPreset(
        root: JSONObject,
        graph: PipelineGraph,
        isBundled: Boolean,
    ): PipelinePreset {
        // The preset id and the embedded graph id are independent: a
        // bundled file's id is its filename stem (carried through the
        // top-level `id` field), while the graph keeps its own id so the
        // round-trip through PipelineJsonSerializer is lossless. We use
        // the document's top-level `id` for the preset identity.
        val presetId = root.optString("id").takeIf { it.isNotBlank() } ?: graph.id
        val name = root.optString("name").takeIf { it.isNotBlank() } ?: graph.name
        val description = if (root.has("description") && !root.isNull("description")) {
            root.optString("description", "")
        } else {
            ""
        }
        val category = PresetCategory.fromKey(
            if (root.has("category") && !root.isNull("category")) root.optString("category") else null,
        )
        val tags = root.optJSONArray("tags")?.let { array ->
            (0 until array.length()).mapNotNull { i ->
                array.optString(i).takeIf { it.isNotBlank() }
            }
        } ?: emptyList()

        return PipelinePreset(
            id = presetId,
            name = name,
            description = description,
            category = category,
            graph = graph,
            tags = tags,
            isBundled = isBundled,
        )
    }
}
