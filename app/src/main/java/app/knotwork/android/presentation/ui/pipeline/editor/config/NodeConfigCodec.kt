@file:Suppress("TooManyFunctions") // 12 node types -> 12 encode/decode pairs by design.

package app.knotwork.android.presentation.ui.pipeline.editor.config

import app.knotwork.android.domain.constants.DefaultPrompts
import app.knotwork.android.domain.models.NodeModel
import app.knotwork.design.components.pipelineeditor.ClarificationConfig
import app.knotwork.design.components.pipelineeditor.CloudConfig
import app.knotwork.design.components.pipelineeditor.ConfirmPolicy
import app.knotwork.design.components.pipelineeditor.DecompositionConfig
import app.knotwork.design.components.pipelineeditor.EvaluationConfig
import app.knotwork.design.components.pipelineeditor.IfConditionConfig
import app.knotwork.design.components.pipelineeditor.InputConfig
import app.knotwork.design.components.pipelineeditor.IntentClass
import app.knotwork.design.components.pipelineeditor.IntentRouterConfig
import app.knotwork.design.components.pipelineeditor.LiteRtConfig
import app.knotwork.design.components.pipelineeditor.NodeConfig
import app.knotwork.design.components.pipelineeditor.OutputConfig
import app.knotwork.design.components.pipelineeditor.OutputFormat
import app.knotwork.design.components.pipelineeditor.QueueProcessorConfig
import app.knotwork.design.components.pipelineeditor.SummaryConfig
import app.knotwork.design.components.pipelineeditor.SummaryFormat
import app.knotwork.design.components.pipelineeditor.ToolArgument
import app.knotwork.design.components.pipelineeditor.ToolConfig
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import timber.log.Timber
import app.knotwork.android.domain.models.NodeType as DomainNodeType
import app.knotwork.design.components.pipelineeditor.NodeType as CatalogNodeType

/**
 * Bridges between the catalog's typed [NodeConfig] sealed family and the production-domain
 * [NodeModel] persistence layer.
 *
 * Two responsibilities:
 *  - **Encode / decode** the configuration as a JSON blob written to `NodeModel.configJson`.
 *    The blob carries the typed
 *    payload introduced by the new `NodeConfigSheet`.
 *  - **Derive defaults** from the legacy flat fields (`systemPrompt`, `cloudProvider`,
 *    `toolName`, `conditionComplexity`, …) when a row was saved by an older app version. This way
 *    pre-existing pipelines open in the new editor with sensible field values without
 *    forcing a one-shot data migration.
 *
 * Pure Kotlin — Android-free — so the codec is unit-testable on the JVM. JSON I/O uses
 * `org.json.JSONObject` per the project's API-conventions doc.
 */
internal object NodeConfigCodec {

    // Schema marker — bump when the JSON shape changes incompatibly.
    private const val SCHEMA_VERSION_KEY = "v"
    private const val SCHEMA_VERSION = 1

    // Common envelope keys.
    private const val TYPE_KEY = "type"
    private const val TITLE_KEY = "title"
    private const val DESCRIPTION_KEY = "description"

    /**
     * Decodes the [NodeConfig] backing [node]. Falls back to legacy flat fields when
     * [NodeModel.configJson] is `null` or malformed.
     *
     * @return the typed configuration; the catalog form receives this as its starting value.
     */
    fun decode(node: NodeModel): NodeConfig {
        val payload = node.configJson?.takeIf { it.isNotBlank() }
        if (payload != null) {
            val parsed = runCatching { JSONObject(payload) }.getOrNull()
            if (parsed != null) {
                return decodeFromJson(parsed, node)
            } else {
                Timber.w("NodeConfig payload for node=%s is not valid JSON; falling back to legacy", node.id)
            }
        }
        return deriveFromLegacy(node)
    }

    /**
     * Encodes [config] to a JSON string suitable for [NodeModel.configJson] persistence.
     *
     * @return a stable JSON document keyed by [SCHEMA_VERSION_KEY], [TYPE_KEY], and per-type fields.
     */
    fun encode(config: NodeConfig): String {
        val json = JSONObject()
            .put(SCHEMA_VERSION_KEY, SCHEMA_VERSION)
            .put(TYPE_KEY, NodeTypeMapper.toDomain(config.type).name)
            .put(TITLE_KEY, config.title)
        config.description?.let { json.put(DESCRIPTION_KEY, it) }
        when (config) {
            is InputConfig -> encodeInput(json, config)
            is OutputConfig -> encodeOutput(json, config)
            is LiteRtConfig -> encodeLiteRt(json, config)
            is CloudConfig -> encodeCloud(json, config)
            is IntentRouterConfig -> encodeIntentRouter(json, config)
            is IfConditionConfig -> encodeIfCondition(json, config)
            is ClarificationConfig -> encodeClarification(json, config)
            is ToolConfig -> encodeTool(json, config)
            is DecompositionConfig -> encodeDecomposition(json, config)
            is QueueProcessorConfig -> encodeQueueProcessor(json, config)
            is EvaluationConfig -> encodeEvaluation(json, config)
            is SummaryConfig -> encodeSummary(json, config)
        }
        return json.toString()
    }

