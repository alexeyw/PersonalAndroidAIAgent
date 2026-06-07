package app.knotwork.android.domain.pipelineio

import app.knotwork.android.domain.models.ConnectionModel
import app.knotwork.android.domain.models.NodeContextConfig
import app.knotwork.android.domain.models.NodeModel
import app.knotwork.android.domain.models.NodeType
import app.knotwork.android.domain.models.PipelineGraph
import app.knotwork.android.domain.models.PipelineImportOutcome
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

/**
 * Two-way mapper between [PipelineGraph] and the schema-versioned JSON
 * format consumed by the browser-side editor (`pipeline-editor.html`) and
 * by other instances of this application.
 *
 * ### Schema (version 1)
 *
 * ```
 * {
 *   "schemaVersion": 1,
 *   "id": "<uuid>",
 *   "name": "<display name>",
 *   "updatedAt": 1730000000000,
 *   "nodes": [
 *     {
 *       "id": "node-1",
 *       "type": "CLOUD",
 *       "position": { "x": 120.0, "y": 80.0 },
 *       "label": "Cloud",
 *       "config": {
 *         "systemPrompt": "...",
 *         "cloudProvider": "auto",
 *         "modelPath": "",
 *         "toolName": "",
 *         "clarificationTimeoutMs": 60000,
 *         "conditionPrompt": "",
 *         "conditionKeywords": "",
 *         "conditionComplexity": null
 *       },
 *       "contextConfig": { "chatHistory": true, ... },
 *       "nodeConfig": { "v": 1, "type": "CLOUD", "title": "Cloud", ... }
 *     }
 *   ],
 *   "connections": [
 *     { "id": "conn-1", "fromNodeId": "node-1", "toNodeId": "node-2", "label": "" }
 *   ]
 * }
 * ```
 *
 * ### Rich per-node config (`nodeConfig`)
 *
 * The optional `nodeConfig` object carries the full
 * `NodeConfig` payload (the `NodeConfigCodec` envelope: `{ "v": 1,
 * "type", "title", ...type-specific... }`) so the browser editor and the
 * in-app editor can round-trip every form field, not just the flat
 * `config` subset the runtime reads. It is treated as an **opaque blob**
 * here — stored into / read from [NodeModel.configJson] verbatim, never
 * interpreted by this domain-layer serializer (only the presentation-layer
 * `NodeConfigCodec` parses it). The field is additive and optional:
 * documents without it (older exports, hand-written presets) parse fine and
 * the app re-derives the rich config from the flat fields on first edit.
 *
 * ### Forward-compatibility
 *
 * Unknown fields in `config` (e.g. `intentRouterPrompt` produced by a
 * future editor version) are silently dropped on parse — they are not
 * representable in the current [NodeModel]. The caller is expected to
 * surface a [PipelineImportOutcome.SchemaMismatch] dialog when the
 * `schemaVersion` differs so the user knows configuration may have been
 * stripped.
 *
 * Uses `org.json` per the project's API conventions.
 */
object PipelineJsonSerializer {

    /**
     * Version stamp emitted on every [serialize] call and validated by [parse].
     *
     * Mismatches surface as [PipelineImportOutcome.SchemaMismatch] so the UI can warn
     * the user that a file produced by a different build of the editor may have fields
     * the current schema cannot represent.
     */
    const val CURRENT_SCHEMA_VERSION: Int = 1

    /* ----------------------------------------------------------------- *
     *  Serialise
     * ----------------------------------------------------------------- */

    /**
     * Renders [graph] into the schema-versioned JSON form. The output is
     * guaranteed to round-trip back through [parse] producing a
     * [PipelineImportOutcome.Success] equivalent to [graph].
     */
    fun serialize(graph: PipelineGraph): String {
        val root = JSONObject()
        root.put("schemaVersion", CURRENT_SCHEMA_VERSION)
        root.put("id", graph.id)
        root.put("name", graph.name)
        root.put("updatedAt", graph.updatedAt)

        val nodesJson = JSONArray()
        graph.nodes.forEach { node -> nodesJson.put(serializeNode(node)) }
        root.put("nodes", nodesJson)

        val connectionsJson = JSONArray()
        graph.connections.forEach { c -> connectionsJson.put(serializeConnection(c)) }
        root.put("connections", connectionsJson)

        return root.toString()
    }

