package app.knotwork.android.domain.usecases

import app.knotwork.android.domain.models.NodeModel
import app.knotwork.android.domain.models.NodeType
import app.knotwork.android.domain.models.PipelineGraph
import app.knotwork.android.domain.repositories.PipelineRepository
import app.knotwork.android.domain.repositories.SettingsRepository
import kotlinx.coroutines.flow.firstOrNull
import javax.inject.Inject

/**
 * How a pipeline run would treat an image attachment, decided before the run is
 * enqueued. Backs the multimodal send-time pre-flight guard.
 *
 * The classification mirrors what
 * [app.knotwork.android.domain.engine.GraphExecutionEngine] actually does at
 * runtime: it delivers the image to the first `LITE_RT` node whose context
 * includes the original task. So the pre-flight does not merely look at the
 * `INPUT` node's immediate successor — it also verifies such a delivery target
 * exists at all, otherwise an image send would pass the guard and then be
 * silently dropped mid-run.
 */
enum class EntryInferenceKind {
    /**
     * The run starts on-device **and** the graph contains a reachable vision
     * sink (a `LITE_RT` node with `originalTask` context). An image attachment
     * can flow into the run, provided the active model is vision-capable (the
     * caller checks that separately).
     */
    LOCAL,

    /**
     * The run starts unconditionally on a `CLOUD` node. Attachments never leave
     * the device, so an image message must be blocked before the run starts.
     */
    CLOUD,

    /**
     * No vision delivery target could be resolved — there is no usable pipeline,
     * no `INPUT` node, `INPUT` has no successor, or no reachable `LITE_RT`
     * node carries the original task. An image sent here would never reach any
     * node, so the caller blocks the send with an explanation rather than
     * letting the picture be silently ignored.
     */
    NONE,
}

/**
 * Resolves the [EntryInferenceKind] of the pipeline a chat session would run, so
 * the send path can pre-flight an image attachment.
 *
 * Pipeline resolution mirrors the orchestrator's enqueue-time chain
 * (`TaskQueueManagerImpl`): the session's bound pipeline id first, then the
 * application-wide [SettingsRepository.defaultPipelineId]. Classification:
 *
 * - the `INPUT` node's immediate successor is a `CLOUD` node ⇒ [EntryInferenceKind.CLOUD]
 *   (the run unconditionally begins off-device);
 * - otherwise, if a vision sink — a `LITE_RT` node with `contextConfig.originalTask`
 *   — is reachable from `INPUT`, ⇒ [EntryInferenceKind.LOCAL] (an image can be delivered);
 * - otherwise ⇒ [EntryInferenceKind.NONE] (nothing on-device can read the image).
 *
 * Branch-dependent routing is still not fully knowable before the run executes:
 * a `LOCAL` result confirms the pipeline *can* show the image to the model, not
 * that every branch will. The engine surfaces the residual case (a run that
 * never delivered its image) on the console.
 */
class ResolveEntryInferenceUseCase @Inject constructor(
    private val pipelineRepository: PipelineRepository,
    private val settingsRepository: SettingsRepository,
) {

    /**
     * Classifies the inference entry of the pipeline bound to a session.
     *
     * @param sessionPipelineId The chat session's bound pipeline id, or `null`
     *   to fall back to the application-wide default.
     * @return [EntryInferenceKind.CLOUD] when the run would start on a `CLOUD`
     *   node, [EntryInferenceKind.LOCAL] when an on-device vision sink is
     *   reachable, or [EntryInferenceKind.NONE] when no delivery target resolves.
     */
    suspend operator fun invoke(sessionPipelineId: String?): EntryInferenceKind {
        val graph = resolveGraph(sessionPipelineId) ?: return EntryInferenceKind.NONE
        val inputNode = graph.nodes.firstOrNull { it.type == NodeType.INPUT } ?: return EntryInferenceKind.NONE
        val entryNode = entrySuccessor(graph, inputNode) ?: return EntryInferenceKind.NONE
        return when {
            entryNode.type == NodeType.CLOUD -> EntryInferenceKind.CLOUD
            hasReachableVisionSink(graph, inputNode) -> EntryInferenceKind.LOCAL
            else -> EntryInferenceKind.NONE
        }
    }

    /**
     * Returns the single node the `INPUT` node connects to (the run's entry
     * node), or `null` when `INPUT` has no outgoing edge or it points nowhere.
     */
    private fun entrySuccessor(graph: PipelineGraph, inputNode: NodeModel): NodeModel? {
        val entryNodeId = graph.connections.firstOrNull { it.sourceNodeId == inputNode.id }?.targetNodeId ?: return null
        return graph.nodes.firstOrNull { it.id == entryNodeId }
    }

    /**
     * Returns `true` when at least one **vision sink** — a `LITE_RT` node whose
     * `contextConfig.originalTask` is enabled, i.e. exactly the node the engine
     * would hand the image to — is reachable from [inputNode] over the graph's
     * connections. A breadth-first walk over the validated DAG; cheap and bounded
     * by the node count.
     */
    private fun hasReachableVisionSink(graph: PipelineGraph, inputNode: NodeModel): Boolean {
        val nodesById = graph.nodes.associateBy { it.id }
        val successors = graph.connections.groupBy({ it.sourceNodeId }, { it.targetNodeId })
        val visited = mutableSetOf(inputNode.id)
        val frontier = ArrayDeque(successors[inputNode.id].orEmpty())
        while (frontier.isNotEmpty()) {
            val nodeId = frontier.removeFirst()
            if (!visited.add(nodeId)) continue
            val node = nodesById[nodeId] ?: continue
            if (node.type == NodeType.LITE_RT && node.contextConfig.originalTask) return true
            frontier.addAll(successors[nodeId].orEmpty())
        }
        return false
    }

    /**
     * Resolves the pipeline graph using the same precedence the orchestrator
     * applies at enqueue time: the bound id when it resolves, otherwise the
     * configured default. Returns `null` when neither resolves.
     */
    private suspend fun resolveGraph(sessionPipelineId: String?): PipelineGraph? {
        sessionPipelineId?.let { id -> pipelineRepository.getPipelineById(id)?.let { return it } }
        val defaultId = settingsRepository.defaultPipelineId.firstOrNull() ?: return null
        return pipelineRepository.getPipelineById(defaultId)
    }
}
