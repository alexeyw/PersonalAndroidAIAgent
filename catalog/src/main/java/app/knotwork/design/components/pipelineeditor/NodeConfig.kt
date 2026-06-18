package app.knotwork.design.components.pipelineeditor

/**
 * Per-node configuration payload rendered inside [NodeConfigSheet]. The
 * sheet's chrome (drag handle, type pill, sticky action row) is shared;
 * the body switches on `when (config)` to a per-type form.
 *
 * Each variant captures the *value*; the matching form
 * composable in `forms/` captures the *rendering*.
 */
sealed interface NodeConfig {
    /** Display title shown both on the [NodeCard] and in the sheet header. */
    val title: String

    /** Node type — drives the colour strip and the type pill on the sheet. */
    val type: NodeType

    /** Optional free-form note rendered under the type pill. */
    val description: String?
}

/** Output text format for [OutputConfig]. */
enum class OutputFormat { PLAIN_TEXT, MARKDOWN, JSON }

/**
 * One installed local model the user can pick in the LiteRt config sheet. Defined here
 * (catalog side) as a plain pair of id / display name plus an `isActive` flag so the
 * design module does not depend on the `:app` `LocalModel` Room entity. The screen
 * maps `app.knotwork.android.domain.models.LocalModel` to this type at the sheet boundary.
 *
 * @property id canonical model identifier — typically the model file path. This is the
 * value written back into [LiteRtConfig.modelId] when the user picks the model.
 * @property displayName human-readable name shown in the dropdown row.
 * @property isActive whether this is the model that the runtime currently loads. The
 * picker badges this row with an "active" suffix.
 */
data class LocalModelOption(val id: String, val displayName: String, val isActive: Boolean)

/**
 * Cloud LLM provider for [CloudConfig].
 *
 * [AUTO] defers the choice to runtime — the executor picks a provider from the
 * configured API keys. It maps to the domain `CloudProvider.AUTO_KEY` wire
 * sentinel (`"auto"`) rather than a concrete provider.
 */
enum class CloudProvider { OPEN_AI, ANTHROPIC, GOOGLE, COMPATIBLE, AUTO }

/** Summary rendering style for [SummaryConfig]. */
enum class SummaryFormat { BULLETS, PARAGRAPH, CUSTOM }

/** Tool-call confirmation override for [ToolConfig]. `null` = inherit declared risk. */
enum class ConfirmPolicy { ALWAYS_CONFIRM, ALLOW_READONLY, ALLOW_SENSITIVE }

/**
 * Configuration for [NodeType.INPUT] — pipeline entry contract.
 *
 * @property title display title.
 * @property description optional one-line note.
 * @property inputName variable name exposed downstream.
 * @property schemaJson optional JSON-Schema body for typed inputs.
 */
data class InputConfig(
    override val title: String,
    override val description: String? = null,
    val inputName: String = "user.message",
    val schemaJson: String? = null,
) : NodeConfig {
    override val type: NodeType get() = NodeType.INPUT
}

/**
 * Configuration for [NodeType.OUTPUT] — pipeline exit.
 *
 * @property title display title.
 * @property description optional one-line note.
 * @property format output rendering format.
 * @property systemPrompt optional system prompt sent to the model that
 *   formats the final response. When blank the node forwards the upstream
 *   text verbatim (the engine wraps a non-blank prompt around the LLM
 *   call). Supports `$DATE` / `$TIME` / `$TOOLS` / `$MODEL` /
 *   `$MEMORY_SUMMARY` variables.
 */
data class OutputConfig(
    override val title: String,
    override val description: String? = null,
    val format: OutputFormat = OutputFormat.PLAIN_TEXT,
    val systemPrompt: String = "",
) : NodeConfig {
    override val type: NodeType get() = NodeType.OUTPUT
}

/**
 * Configuration for [NodeType.LITE_RT] — on-device LiteRT inference.
 *
 * @property title display title.
 * @property description optional one-line note.
 * @property modelId installed local model identifier.
 * @property systemPrompt mono multi-line prompt; supports `$DATE` / `$TIME`
 * / `$TOOLS` / `$MODEL` / `$MEMORY_SUMMARY` variables resolved at runtime.
 * @property temperature sampling temperature, range `0.0..2.0`.
 * @property topP nucleus-sampling cumulative probability, range `0.0..1.0`.
 * @property maxNewTokens token-generation cap, range `32..4096`.
 * @property stopTokens optional list of stop sequences.
 */
