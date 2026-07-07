package app.knotwork.android.domain.pipelineio

import app.knotwork.android.domain.models.ConnectionModel
import app.knotwork.android.domain.models.NodeModel
import app.knotwork.android.domain.models.NodeType
import app.knotwork.android.domain.models.PipelineGraph

/**
 * Shared builders for pipeline-bundle tests: a structurally valid linear graph
 * `INPUT → [PIPELINE(target)…] → OUTPUT` whose PIPELINE nodes name arbitrary
 * targets, so referential-integrity, closure-walk and remap behaviour can be
 * exercised without hand-writing JSON in every test.
 */
internal object PipelineBundleTestFixtures {

    /**
     * Builds a valid linear graph with one PIPELINE node per entry in
     * [targets], chained between a single INPUT and OUTPUT.
     *
     * @param id The pipeline id (also the stem of every node/connection id).
     * @param targets The `targetPipelineId`s referenced, in order.
     */
    fun linearGraph(id: String, targets: List<String> = emptyList()): PipelineGraph {
        val nodes = mutableListOf(NodeModel(id = "${id}_in", type = NodeType.INPUT, x = 0f, y = 0f))
        val connections = mutableListOf<ConnectionModel>()
        var previous = "${id}_in"
        targets.forEachIndexed { index, target ->
            val pipelineNodeId = "${id}_p$index"
            nodes.add(
                NodeModel(id = pipelineNodeId, type = NodeType.PIPELINE, x = 0f, y = 0f, targetPipelineId = target),
            )
            connections.add(
                ConnectionModel(id = "${id}_cin$index", sourceNodeId = previous, targetNodeId = pipelineNodeId),
            )
            previous = pipelineNodeId
        }
        val outId = "${id}_out"
        nodes.add(NodeModel(id = outId, type = NodeType.OUTPUT, x = 0f, y = 0f))
        connections.add(ConnectionModel(id = "${id}_cout", sourceNodeId = previous, targetNodeId = outId))
        return PipelineGraph(id = id, name = "name-$id", nodes = nodes, connections = connections, updatedAt = 0L)
    }
}
