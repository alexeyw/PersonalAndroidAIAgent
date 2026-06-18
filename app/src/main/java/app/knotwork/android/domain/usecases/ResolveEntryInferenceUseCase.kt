package app.knotwork.android.domain.usecases

import app.knotwork.android.domain.models.NodeType
import app.knotwork.android.domain.models.PipelineGraph
import app.knotwork.android.domain.repositories.PipelineRepository
import app.knotwork.android.domain.repositories.SettingsRepository
import kotlinx.coroutines.flow.firstOrNull
import javax.inject.Inject

/**
 * Where a pipeline run's inference begins — the node the `INPUT` node hands the
 * user's message to first. Backs the multimodal send-time pre-flight guard,
 * which must decide whether an image attachment can travel into the run before
 * it is enqueued.
 */
enum class EntryInferenceKind {
    /**
     * The first node after `INPUT` runs on-device (a `LITE_RT` node or any
     * other local node). An image attachment may flow into the run, **provided**
     * the active model is vision-capable (checked separately by the caller).
     */
    LOCAL,

    /**
     * The first node after `INPUT` is a `CLOUD` node. Attachments are never sent
     * off-device, so an image message must be blocked before the run starts.
     */
    CLOUD,

    /**
     * No inference entry could be resolved — there is no usable pipeline, no
     * `INPUT` node, or `INPUT` has no successor. The pre-flight treats this as
     * "nothing to block": a genuinely broken/empty pipeline fails through the
     * normal run-start error path rather than the attachment guard.
     */
    NONE,
}

/**
 * Resolves the [EntryInferenceKind] of the pipeline a chat session would run, so
 * the send path can pre-flight an image attachment.
 *
 * Pipeline resolution mirrors the orchestrator's enqueue-time chain
 * (`TaskQueueManagerImpl`): the session's bound pipeline id first, then the
 * application-wide [SettingsRepository.defaultPipelineId]. The entry node is the
 * single target of the `INPUT` node's outgoing connection; only its type is
 * classified — branch-dependent inference deeper in the graph is intentionally
 * out of scope, because the active branch is unknowable before the run executes.
 * The conservative cases the task cares about (a `CLOUD`-first pipeline, and any
 * local-first pipeline gated by the model's vision flag) are both covered.
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
     *   node, [EntryInferenceKind.LOCAL] when it would start on-device, or
     *   [EntryInferenceKind.NONE] when no inference entry could be resolved.
     */
    suspend operator fun invoke(sessionPipelineId: String?): EntryInferenceKind {
        val graph = resolveGraph(sessionPipelineId) ?: return EntryInferenceKind.NONE
        val inputNode = graph.nodes.firstOrNull { it.type == NodeType.INPUT } ?: return EntryInferenceKind.NONE
        val entryNodeId = graph.connections
            .firstOrNull { it.sourceNodeId == inputNode.id }
            ?.targetNodeId
            ?: return EntryInferenceKind.NONE
        val entryNode = graph.nodes.firstOrNull { it.id == entryNodeId } ?: return EntryInferenceKind.NONE
        return when (entryNode.type) {
            NodeType.CLOUD -> EntryInferenceKind.CLOUD
            else -> EntryInferenceKind.LOCAL
        }
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
