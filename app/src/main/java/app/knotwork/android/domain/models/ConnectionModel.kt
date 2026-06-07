package app.knotwork.android.domain.models

/**
 * Represents a directed connection between two nodes in the visual orchestrator pipeline.
 *
 * @property id The unique identifier of the connection.
 * @property sourceNodeId The ID of the node where this connection starts.
 * @property targetNodeId The ID of the node where this connection ends.
 * @property label Optional label for the connection (e.g. "True" or "False").
 */
data class ConnectionModel(
    val id: String,
    val sourceNodeId: String,
    val targetNodeId: String,
    val label: String? = null,
)