data class LiteRtConfig(
    override val title: String,
    override val description: String? = null,
    val modelId: String = "",
    val systemPrompt: String = "",
    val temperature: Float = 0.7f,
    val topP: Float = 0.9f,
    val maxNewTokens: Int = 512,
    val stopTokens: List<String> = emptyList(),
) : NodeConfig {
    override val type: NodeType get() = NodeType.LITE_RT
}

/**
 * Configuration for [NodeType.CLOUD] — external LLM API.
 *
 * @property title display title.
 * @property description optional one-line note.
 * @property provider cloud LLM provider.
 * @property model free-text model id; dropdown suggests known ids per provider.
 * @property systemPrompt mono multi-line prompt with the same variable
 * chips as [LiteRtConfig].
 * @property temperature sampling temperature, range `0.0..2.0`.
 * @property maxTokens hard cap on the response length.
 * @property timeoutMs request timeout in milliseconds.
 */
data class CloudConfig(
    override val title: String,
    override val description: String? = null,
    val provider: CloudProvider = CloudProvider.OPEN_AI,
    val model: String = "",
    val systemPrompt: String = "",
    val temperature: Float = 0.7f,
    val maxTokens: Int = 1024,
    val timeoutMs: Int = 30_000,
) : NodeConfig {
    override val type: NodeType get() = NodeType.CLOUD
}

/**
 * Per-class declaration for [IntentRouterConfig.classes].
 *
 * @property name machine-readable class id rendered as the out-port label.
 * @property description human-readable description used by the classifier prompt.
 * @property examples optional list of training-side example strings.
 */
data class IntentClass(val name: String, val description: String = "", val examples: List<String> = emptyList())

/**
 * Configuration for [NodeType.INTENT_ROUTER] — classifier with one out-port
 * per declared class.
 *
 * @property title display title.
 * @property description optional one-line note.
 * @property classes 2..6 declared classes (validated at save).
 * @property classifierPrompt mono multi-line prompt consumed by the local LLM.
 * @property fallbackClass class name to route to when no match is found;
 * `null` = no fallback (the engine raises an error).
 * @property engineProvider optional cloud provider backing this node's
 * structured inference; `null` runs on-device (the default).
 */
data class IntentRouterConfig(
    override val title: String,
    override val description: String? = null,
    val classes: List<IntentClass> = emptyList(),
    val classifierPrompt: String = "",
    val fallbackClass: String? = null,
    val engineProvider: CloudProvider? = null,
) : NodeConfig {
    override val type: NodeType get() = NodeType.INTENT_ROUTER
}

/**
 * Configuration for [NodeType.IF_CONDITION] — boolean branch.
 *
 * @property title display title.
 * @property description optional one-line note.
 * @property expression mono boolean expression evaluated against upstream
 * node outputs.
 * @property labelTrue port label for the True branch (also the [EdgeLabel]).
 * @property labelFalse port label for the False branch.
 * @property engineProvider optional cloud provider backing this node's
 * structured inference; `null` runs on-device (the default).
 */
data class IfConditionConfig(
    override val title: String,
    override val description: String? = null,
    val expression: String = "",
    val labelTrue: String = "True",
    val labelFalse: String = "False",
    val engineProvider: CloudProvider? = null,
) : NodeConfig {
    override val type: NodeType get() = NodeType.IF_CONDITION
}

/**
 * Configuration for [NodeType.CLARIFICATION] — mid-pipeline question to
 * the user.
 *
 * @property title display title.
 * @property description optional one-line note.
 * @property questionTemplate mono multi-line template; supports `$INPUT`
 * and upstream variables.
 * @property quickReplies 0..4 quick-reply chips surfaced under the question.
 * @property timeoutMs optional wait timeout; `null` waits indefinitely.
 */
data class ClarificationConfig(
    override val title: String,
    override val description: String? = null,
    val questionTemplate: String = "",
    val quickReplies: List<String> = emptyList(),
    val timeoutMs: Int? = null,
) : NodeConfig {
    override val type: NodeType get() = NodeType.CLARIFICATION
}

/**
 * One row of [ToolConfig.argumentMapping]. Modelled as an ordered list
 * (not a `Map`) so the editor can preserve every row even while the
 * user is mid-rename and two rows temporarily share the same key — a
 * `Map<String, String>` would silently drop the older entry on
 * deduplication. Validation catches the collision via
 * `ValidationFailure.KEY_DUPLICATE` and disables Save until resolved.
 *
 * @property name argument key emitted to the tool's typed schema.
 * @property expression upstream-node expression that resolves to the
 * value passed to the tool.
 */
data class ToolArgument(val name: String, val expression: String)