    private fun serializeNode(node: NodeModel): JSONObject {
        val position = JSONObject()
            .put("x", node.x.toDouble())
            .put("y", node.y.toDouble())

        val config = JSONObject()
            .put("systemPrompt", node.systemPrompt ?: JSONObject.NULL)
            .put("cloudProvider", node.cloudProvider ?: JSONObject.NULL)
            .put("modelPath", node.modelPath ?: JSONObject.NULL)
            .put("toolName", node.toolName ?: JSONObject.NULL)
            .put("clarificationTimeoutMs", node.clarificationTimeoutMs ?: JSONObject.NULL)
            .put("conditionPrompt", node.conditionPrompt ?: JSONObject.NULL)
            .put("conditionKeywords", node.conditionKeywords ?: JSONObject.NULL)
            .put("conditionComplexity", node.conditionComplexity ?: JSONObject.NULL)

        val contextConfig = JSONObject()
            .put("chatHistory", node.contextConfig.chatHistory)
            .put("originalTask", node.contextConfig.originalTask)
            .put("nodeInput", node.contextConfig.nodeInput)
            .put("longTermMemory", node.contextConfig.longTermMemory)
            .put("toolResults", node.contextConfig.toolResults)

        val nodeJson = JSONObject()
            .put("id", node.id)
            .put("type", node.type.name)
            .put("position", position)
            .put("label", node.label)
            .put("config", config)
            .put("contextConfig", contextConfig)

        // Embed the rich per-node config (the `NodeConfigCodec` payload stored
        // in [NodeModel.configJson]) as an opaque nested `nodeConfig` object so
        // the browser editor and other app instances can round-trip the full
        // `NodeConfig`. The domain layer intentionally does NOT
        // interpret this blob — it is passed through verbatim, keeping the
        // serializer free of any presentation-layer dependency. The flat
        // `config` block above stays authoritative for the runtime engine.
        // Absent / blank / malformed `configJson` simply omits the key.
        node.configJson
            ?.takeIf { it.isNotBlank() }
            ?.let { raw -> runCatching { JSONObject(raw) }.getOrNull() }
            ?.let { nodeJson.put("nodeConfig", it) }

        return nodeJson
    }

    private fun serializeConnection(c: ConnectionModel): JSONObject = JSONObject()
        .put("id", c.id)
        .put("fromNodeId", c.sourceNodeId)
        .put("toNodeId", c.targetNodeId)
        .put("label", c.label ?: JSONObject.NULL)

    /* ----------------------------------------------------------------- *
     *  Parse
     * ----------------------------------------------------------------- */

    /**
     * Parses [jsonText] into a [PipelineGraph] and reports the outcome.
     *
     * The function never throws — every parse error is converted into a
     * [PipelineImportOutcome.Failure] with a human-readable message so the
     * caller (UI / use case) can surface it without try/catch boilerplate.
     */
    fun parse(jsonText: String): PipelineImportOutcome {
        val root = try {
            JSONObject(jsonText)
        } catch (e: JSONException) {
            return PipelineImportOutcome.Failure("Invalid JSON: ${e.message}")
        }

        if (!root.has("schemaVersion")) {
            return PipelineImportOutcome.Failure("Missing required field: schemaVersion")
        }
        val schemaVersion = root.optInt("schemaVersion", -1)

        val graph = try {
            buildGraph(root)
        } catch (e: PipelineParseException) {
            return PipelineImportOutcome.Failure(e.message ?: "Parse failed")
        } catch (e: JSONException) {
            return PipelineImportOutcome.Failure("Malformed pipeline document: ${e.message}")
        }

        return if (schemaVersion == CURRENT_SCHEMA_VERSION) {
            PipelineImportOutcome.Success(graph)
        } else {
            PipelineImportOutcome.SchemaMismatch(
                graph = graph,
                foundVersion = schemaVersion,
                expectedVersion = CURRENT_SCHEMA_VERSION,
            )
        }
    }

    private fun buildGraph(root: JSONObject): PipelineGraph {
        val id = root.optString("id").takeIf { it.isNotBlank() }
            ?: throw PipelineParseException("Missing required field: id")
        val name = root.optString("name").takeIf { it.isNotBlank() }
            ?: throw PipelineParseException("Missing required field: name")
        val updatedAt = root.optLong("updatedAt", System.currentTimeMillis())

        val nodesJson = root.optJSONArray("nodes")
            ?: throw PipelineParseException("Missing required field: nodes")
        val nodes = (0 until nodesJson.length()).map { i ->
            buildNode(nodesJson.getJSONObject(i), index = i)
        }

        val connectionsJson = root.optJSONArray("connections") ?: JSONArray()
        val nodeIds = nodes.mapTo(mutableSetOf()) { it.id }
        val connections = (0 until connectionsJson.length()).map { i ->
            buildConnection(connectionsJson.getJSONObject(i), index = i, nodeIds = nodeIds)
        }

        return PipelineGraph(
            id = id,
            name = name,
            nodes = nodes,
            connections = connections,
            updatedAt = updatedAt,
        )
    }

