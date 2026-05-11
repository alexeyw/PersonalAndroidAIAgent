package ai.agent.android.data.local.models

import androidx.room.Embedded
import androidx.room.Relation

/**
 * Data class representing a pipeline with its associated nodes and connections.
 * Used by Room to query complex relationships.
 *
 * @property pipeline The root [PipelineEntity].
 * @property nodes The list of [NodeEntity]s associated with the pipeline.
 * @property connections The list of [ConnectionEntity]s associated with the pipeline.
 */
data class PipelineWithNodesAndConnections(
    @Embedded val pipeline: PipelineEntity,

    @Relation(
        parentColumn = "id",
        entityColumn = "pipelineId",
    )
    val nodes: List<NodeEntity>,

    @Relation(
        parentColumn = "id",
        entityColumn = "pipelineId",
    )
    val connections: List<ConnectionEntity>,
)