    /**
     * Projects an edited [NodeConfig] back onto its source [NodeModel], preserving graph
     * identity (id, position, context flags) and updating both the JSON payload and the
     * legacy flat fields the runtime engine still reads.
     *
     * @return a copy of [source] with the new payload encoded into `configJson` and the
     * matching flat columns (label / systemPrompt / cloudProvider / toolName / clarification
     * timeout / condition fields) refreshed from [config].
     */
    fun apply(source: NodeModel, config: NodeConfig): NodeModel {
        val withJson = source.copy(
            label = config.title,
            configJson = encode(config),
        )
        return when (config) {
            is LiteRtConfig -> withJson.copy(
                systemPrompt = config.systemPrompt,
                // Blank `modelId` is the explicit "Active model" sentinel
                // — persist as `null` on
                // the domain row so `LoadModelUseCase(null)` falls back to
                // the current `LocalModelRepository.getActiveModel()` at
                // execute time. Earlier this branch preserved the previous
                // `withJson.modelPath`, which froze the node to whichever
                // model happened to be active when the user first opened
                // the form.
                modelPath = config.modelId.takeIf { it.isNotBlank() },
            )
            is CloudConfig -> withJson.copy(
                systemPrompt = config.systemPrompt,
                // `toWireId` keeps `CloudProvider.AUTO` as the "auto" sentinel
                // (rather than collapsing to a concrete provider), so saving the
                // sheet does not rewrite an auto-routing node to OpenAI.
                cloudProvider = CloudProviderMapper.toWireId(config.provider),
            )
            is ToolConfig -> withJson.copy(toolName = config.toolId.takeIf { it.isNotBlank() })
            is IfConditionConfig -> withJson.copy(conditionPrompt = config.expression)
            is ClarificationConfig -> withJson.copy(
                clarificationTimeoutMs = config.timeoutMs?.toLong(),
                systemPrompt = config.questionTemplate,
            )
            // OUTPUT mirrors `systemPrompt` onto the domain row so the
            // `OutputNodeExecutor` can read it via `node.systemPrompt`.
            // Empty string is allowed
            // and the executor reads it as "echo upstream verbatim".
            is OutputConfig -> withJson.copy(systemPrompt = config.systemPrompt)
            is IntentRouterConfig,
            is DecompositionConfig,
            is QueueProcessorConfig,
            is EvaluationConfig,
            is SummaryConfig,
            is InputConfig,
            -> withJson
        }
    }

    /**
     * Builds a fresh default [NodeConfig] for [type] — used by the editor when the user picks
     * a node from the radial quick-add menu and the [NodeConfigSheet] opens for the first time.
     *
     * @param type the catalog node type the form will render for.
     * @param title initial title (typically the node's label or the type's display label).
     */
    fun defaultFor(type: CatalogNodeType, title: String): NodeConfig = when (type) {
        CatalogNodeType.INPUT -> InputConfig(title = title)
        CatalogNodeType.OUTPUT -> OutputConfig(title = title)
        CatalogNodeType.LITE_RT -> LiteRtConfig(
            title = title,
            systemPrompt = DefaultPrompts.getDefaultPromptForNodeType(DomainNodeType.LITE_RT).orEmpty(),
        )
        CatalogNodeType.CLOUD -> CloudConfig(
            title = title,
            systemPrompt = DefaultPrompts.getDefaultPromptForNodeType(DomainNodeType.CLOUD).orEmpty(),
        )
        CatalogNodeType.INTENT_ROUTER -> IntentRouterConfig(
            title = title,
            classifierPrompt = DefaultPrompts
                .getDefaultPromptForNodeType(DomainNodeType.INTENT_ROUTER)
                .orEmpty(),
            classes = listOf(IntentClass(name = "simple"), IntentClass(name = "complex")),
        )
        CatalogNodeType.IF_CONDITION -> IfConditionConfig(title = title)
        CatalogNodeType.CLARIFICATION -> ClarificationConfig(
            title = title,
            questionTemplate = DefaultPrompts
                .getDefaultPromptForNodeType(DomainNodeType.CLARIFICATION)
                .orEmpty(),
        )
        CatalogNodeType.TOOL -> ToolConfig(title = title)
        CatalogNodeType.DECOMPOSITION -> DecompositionConfig(
            title = title,
            planningPrompt = DefaultPrompts
                .getDefaultPromptForNodeType(DomainNodeType.DECOMPOSITION)
                .orEmpty(),
        )
        CatalogNodeType.QUEUE_PROCESSOR -> QueueProcessorConfig(title = title)
        CatalogNodeType.EVALUATION -> EvaluationConfig(
            title = title,
            criteriaPrompt = DefaultPrompts
                .getDefaultPromptForNodeType(DomainNodeType.EVALUATION)
                .orEmpty(),
        )
        CatalogNodeType.SUMMARY -> SummaryConfig(
            title = title,
            customPrompt = DefaultPrompts
                .getDefaultPromptForNodeType(DomainNodeType.SUMMARY),
        )
    }

