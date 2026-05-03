package ai.agent.android.domain.models

/**
 * Represents a single node on the visual orchestrator canvas.
 *
 * @property id The unique identifier of the node.
 * @property type The [NodeType] describing the capability of this node.
 * @property x The X coordinate of the node on the canvas.
 * @property y The Y coordinate of the node on the canvas.
 * @property label An optional label or name for the node.
 * @property toolName An optional name of the assigned tool if the node type is [NodeType.TOOL].
 * @property modelPath An optional path to a specific model file (.tflite) for this node.
 * @property conditionComplexity Threshold for task complexity if type is [NodeType.IF_CONDITION].
 * @property conditionKeywords Comma-separated keywords for condition if type is [NodeType.IF_CONDITION].
 * @property conditionPrompt Free-form prompt for condition classification if type is [NodeType.IF_CONDITION].
 * @property systemPrompt An optional system prompt to configure the behavior of the node.
 * @property cloudProvider An optional provider for a CLOUD node ("auto", "openai", "anthropic", "google", "deepseek").
 * @property clarificationTimeoutMs Timeout (in ms) the [NodeType.CLARIFICATION] node waits for the
 * user's reply before falling back to a default answer. `null` means use the engine's default
 * (60 000 ms). Ignored for non-CLARIFICATION nodes.
 */
data class NodeModel(
    val id: String,
    val type: NodeType,
    val x: Float,
    val y: Float,
    val label: String = type.name,
    val toolName: String? = null,
    val modelPath: String? = null,
    val conditionComplexity: Int? = null,
    val conditionKeywords: String? = null,
    val conditionPrompt: String? = null,
    val systemPrompt: String? = ai.agent.android.domain.constants.DefaultPrompts.getDefaultPromptForNodeType(type),
    val cloudProvider: String? = null,
    val clarificationTimeoutMs: Long? = null,
)