    private fun buildNode(json: JSONObject, index: Int): NodeModel {
        val id = json.optString("id").takeIf { it.isNotBlank() }
            ?: throw PipelineParseException("Node #$index missing id")
        val typeRaw = json.optString("type").takeIf { it.isNotBlank() }
            ?: throw PipelineParseException("Node \"$id\" missing type")
        val type = try {
            NodeType.valueOf(typeRaw)
        } catch (e: IllegalArgumentException) {
            throw PipelineParseException("Node \"$id\" has unknown type \"$typeRaw\"")
        }

        val position = json.optJSONObject("position")
        val x = position?.optDouble("x", 0.0)?.toFloat() ?: 0f
        val y = position?.optDouble("y", 0.0)?.toFloat() ?: 0f
        val label = json.optString("label").takeIf { it.isNotBlank() } ?: type.name

        val config = json.optJSONObject("config") ?: JSONObject()
        val contextConfigJson = json.optJSONObject("contextConfig")
        val contextConfig = if (contextConfigJson != null) {
            NodeContextConfig(
                chatHistory = contextConfigJson.optBoolean("chatHistory", true),
                originalTask = contextConfigJson.optBoolean("originalTask", true),
                nodeInput = contextConfigJson.optBoolean("nodeInput", true),
                longTermMemory = contextConfigJson.optBoolean("longTermMemory", true),
                toolResults = contextConfigJson.optBoolean("toolResults", true),
            )
        } else {
            // Legacy / minimal documents fall back to the per-type recommended
            // defaults so the imported graph behaves sensibly out of the box.
            NodeContextConfig.defaultForType(type)
        }

        // Round-trip the opaque rich-config blob when present. Stored verbatim
        // into [NodeModel.configJson]; `NodeConfigCodec` (presentation layer)
        // is the only code that interprets it. Absent ⇒ null, so legacy
        // flat-only documents continue to derive their config on first edit.
        val nodeConfigJson = json.optJSONObject("nodeConfig")?.toString()

        return NodeModel(
            id = id,
            type = type,
            x = x,
            y = y,
            label = label,
            toolName = config.optStringOrNull("toolName"),
            modelPath = config.optStringOrNull("modelPath"),
            conditionComplexity = config.optIntOrNull("conditionComplexity"),
            conditionKeywords = config.optStringOrNull("conditionKeywords"),
            conditionPrompt = config.optStringOrNull("conditionPrompt"),
            systemPrompt = config.optStringOrNull("systemPrompt"),
            cloudProvider = config.optStringOrNull("cloudProvider"),
            clarificationTimeoutMs = config.optLongOrNull("clarificationTimeoutMs"),
            contextConfig = contextConfig,
            configJson = nodeConfigJson,
        )
    }

    // Reason: each `throw` here pinpoints a distinct schema-violation kind
    // (`missing id`, `missing fromNodeId`, `missing toNodeId`, `unknown source`,
    // `unknown target`). Folding them into a single Result<Throwable> would
    // erase the message-specificity that makes import errors actionable.
    @Suppress("ThrowsCount")
    private fun buildConnection(json: JSONObject, index: Int, nodeIds: Set<String>): ConnectionModel {
        val id = json.optString("id").takeIf { it.isNotBlank() }
            ?: throw PipelineParseException("Connection #$index missing id")
        val from = json.optString("fromNodeId").takeIf { it.isNotBlank() }
            ?: throw PipelineParseException("Connection \"$id\" missing fromNodeId")
        val to = json.optString("toNodeId").takeIf { it.isNotBlank() }
            ?: throw PipelineParseException("Connection \"$id\" missing toNodeId")
        if (from !in nodeIds) throw PipelineParseException("Connection \"$id\" references unknown node \"$from\"")
        if (to !in nodeIds) throw PipelineParseException("Connection \"$id\" references unknown node \"$to\"")
        val label = json.optStringOrNull("label")
        return ConnectionModel(id = id, sourceNodeId = from, targetNodeId = to, label = label)
    }

    private class PipelineParseException(message: String) : RuntimeException(message)
}

/* ----------------------------------------------------------------- *
 *  JSONObject helpers — return null instead of empty string / 0 for
 *  fields that are absent or explicitly JSON-null. Default org.json
 *  optString returns "" for both, which loses signal.
 * ----------------------------------------------------------------- */

private fun JSONObject.optStringOrNull(name: String): String? =
    if (!has(name) || isNull(name)) null else getString(name).takeIf { it.isNotEmpty() }

private fun JSONObject.optIntOrNull(name: String): Int? = if (!has(name) || isNull(name)) null else getInt(name)

private fun JSONObject.optLongOrNull(name: String): Long? = if (!has(name) || isNull(name)) null else getLong(name)
