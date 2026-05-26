package app.knotwork.design.components.pipelineeditor

import org.json.JSONException
import org.json.JSONObject

/**
 * Stable identifier for one validatable field inside a [NodeConfig].
 *
 * The validator returns `Map<FieldId, ValidationFailure>` so forms can
 * look up the inline error for the field they are rendering without
 * re-checking the same rule. Using an enum (rather than string keys)
 * keeps every callsite refactor-safe.
 */
enum class FieldId {
    TITLE,
    DESCRIPTION,
    INPUT_NAME,
    SCHEMA_JSON,
    FORMAT,
    MODEL_ID,
    SYSTEM_PROMPT,
    TEMPERATURE,
    TOP_P,
    MAX_NEW_TOKENS,
    STOP_TOKENS,
    PROVIDER,
    MODEL,
    MAX_TOKENS,
    TIMEOUT_MS,
    CLASSES,
    CLASSIFIER_PROMPT,
    FALLBACK_CLASS,
    EXPRESSION,
    LABEL_TRUE,
    LABEL_FALSE,
    QUESTION_TEMPLATE,
    QUICK_REPLIES,
    TIMEOUT_OPTIONAL,
    TOOL_ID,
    ARGUMENT_MAPPING,
    CONFIRM_OVERRIDE,
    PLANNING_PROMPT,
    MAX_SUBTASKS,
    OUTPUT_SCHEMA_JSON,
    INPUT_LIST,
    ITEM_VARIABLE,
    PARALLELISM,
    STOP_ON_ERROR,
    CRITERIA_PROMPT,
    MAX_RETRIES,
    CUSTOM_PROMPT,
    TARGET_LENGTH_CHARS,
}

/**
 * One inline validation error attached to a field. The string ids map to
 * `R.string.knotwork_node_validation_*` so forms can render the message
 * via `stringResource` without holding a `Context` here.
 */
enum class ValidationFailure(val stringRes: Int) {
    /** Title field is blank / whitespace-only. */
    TITLE_EMPTY(app.knotwork.design.R.string.knotwork_node_validation_title_empty),

    /** Title clashes with another node in the same pipeline. */
    TITLE_DUPLICATE(app.knotwork.design.R.string.knotwork_node_validation_title_duplicate),

    /** Field is required but currently empty. */
    REQUIRED(app.knotwork.design.R.string.knotwork_node_validation_field_required),

    /** Numeric value outside the spec's range. */
    OUT_OF_RANGE(app.knotwork.design.R.string.knotwork_node_validation_out_of_range),

    /** IntentRouter has fewer than 2 or more than 6 classes. */
    INTENT_CLASS_COUNT(app.knotwork.design.R.string.knotwork_node_validation_intent_class_count),

    /** JSON-Schema body failed to parse. */
    INVALID_JSON(app.knotwork.design.R.string.knotwork_node_validation_invalid_json),

    /** QueueProcessor parallelism outside `1..8`. */
    PARALLELISM_RANGE(app.knotwork.design.R.string.knotwork_node_validation_parallelism_range),

    /**
     * IntentRouter `fallbackClass` references a name that is no longer in
     * the declared `classes` list — typically after the user renamed /
     * removed a class but forgot to update the fallback selection.
     * Surfaced inline under the fallback dropdown so the user can either
     * pick another class or clear the selection.
     */
    FALLBACK_NOT_IN_CLASSES(app.knotwork.design.R.string.knotwork_node_validation_fallback_unknown),

    /**
     * Two IntentRouter classes share the same name. Duplicate names would
     * collapse into a single canvas out-port and break unmatched-intent
     * routing, so the rule fires on `FieldId.CLASSES`.
     */
    CLASS_NAME_DUPLICATE(app.knotwork.design.R.string.knotwork_node_validation_class_duplicate),

    /**
     * Two `ToolConfig.argumentMapping` rows share the same key. With an
     * ordered `List<ToolArgument>` we preserve every keystroke, but the
     * tool's typed schema cannot accept duplicate keys, so the rule
     * fires on `FieldId.ARGUMENT_MAPPING`.
     */
    KEY_DUPLICATE(app.knotwork.design.R.string.knotwork_node_validation_arg_key_duplicate),
}

/** Allowed range for [LiteRtConfig.temperature]. */
private val TEMPERATURE_RANGE = 0f..2f

/** Allowed range for [LiteRtConfig.topP]. */
private val TOP_P_RANGE = 0f..1f