    // ─────────────────────────────────────────────────────────────────────
    // Decode dispatch
    // ─────────────────────────────────────────────────────────────────────

    private fun decodeFromJson(payload: JSONObject, fallback: NodeModel): NodeConfig {
        val title = payload.optString(TITLE_KEY).ifBlank { fallback.label }
        val description = payload.optStringOrNull(DESCRIPTION_KEY)
        return when (NodeTypeMapper.toCatalog(fallback.type)) {
            CatalogNodeType.INPUT -> decodeInput(payload, title, description)
            CatalogNodeType.OUTPUT -> decodeOutput(payload, title, description)
            CatalogNodeType.LITE_RT -> decodeLiteRt(payload, title, description, fallback)
            CatalogNodeType.CLOUD -> decodeCloud(payload, title, description, fallback)
            CatalogNodeType.INTENT_ROUTER -> decodeIntentRouter(payload, title, description, fallback)
            CatalogNodeType.IF_CONDITION -> decodeIfCondition(payload, title, description, fallback)
            CatalogNodeType.CLARIFICATION -> decodeClarification(payload, title, description, fallback)
            CatalogNodeType.TOOL -> decodeTool(payload, title, description, fallback)
            CatalogNodeType.DECOMPOSITION -> decodeDecomposition(payload, title, description, fallback)
            CatalogNodeType.QUEUE_PROCESSOR -> decodeQueueProcessor(payload, title, description, fallback)
            CatalogNodeType.EVALUATION -> decodeEvaluation(payload, title, description, fallback)
            CatalogNodeType.SUMMARY -> decodeSummary(payload, title, description, fallback)
        }
    }

    // ─────────────────────────────────────────────────────────────────────
    // Legacy-field derivation (older saved rows)
    // ─────────────────────────────────────────────────────────────────────

    private fun deriveFromLegacy(node: NodeModel): NodeConfig {
        val title = node.label.ifBlank { node.type.name }
        // When a legacy node persists with
        // an empty `systemPrompt` (older pipelines created before
        // `DefaultPrompts.getDefaultPromptForNodeType` was wired into NodeModel
        // construction), fall back to the registered default prompt instead of
        // showing an empty field. Users were rightly confused that "standard
        // prompts disappeared" for these node types.
        val systemPromptOrDefault = node.systemPrompt
            ?.takeIf { it.isNotBlank() }
            ?: DefaultPrompts.getDefaultPromptForNodeType(node.type).orEmpty()
        return when (NodeTypeMapper.toCatalog(node.type)) {
            CatalogNodeType.INPUT -> InputConfig(title = title)
            // Legacy rows that pre-date F10 keep their persisted
            // `node.systemPrompt` here so the editor surfaces what they were
            // already sending to the LLM, instead of silently clearing it.
            CatalogNodeType.OUTPUT -> OutputConfig(
                title = title,
                systemPrompt = node.systemPrompt.orEmpty(),
            )
            CatalogNodeType.LITE_RT -> LiteRtConfig(
                title = title,
                systemPrompt = systemPromptOrDefault,
                modelId = node.modelPath.orEmpty(),
            )
            CatalogNodeType.CLOUD -> CloudConfig(
                title = title,
                systemPrompt = systemPromptOrDefault,
                provider = CloudProviderMapper.fromWireId(node.cloudProvider),
            )
            CatalogNodeType.INTENT_ROUTER -> IntentRouterConfig(
                title = title,
                classifierPrompt = systemPromptOrDefault,
            )
            CatalogNodeType.IF_CONDITION -> IfConditionConfig(
                title = title,
                expression = node.conditionPrompt.orEmpty(),
            )
            CatalogNodeType.CLARIFICATION -> ClarificationConfig(
                title = title,
                questionTemplate = systemPromptOrDefault,
                timeoutMs = node.clarificationTimeoutMs?.toInt(),
            )
            CatalogNodeType.TOOL -> ToolConfig(
                title = title,
                toolId = node.toolName.orEmpty(),
            )
            CatalogNodeType.DECOMPOSITION -> DecompositionConfig(
                title = title,
                planningPrompt = systemPromptOrDefault,
            )
            CatalogNodeType.QUEUE_PROCESSOR -> QueueProcessorConfig(title = title)
            CatalogNodeType.EVALUATION -> EvaluationConfig(
                title = title,
                criteriaPrompt = systemPromptOrDefault,
            )
            CatalogNodeType.SUMMARY -> SummaryConfig(
                title = title,
                customPrompt = systemPromptOrDefault.takeIf { it.isNotBlank() },
            )
        }
    }

