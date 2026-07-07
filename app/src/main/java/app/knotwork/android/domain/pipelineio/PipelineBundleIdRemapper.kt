package app.knotwork.android.domain.pipelineio

import app.knotwork.android.domain.models.PipelineGraph

/**
 * Pure id-regeneration for the "import as copy" path of a pipeline bundle.
 *
 * When the user chooses to import a colliding bundle as fresh copies, every
 * pipeline in the closure must receive a new id so nothing existing is
 * overwritten — and every intra-bundle reference must be rewired to the new
 * ids or the copied composition would point back at the originals.
 *
 * The rewrite is total and internally consistent:
 * - each pipeline id is regenerated;
 * - each node id and connection id is regenerated (they are globally unique
 *   primary keys, so a copy that reused them would collide with the source);
 * - each connection's endpoints are remapped to the new node ids;
 * - each `PIPELINE` node's `targetPipelineId` that points **inside** the
 *   bundle is remapped to the target's new pipeline id (references that point
 *   outside are left untouched, though a well-formed bundle has none).
 *
 * Kept as a stateless object taking an explicit id generator so the remap is
 * deterministic under test.
 */
object PipelineBundleIdRemapper {

    /**
     * Returns a copy of [pipelines] with every pipeline / node / connection id
     * regenerated and every intra-bundle reference remapped.
     *
     * @param pipelines The closure to copy. Must be internally consistent
     *   (connection endpoints and `targetPipelineId`s that resolve within the
     *   set); this holds for any bundle that passed
     *   [PipelineBundleJsonSerializer.parse].
     * @param newId Factory for fresh ids. Called once per distinct old id
     *   being replaced. Production passes a UUID generator; tests pass a
     *   deterministic counter.
     * @return The re-identified closure, in the same order as [pipelines].
     */
    fun regenerate(pipelines: List<PipelineGraph>, newId: (String) -> String): List<PipelineGraph> {
        val pipelineIdMap = pipelines.associate { it.id to newId(it.id) }
        return pipelines.map { graph ->
            freshenGraphElements(
                graph = graph,
                newPipelineId = pipelineIdMap.getValue(graph.id),
                remapTarget = { target -> pipelineIdMap[target] ?: target },
                newId = newId,
            )
        }
    }

    /**
     * Returns a copy of [pipelines] with **pipeline ids preserved** but every
     * node and connection id regenerated (and connection endpoints remapped).
     *
     * This is the ids-are-globally-unique guarantee the persistence layer needs:
     * `NodeEntity` / `ConnectionEntity` use a **global** primary key, so two
     * pipelines in one bundle that reuse a node id (the shipped presets number
     * nodes `node-1`, `node-2`, … independently per pipeline) would otherwise
     * collide under `OnConflictStrategy.REPLACE` and silently drop rows. Pipeline
     * ids and `targetPipelineId` references are left untouched, so bindings
     * (`ChatSession.pipelineId`, default/share/tile) and the composition survive
     * — this is the REPLACE ("keep ids") counterpart to [regenerate].
     *
     * @param pipelines The closure to persist.
     * @param newId Factory for fresh node/connection ids.
     * @return The closure with globally-unique node/connection ids.
     */
    fun freshenElementIds(pipelines: List<PipelineGraph>, newId: (String) -> String): List<PipelineGraph> =
        pipelines.map { graph ->
            freshenGraphElements(
                graph = graph,
                newPipelineId = graph.id,
                remapTarget = { it },
                newId = newId,
            )
        }

    /**
     * Shared rewrite for one graph: regenerates every node and connection id,
     * remaps connection endpoints to the new node ids, sets the pipeline id to
     * [newPipelineId], and rewrites each PIPELINE `targetPipelineId` through
     * [remapTarget].
     */
    private fun freshenGraphElements(
        graph: PipelineGraph,
        newPipelineId: String,
        remapTarget: (String) -> String,
        newId: (String) -> String,
    ): PipelineGraph {
        val nodeIdMap = graph.nodes.associate { it.id to newId(it.id) }
        val remappedNodes = graph.nodes.map { node ->
            node.copy(
                id = nodeIdMap.getValue(node.id),
                targetPipelineId = node.targetPipelineId?.let(remapTarget),
            )
        }
        val remappedConnections = graph.connections.map { connection ->
            connection.copy(
                id = newId(connection.id),
                sourceNodeId = nodeIdMap.getValue(connection.sourceNodeId),
                targetNodeId = nodeIdMap.getValue(connection.targetNodeId),
            )
        }
        return graph.copy(id = newPipelineId, nodes = remappedNodes, connections = remappedConnections)
    }
}