/** Allowed range for [LiteRtConfig.maxNewTokens]. */
private val MAX_NEW_TOKENS_RANGE = 32..4_096

/** Allowed range for [CloudConfig.maxTokens]. */
private val MAX_TOKENS_RANGE = 1..32_768

/** Allowed range for [CloudConfig.timeoutMs]. */
private val TIMEOUT_RANGE_MS = 1_000..600_000

/** Allowed range for [DecompositionConfig.maxSubtasks]. */
private val MAX_SUBTASKS_RANGE = 1..20

/** Allowed range for [QueueProcessorConfig.parallelism]. */
private val PARALLELISM_RANGE = 1..8

/** Allowed range for [EvaluationConfig.maxRetries]. */
private val MAX_RETRIES_RANGE = 0..5

/** Allowed range for [SummaryConfig.targetLengthChars]. */
private val TARGET_LENGTH_RANGE = 200..4_000

/** Inclusive bounds on [IntentRouterConfig.classes]. */
private val INTENT_CLASSES_RANGE = 2..6

/** Inclusive bounds on [ClarificationConfig.quickReplies]. */
private val QUICK_REPLIES_RANGE = 0..4

/** Min wait-timeout in milliseconds for [ClarificationConfig.timeoutMs]. */
private const val MIN_WAIT_TIMEOUT_MS = 0

/**
 * Pure-Kotlin validator that walks a [NodeConfig] and returns one entry
 * per offending field. An empty map means the form may enable Save.
 *
 * The implementation mirrors `node-specs.md` §Validation rules — the
 * table there is the authoritative spec. Pipeline-wide title uniqueness
 * is delegated to the caller, which supplies [peerTitles] (the set of
 * sibling node titles excluding the one currently being edited).
 *
 * Side-effect-free and Compose-free so it can be unit-tested against
 * pure Kotlin and also called from a non-Compose canvas worker.
 */
object NodeConfigValidation {

    /**
     * Validates [config] against the spec rules.
     *
     * @param config the configuration payload to check.
     * @param peerTitles the set of sibling node titles in the same
     * pipeline, excluding [config.title]. Pass an empty set when the
     * caller has not assembled the pipeline yet (e.g. catalog previews).
     * @return a map of failing fields. Save should be disabled when the
     * map is non-empty.
     */
    // 12-arm `when` mirrors 12 node types; further split would only hide structure.
    @Suppress("CyclomaticComplexMethod")
    fun validate(config: NodeConfig, peerTitles: Set<String>): Map<FieldId, ValidationFailure> {
        val errors = mutableMapOf<FieldId, ValidationFailure>()
        validateTitle(config.title, peerTitles)?.let { errors[FieldId.TITLE] = it }
        when (config) {
            is InputConfig -> errors += validateInput(config)
            is OutputConfig -> Unit // No type-specific rules beyond title.
            is LiteRtConfig -> errors += validateLiteRt(config)
            is CloudConfig -> errors += validateCloud(config)
            is IntentRouterConfig -> errors += validateIntentRouter(config)
            is IfConditionConfig -> errors += validateIfCondition(config)
            is ClarificationConfig -> errors += validateClarification(config)
            is ToolConfig -> errors += validateTool(config)
            is DecompositionConfig -> errors += validateDecomposition(config)
            is QueueProcessorConfig -> errors += validateQueueProcessor(config)
            is EvaluationConfig -> errors += validateEvaluation(config)
            is SummaryConfig -> errors += validateSummary(config)
        }
        return errors
    }

    /**
     * Stand-alone title rule. Surfaced separately so the `EditorToolbar`
     * inline-name field can validate the pipeline-level title with the
     * same algorithm.
     *
     * @param title the candidate title.
     * @param peerTitles set of sibling titles to check uniqueness against.
     * @return the failure or `null` when the title passes.
     */
    fun validateTitle(title: String, peerTitles: Set<String>): ValidationFailure? = when {
        title.isBlank() -> ValidationFailure.TITLE_EMPTY
        title in peerTitles -> ValidationFailure.TITLE_DUPLICATE
        else -> null
    }

    private fun validateInput(config: InputConfig): Map<FieldId, ValidationFailure> {
        val errors = mutableMapOf<FieldId, ValidationFailure>()
        if (config.inputName.isBlank()) errors[FieldId.INPUT_NAME] = ValidationFailure.REQUIRED
        config.schemaJson?.takeIf { it.isNotBlank() }?.let { json ->
            if (!isValidJson(json)) errors[FieldId.SCHEMA_JSON] = ValidationFailure.INVALID_JSON
        }
        return errors
    }