    // ─────────────────────────────────────────────────────────────────────
    // Per-type encoders
    // ─────────────────────────────────────────────────────────────────────

    private fun encodeInput(json: JSONObject, c: InputConfig) {
        json.put("inputName", c.inputName)
        c.schemaJson?.let { json.put("schemaJson", it) }
    }

    private fun encodeOutput(json: JSONObject, c: OutputConfig) {
        json.put("format", c.format.name)
        json.put("systemPrompt", c.systemPrompt)
    }

    private fun encodeLiteRt(json: JSONObject, c: LiteRtConfig) {
        json.put("modelId", c.modelId)
        json.put("systemPrompt", c.systemPrompt)
        json.put("temperature", c.temperature.toDouble())
        json.put("topP", c.topP.toDouble())
        json.put("maxNewTokens", c.maxNewTokens)
        json.put("stopTokens", JSONArray(c.stopTokens))
    }

    private fun encodeCloud(json: JSONObject, c: CloudConfig) {
        json.put("provider", c.provider.name)
        json.put("model", c.model)
        json.put("systemPrompt", c.systemPrompt)
        json.put("temperature", c.temperature.toDouble())
        json.put("maxTokens", c.maxTokens)
        json.put("timeoutMs", c.timeoutMs)
    }

    private fun encodeIntentRouter(json: JSONObject, c: IntentRouterConfig) {
        val classes = JSONArray()
        c.classes.forEach { cls ->
            classes.put(
                JSONObject()
                    .put("name", cls.name)
                    .put("description", cls.description)
                    .put("examples", JSONArray(cls.examples)),
            )
        }
        json.put("classes", classes)
        json.put("classifierPrompt", c.classifierPrompt)
        c.fallbackClass?.let { json.put("fallbackClass", it) }
    }

    private fun encodeIfCondition(json: JSONObject, c: IfConditionConfig) {
        json.put("expression", c.expression)
        json.put("labelTrue", c.labelTrue)
        json.put("labelFalse", c.labelFalse)
    }

    private fun encodeClarification(json: JSONObject, c: ClarificationConfig) {
        json.put("questionTemplate", c.questionTemplate)
        json.put("quickReplies", JSONArray(c.quickReplies))
        c.timeoutMs?.let { json.put("timeoutMs", it) }
    }

    private fun encodeTool(json: JSONObject, c: ToolConfig) {
        json.put("toolId", c.toolId)
        val args = JSONArray()
        c.argumentMapping.forEach { arg ->
            args.put(JSONObject().put("name", arg.name).put("expression", arg.expression))
        }
        json.put("argumentMapping", args)
        c.confirmOverride?.let { json.put("confirmOverride", it.name) }
    }

    private fun encodeDecomposition(json: JSONObject, c: DecompositionConfig) {
        json.put("planningPrompt", c.planningPrompt)
        json.put("maxSubtasks", c.maxSubtasks)
        c.outputSchemaJson?.let { json.put("outputSchemaJson", it) }
    }

    private fun encodeQueueProcessor(json: JSONObject, c: QueueProcessorConfig) {
        json.put("inputList", c.inputList)
        json.put("itemVariable", c.itemVariable)
        json.put("parallelism", c.parallelism)
        json.put("stopOnError", c.stopOnError)
    }