/**
 * Configuration for [NodeType.TOOL] — AppFunctions / MCP tool invocation.
 *
 * @property title display title.
 * @property description optional one-line note.
 * @property toolId fully-qualified tool identifier (e.g. `fs.write_file`).
 * @property argumentMapping ordered list of `(name, expression)` rows
 * mapped to the tool's typed argument schema. Required arguments not
 * declared here surface as validation errors.
 * @property confirmOverride optional override of the tool's declared risk
 * policy; `null` inherits the tool's declared risk.
 * @property engineProvider optional cloud provider backing this node's
 * structured tool-selection / argument inference; `null` runs on-device.
 */
data class ToolConfig(
    override val title: String,
    override val description: String? = null,
    val toolId: String = "",
    val argumentMapping: List<ToolArgument> = emptyList(),
    val confirmOverride: ConfirmPolicy? = null,
    val engineProvider: CloudProvider? = null,
) : NodeConfig {
    override val type: NodeType get() = NodeType.TOOL
}

/**
 * Configuration for [NodeType.DECOMPOSITION] — breaks a task into a list
 * of subtasks.
 *
 * @property title display title.
 * @property description optional one-line note.
 * @property planningPrompt mono multi-line planning prompt; supports `$INPUT`.
 * @property maxSubtasks generation cap, range `1..20`.
 * @property outputSchemaJson optional structured-output JSON schema.
 * @property engineProvider optional cloud provider backing this node's
 * structured inference; `null` runs on-device (the default).
 */
data class DecompositionConfig(
    override val title: String,
    override val description: String? = null,
    val planningPrompt: String = "",
    val maxSubtasks: Int = 5,
    val outputSchemaJson: String? = null,
    val engineProvider: CloudProvider? = null,
) : NodeConfig {
    override val type: NodeType get() = NodeType.DECOMPOSITION
}

/**
 * Configuration for [NodeType.QUEUE_PROCESSOR] — iterates a list.
 *
 * @property title display title.
 * @property description optional one-line note.
 * @property inputList upstream expression that resolves to a list.
 * @property itemVariable variable name exposed inside the loop body.
 * @property parallelism concurrent workers, range `1..8`.
 * @property stopOnError when `true`, the first failure short-circuits the loop.
 */
data class QueueProcessorConfig(
    override val title: String,
    override val description: String? = null,
    val inputList: String = "",
    val itemVariable: String = "item",
    val parallelism: Int = 1,
    val stopOnError: Boolean = true,
) : NodeConfig {
    override val type: NodeType get() = NodeType.QUEUE_PROCESSOR
}

/**
 * Configuration for [NodeType.EVALUATION] — judges a step result.
 *
 * @property title display title.
 * @property description optional one-line note.
 * @property criteriaPrompt mono multi-line evaluation prompt; supports
 * `$INPUT` and `$ATTEMPT`.
 * @property maxRetries retry budget, range `0..5`. Surfaces the `Retry`
 * out-port only when greater than zero.
 * @property engineProvider optional cloud provider backing this node's
 * structured inference; `null` runs on-device (the default).
 */
data class EvaluationConfig(
    override val title: String,
    override val description: String? = null,
    val criteriaPrompt: String = "",
    val maxRetries: Int = 2,
    val engineProvider: CloudProvider? = null,
) : NodeConfig {
    override val type: NodeType get() = NodeType.EVALUATION
}

/**
 * Configuration for [NodeType.SUMMARY] — condenses many node outputs.
 *
 * @property title display title.
 * @property description optional one-line note.
 * @property format summary rendering style.
 * @property customPrompt mono multi-line prompt; required when
 * [format] == [SummaryFormat.CUSTOM] and ignored otherwise.
 * @property targetLengthChars target character count, range `200..4000`.
 */
data class SummaryConfig(
    override val title: String,
    override val description: String? = null,
    val format: SummaryFormat = SummaryFormat.BULLETS,
    val customPrompt: String? = null,
    val targetLengthChars: Int = 600,
) : NodeConfig {
    override val type: NodeType get() = NodeType.SUMMARY
}

/**
 * Why a candidate pipeline cannot be picked as a [PipelineConfig.targetPipelineId].
 * Each reason maps to a distinct inline message because the user's remedy differs:
 *
 *  - [SELF] — pick a different pipeline.
 *  - [CYCLE] — break the back-reference (the offending pipeline is named via
 *    [PipelineTargetOption.cycleCulprit]).
 *  - [DEPTH] — reduce the nesting depth (the ceiling is carried in
 *    [PipelineTargetOption.depthLimit]).
 */
enum class PipelineTargetDisabledReason { SELF, CYCLE, DEPTH }