    private fun validateLiteRt(config: LiteRtConfig): Map<FieldId, ValidationFailure> {
        val errors = mutableMapOf<FieldId, ValidationFailure>()
        // `modelId.isBlank()` means "use the currently-active model" — a
        // valid first-class choice (Phase 22 / Task 16 follow-up F8). No
        // REQUIRED error in that case; the executor resolves the model at
        // run-time through `LoadModelUseCase`'s null fallback.
        if (config.systemPrompt.isBlank()) errors[FieldId.SYSTEM_PROMPT] = ValidationFailure.REQUIRED
        if (config.temperature !in TEMPERATURE_RANGE) errors[FieldId.TEMPERATURE] = ValidationFailure.OUT_OF_RANGE
        if (config.topP !in TOP_P_RANGE) errors[FieldId.TOP_P] = ValidationFailure.OUT_OF_RANGE
        if (config.maxNewTokens !in MAX_NEW_TOKENS_RANGE) {
            errors[FieldId.MAX_NEW_TOKENS] = ValidationFailure.OUT_OF_RANGE
        }
        return errors
    }

    private fun validateCloud(config: CloudConfig): Map<FieldId, ValidationFailure> {
        val errors = mutableMapOf<FieldId, ValidationFailure>()
        // `CloudConfig.model` was removed from the sheet in Phase 22 / Task 14
        // review round 3 (model ids live once per provider in Settings →
        // External providers, not per-node). The validator therefore no
        // longer flags a blank model — the executor falls back to the
        // provider's configured model at runtime when this field is empty.
        if (config.systemPrompt.isBlank()) errors[FieldId.SYSTEM_PROMPT] = ValidationFailure.REQUIRED
        if (config.temperature !in TEMPERATURE_RANGE) errors[FieldId.TEMPERATURE] = ValidationFailure.OUT_OF_RANGE
        if (config.maxTokens !in MAX_TOKENS_RANGE) errors[FieldId.MAX_TOKENS] = ValidationFailure.OUT_OF_RANGE
        if (config.timeoutMs !in TIMEOUT_RANGE_MS) errors[FieldId.TIMEOUT_MS] = ValidationFailure.OUT_OF_RANGE
        return errors
    }

    private fun validateIntentRouter(config: IntentRouterConfig): Map<FieldId, ValidationFailure> {
        val errors = mutableMapOf<FieldId, ValidationFailure>()
        val names = config.classes.map { it.name }
        when {
            config.classes.size !in INTENT_CLASSES_RANGE -> {
                errors[FieldId.CLASSES] = ValidationFailure.INTENT_CLASS_COUNT
            }
            names.any { it.isBlank() } -> {
                errors[FieldId.CLASSES] = ValidationFailure.REQUIRED
            }
            // Surface CLASS_NAME_DUPLICATE last so it only fires once every
            // class has a non-blank name — otherwise a row the user just
            // started typing into would race REQUIRED and CLASS_NAME_DUPLICATE.
            names.distinct().size != names.size -> {
                errors[FieldId.CLASSES] = ValidationFailure.CLASS_NAME_DUPLICATE
            }
        }
        if (config.classifierPrompt.isBlank()) errors[FieldId.CLASSIFIER_PROMPT] = ValidationFailure.REQUIRED
        val fallback = config.fallbackClass
        if (!fallback.isNullOrBlank()) {
            val declared = names.toSet()
            if (fallback !in declared) {
                errors[FieldId.FALLBACK_CLASS] = ValidationFailure.FALLBACK_NOT_IN_CLASSES
            }
        }
        return errors
    }

    private fun validateIfCondition(config: IfConditionConfig): Map<FieldId, ValidationFailure> {
        val errors = mutableMapOf<FieldId, ValidationFailure>()
        if (config.expression.isBlank()) errors[FieldId.EXPRESSION] = ValidationFailure.REQUIRED
        if (config.labelTrue.isBlank()) errors[FieldId.LABEL_TRUE] = ValidationFailure.REQUIRED
        if (config.labelFalse.isBlank()) errors[FieldId.LABEL_FALSE] = ValidationFailure.REQUIRED
        return errors
    }