    private fun encodeEvaluation(json: JSONObject, c: EvaluationConfig) {
        json.put("criteriaPrompt", c.criteriaPrompt)
        json.put("maxRetries", c.maxRetries)
    }

    private fun encodeSummary(json: JSONObject, c: SummaryConfig) {
        json.put("format", c.format.name)
        c.customPrompt?.let { json.put("customPrompt", it) }
        json.put("targetLengthChars", c.targetLengthChars)
    }

    // ─────────────────────────────────────────────────────────────────────
    // Per-type decoders
    // ─────────────────────────────────────────────────────────────────────

    private fun decodeInput(p: JSONObject, title: String, description: String?): InputConfig = InputConfig(
        title = title,
        description = description,
        inputName = p.optString("inputName").ifBlank { "user.message" },
        schemaJson = p.optStringOrNull("schemaJson"),
    )

    private fun decodeOutput(p: JSONObject, title: String, description: String?): OutputConfig = OutputConfig(
        title = title,
        description = description,
        format = enumOrDefault(p.optStringOrNull("format"), OutputFormat.PLAIN_TEXT),
        // Optional — older persisted rows simply lack this key and fall back to the default
        // empty string (echo-through mode).
        systemPrompt = p.optString("systemPrompt"),
    )

    private fun decodeLiteRt(p: JSONObject, title: String, description: String?, fb: NodeModel): LiteRtConfig =
        LiteRtConfig(
            title = title,
            description = description,
            modelId = p.optString("modelId").ifBlank { fb.modelPath.orEmpty() },
            systemPrompt = p.optString("systemPrompt").ifBlank { fb.systemPrompt.orEmpty() },
            temperature = p.optDouble("temperature", DEFAULT_TEMPERATURE).toFloat(),
            topP = p.optDouble("topP", DEFAULT_TOP_P).toFloat(),
            maxNewTokens = p.optInt("maxNewTokens", DEFAULT_MAX_NEW_TOKENS),
            stopTokens = p.optStringList("stopTokens"),
        )

    private fun decodeCloud(p: JSONObject, title: String, description: String?, fb: NodeModel): CloudConfig =
        CloudConfig(
            title = title,
            description = description,
            provider = enumOrDefault(
                p.optStringOrNull("provider"),
                CloudProviderMapper.fromWireId(fb.cloudProvider),
            ),
            model = p.optString("model"),
            systemPrompt = p.optString("systemPrompt").ifBlank { fb.systemPrompt.orEmpty() },
            temperature = p.optDouble("temperature", DEFAULT_TEMPERATURE).toFloat(),
            maxTokens = p.optInt("maxTokens", DEFAULT_MAX_TOKENS),
            timeoutMs = p.optInt("timeoutMs", DEFAULT_TIMEOUT_MS),
        )

    private fun decodeIntentRouter(
        p: JSONObject,
        title: String,
        description: String?,
        fb: NodeModel,
    ): IntentRouterConfig = IntentRouterConfig(
        title = title,
        description = description,
        classes = p.optJSONArray("classes")?.let { arr ->
            (0 until arr.length()).mapNotNull { i ->
                val obj = arr.optJSONObject(i) ?: return@mapNotNull null
                IntentClass(
                    name = obj.optString("name"),
                    description = obj.optString("description"),
                    examples = obj.optJSONArray("examples")?.toStringList().orEmpty(),
                )
            }
        }.orEmpty(),
        classifierPrompt = p.optString("classifierPrompt").ifBlank { fb.systemPrompt.orEmpty() },
        fallbackClass = p.optStringOrNull("fallbackClass"),
    )

    private fun decodeIfCondition(
        p: JSONObject,
        title: String,
        description: String?,
        fb: NodeModel,
    ): IfConditionConfig = IfConditionConfig(
        title = title,
        description = description,
        expression = p.optString("expression").ifBlank { fb.conditionPrompt.orEmpty() },
        labelTrue = p.optString("labelTrue").ifBlank { "True" },
        labelFalse = p.optString("labelFalse").ifBlank { "False" },
    )

    private fun decodeClarification(
        p: JSONObject,
        title: String,
        description: String?,
        fb: NodeModel,
    ): ClarificationConfig = ClarificationConfig(
        title = title,
        description = description,
        questionTemplate = p.optString("questionTemplate").ifBlank { fb.systemPrompt.orEmpty() },
        quickReplies = p.optStringList("quickReplies"),
        timeoutMs = if (p.has("timeoutMs")) p.optInt("timeoutMs") else fb.clarificationTimeoutMs?.toInt(),
    )