/**
 * One row in the [PipelineConfig] target picker. The app resolves the full
 * catalogue of saved pipelines, classifies each against the pipeline being
 * edited (reusing the domain composition validator), and hands the result
 * down as a list of these options so the catalog stays free of any
 * cross-pipeline reachability logic.
 *
 * @property id the candidate pipeline's stable id, written into
 * [PipelineConfig.targetPipelineId] when picked.
 * @property name the candidate pipeline's display name shown in the row.
 * @property selectable `true` when the option may be chosen; `false` renders
 * the row disabled with [disabledReason].
 * @property disabledReason why the option is disabled; `null` when [selectable].
 * @property cycleCulprit for [PipelineTargetDisabledReason.CYCLE], the display
 * name of the pipeline that already calls the edited one (so the reason can
 * name it). `null` for the other reasons.
 * @property depthLimit for [PipelineTargetDisabledReason.DEPTH], the configured
 * maximum nesting depth. `null` for the other reasons.
 */
data class PipelineTargetOption(
    val id: String,
    val name: String,
    val selectable: Boolean = true,
    val disabledReason: PipelineTargetDisabledReason? = null,
    val cycleCulprit: String? = null,
    val depthLimit: Int? = null,
)

/**
 * Configuration for [NodeType.PIPELINE] — runs another saved pipeline as a
 * nested sub-call (composition).
 *
 * @property title display title.
 * @property description optional one-line note.
 * @property targetPipelineId id of the pipeline this node runs. Blank means
 * "no target chosen yet" — the validator surfaces [ValidationFailure.TARGET_PIPELINE_MISSING]
 * and Save stays disabled until the user picks one.
 * @property targetPipelineName resolved display name of [targetPipelineId],
 * supplied by the app so the picker can show the current selection without the
 * catalog resolving ids. `null` when no target is set or the id no longer
 * resolves to a stored pipeline.
 */
data class PipelineConfig(
    override val title: String,
    override val description: String? = null,
    val targetPipelineId: String = "",
    val targetPipelineName: String? = null,
) : NodeConfig {
    override val type: NodeType get() = NodeType.PIPELINE
}

/** Inference engine a [SkillConfig] node runs the skill on. */
enum class SkillEngine { LITE_RT, CLOUD }

/**
 * One row in the [SkillConfig] skill picker. The app resolves the full skill
 * library (bundled + user) and hands the rows down so the catalog stays free
 * of any skill-storage knowledge.
 *
 * @property id the skill's stable id, written into [SkillConfig.skillId] when picked.
 * @property name the skill's display name shown in the row.
 * @property instructionPreview the skill's instruction text, copied into
 * [SkillConfig.instructionPreview] on pick so the read-only preview updates
 * immediately without a round-trip to the app.
 * @property toolRestrictionSummary one-line allowlist summary (e.g. "All tools",
 * "No tools", "3 tools"), copied into [SkillConfig.toolRestrictionSummary] on pick.
 */
data class SkillOption(
    val id: String,
    val name: String,
    val instructionPreview: String,
    val toolRestrictionSummary: String,
)

/**
 * Configuration for [NodeType.SKILL] — runs a reusable skill (fixed
 * instruction + visible-tool allowlist + default context) as an inference step.
 *
 * The skill supplies the instruction, the allowlist, and the default context;
 * the node only chooses *which* skill and *which engine* to run it on. The
 * instruction preview and allowlist summary are read-only — they are edited in
 * the Skill library, not here.
 *
 * @property title display title.
 * @property description optional one-line note.
 * @property skillId id of the skill this node runs. Blank means "no skill chosen
 * yet" — the validator surfaces [ValidationFailure.TARGET_SKILL_MISSING] and Save
 * stays disabled until the user picks one.
 * @property skillName resolved display name of [skillId], supplied by the app so
 * the picker can show the current selection. `null` when no skill is set or the id
 * no longer resolves to a stored skill.
 * @property instructionPreview resolved instruction text of the selected skill,
 * shown read-only. `null` when no skill is selected.
 * @property toolRestrictionSummary resolved one-line summary of the selected
 * skill's tool allowlist (e.g. "All tools", "No tools", "3 tools"), shown as the
 * allowlist indicator. `null` when no skill is selected.
 * @property engine which inference engine runs the skill ([SkillEngine.LITE_RT]
 * local or [SkillEngine.CLOUD]).
 */
data class SkillConfig(
    override val title: String,
    override val description: String? = null,
    val skillId: String = "",
    val skillName: String? = null,
    val instructionPreview: String? = null,
    val toolRestrictionSummary: String? = null,
    val engine: SkillEngine = SkillEngine.LITE_RT,
) : NodeConfig {
    override val type: NodeType get() = NodeType.SKILL
}