    private fun validateClarification(config: ClarificationConfig): Map<FieldId, ValidationFailure> {
        val errors = mutableMapOf<FieldId, ValidationFailure>()
        if (config.questionTemplate.isBlank()) errors[FieldId.QUESTION_TEMPLATE] = ValidationFailure.REQUIRED
        if (config.quickReplies.size !in QUICK_REPLIES_RANGE) {
            errors[FieldId.QUICK_REPLIES] = ValidationFailure.OUT_OF_RANGE
        }
        config.timeoutMs?.let { ms ->
            if (ms < MIN_WAIT_TIMEOUT_MS) errors[FieldId.TIMEOUT_OPTIONAL] = ValidationFailure.OUT_OF_RANGE
        }
        return errors
    }

    private fun validateTool(config: ToolConfig): Map<FieldId, ValidationFailure> {
        val errors = mutableMapOf<FieldId, ValidationFailure>()
        if (config.toolId.isBlank()) errors[FieldId.TOOL_ID] = ValidationFailure.REQUIRED
        val names = config.argumentMapping.map { it.name }
        when {
            config.argumentMapping.any { it.name.isBlank() || it.expression.isBlank() } -> {
                errors[FieldId.ARGUMENT_MAPPING] = ValidationFailure.REQUIRED
            }
            // Same ordering rationale as IntentRouter — duplicate-key check
            // only fires once every row has a non-blank name.
            names.distinct().size != names.size -> {
                errors[FieldId.ARGUMENT_MAPPING] = ValidationFailure.KEY_DUPLICATE
            }
        }
        return errors
    }

    private fun validateDecomposition(config: DecompositionConfig): Map<FieldId, ValidationFailure> {
        val errors = mutableMapOf<FieldId, ValidationFailure>()
        if (config.planningPrompt.isBlank()) errors[FieldId.PLANNING_PROMPT] = ValidationFailure.REQUIRED
        if (config.maxSubtasks !in MAX_SUBTASKS_RANGE) errors[FieldId.MAX_SUBTASKS] = ValidationFailure.OUT_OF_RANGE
        config.outputSchemaJson?.takeIf { it.isNotBlank() }?.let { json ->
            if (!isValidJson(json)) errors[FieldId.OUTPUT_SCHEMA_JSON] = ValidationFailure.INVALID_JSON
        }
        return errors
    }

    private fun validateQueueProcessor(config: QueueProcessorConfig): Map<FieldId, ValidationFailure> {
        val errors = mutableMapOf<FieldId, ValidationFailure>()
        if (config.inputList.isBlank()) errors[FieldId.INPUT_LIST] = ValidationFailure.REQUIRED
        if (config.itemVariable.isBlank()) errors[FieldId.ITEM_VARIABLE] = ValidationFailure.REQUIRED
        if (config.parallelism !in PARALLELISM_RANGE) {
            errors[FieldId.PARALLELISM] = ValidationFailure.PARALLELISM_RANGE
        }
        return errors
    }

    private fun validateEvaluation(config: EvaluationConfig): Map<FieldId, ValidationFailure> {
        val errors = mutableMapOf<FieldId, ValidationFailure>()
        if (config.criteriaPrompt.isBlank()) errors[FieldId.CRITERIA_PROMPT] = ValidationFailure.REQUIRED
        if (config.maxRetries !in MAX_RETRIES_RANGE) errors[FieldId.MAX_RETRIES] = ValidationFailure.OUT_OF_RANGE
        return errors
    }

    private fun validateSummary(config: SummaryConfig): Map<FieldId, ValidationFailure> {
        val errors = mutableMapOf<FieldId, ValidationFailure>()
        if (config.format == SummaryFormat.CUSTOM && config.customPrompt.isNullOrBlank()) {
            errors[FieldId.CUSTOM_PROMPT] = ValidationFailure.REQUIRED
        }
        if (config.targetLengthChars !in TARGET_LENGTH_RANGE) {
            errors[FieldId.TARGET_LENGTH_CHARS] = ValidationFailure.OUT_OF_RANGE
        }
        return errors
    }

    /**
     * Returns `true` when [json] parses cleanly as a JSON object. The
     * spec only allows schemas to be objects — arrays / primitives are
     * rejected, so the parser uses [JSONObject] directly.
     *
     * Caller is expected to skip the check when the field is blank /
     * `null`; this helper presumes non-empty input.
     */
    private fun isValidJson(json: String): Boolean = try {
        JSONObject(json)
        true
    } catch (_: JSONException) {
        false
    }
}
