package ai.agent.android.domain.models

/**
 * Represents a single node on the visual orchestrator canvas.
 *
 * @property id The unique identifier of the node.
 * @property type The [NodeType] describing the capability of this node.
 * @property x The X coordinate of the node on the canvas.
 * @property y The Y coordinate of the node on the canvas.
 * @property label An optional label or name for the node.
 */
data class NodeModel(
    val id: String,
    val type: NodeType,
    val x: Float,
    val y: Float,
    val label: String = type.name
)