    private fun decodeTool(p: JSONObject, title: String, description: String?, fb: NodeModel): ToolConfig = ToolConfig(
        title = title,
        description = description,
        toolId = p.optString("toolId").ifBlank { fb.toolName.orEmpty() },
        argumentMapping = p.optJSONArray("argumentMapping")?.let { arr ->
            (0 until arr.length()).mapNotNull { i ->
                val obj = arr.optJSONObject(i) ?: return@mapNotNull null
                ToolArgument(name = obj.optString("name"), expression = obj.optString("expression"))
            }
        }.orEmpty(),
        confirmOverride = enumOrNull<ConfirmPolicy>(p.optStringOrNull("confirmOverride")),
    )

    private fun decodeDecomposition(
        p: JSONObject,
        title: String,
        description: String?,
        fb: NodeModel,
    ): DecompositionConfig = DecompositionConfig(
        title = title,
        description = description,
        planningPrompt = p.optString("planningPrompt").ifBlank { fb.systemPrompt.orEmpty() },
        maxSubtasks = p.optInt("maxSubtasks", DEFAULT_MAX_SUBTASKS),
        outputSchemaJson = p.optStringOrNull("outputSchemaJson"),
    )

    private fun decodeQueueProcessor(
        p: JSONObject,
        title: String,
        description: String?,
        @Suppress("UNUSED_PARAMETER") fb: NodeModel,
    ): QueueProcessorConfig = QueueProcessorConfig(
        title = title,
        description = description,
        inputList = p.optString("inputList"),
        itemVariable = p.optString("itemVariable").ifBlank { "item" },
        parallelism = p.optInt("parallelism", DEFAULT_PARALLELISM),
        stopOnError = p.optBoolean("stopOnError", true),
    )

    private fun decodeEvaluation(p: JSONObject, title: String, description: String?, fb: NodeModel): EvaluationConfig =
        EvaluationConfig(
            title = title,
            description = description,
            criteriaPrompt = p.optString("criteriaPrompt").ifBlank { fb.systemPrompt.orEmpty() },
            maxRetries = p.optInt("maxRetries", DEFAULT_MAX_RETRIES),
        )

    private fun decodeSummary(p: JSONObject, title: String, description: String?, fb: NodeModel): SummaryConfig =
        SummaryConfig(
            title = title,
            description = description,
            format = enumOrDefault(p.optStringOrNull("format"), SummaryFormat.BULLETS),
            customPrompt = p.optStringOrNull("customPrompt") ?: fb.systemPrompt,
            targetLengthChars = p.optInt("targetLengthChars", DEFAULT_SUMMARY_LENGTH),
        )

    // ─────────────────────────────────────────────────────────────────────
    // JSON helpers
    // ─────────────────────────────────────────────────────────────────────

    private fun JSONObject.optStringOrNull(key: String): String? = if (has(key) && !isNull(key)) {
        optString(key).takeIf { it.isNotEmpty() }
    } else {
        null
    }

    private fun JSONObject.optStringList(key: String): List<String> = try {
        optJSONArray(key)?.toStringList().orEmpty()
    } catch (e: JSONException) {
        Timber.w(e, "Failed to parse %s as list of strings", key)
        emptyList()
    }

    private fun JSONArray.toStringList(): List<String> = (0 until length()).map { optString(it) }

    private inline fun <reified T : Enum<T>> enumOrDefault(raw: String?, default: T): T = if (raw == null) {
        default
    } else {
        runCatching { enumValueOf<T>(raw) }.getOrDefault(default)
    }

    private inline fun <reified T : Enum<T>> enumOrNull(raw: String?): T? = raw?.let {
        runCatching { enumValueOf<T>(it) }.getOrNull()
    }

    // Numeric defaults for node configuration.
    private const val DEFAULT_TEMPERATURE = 0.7
    private const val DEFAULT_TOP_P = 0.9
    private const val DEFAULT_MAX_NEW_TOKENS = 512
    private const val DEFAULT_MAX_TOKENS = 1_024
    private const val DEFAULT_TIMEOUT_MS = 30_000
    private const val DEFAULT_MAX_SUBTASKS = 5
    private const val DEFAULT_PARALLELISM = 1
    private const val DEFAULT_MAX_RETRIES = 2
    private const val DEFAULT_SUMMARY_LENGTH = 600
}
