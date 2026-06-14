package app.knotwork.android.domain.services

import app.knotwork.android.domain.models.NodeType
import app.knotwork.android.domain.models.PipelineGraph
import app.knotwork.android.domain.models.PipelineValidationError
import app.knotwork.android.domain.repositories.PipelineRepository
import app.knotwork.android.domain.repositories.SettingsRepository
import kotlinx.coroutines.flow.first
import javax.inject.Inject

/**
 * Static, cross-pipeline validator for
 * [NodeType.PIPELINE][app.knotwork.android.domain.models.NodeType.PIPELINE]
 * composition. Where [PipelineGraph.validate] performs the structural,
 * single-graph checks (including a PIPELINE node that names no target), this
 * validator walks the *call graph* — following every PIPELINE node's
 * [app.knotwork.android.domain.models.NodeModel.targetPipelineId] transitively
 * through the [PipelineRepository] — and rejects the three composition hazards
 * that can only be decided with knowledge of the other pipelines:
 *
 * - [PipelineValidationError.TargetPipelineNotFound] — a referenced pipeline id
 *   does not resolve (deleted or never existed).
 * - [PipelineValidationError.PipelineCycle] — a chain of references returns to a
 *   pipeline already on the path (including a self-reference); such a
 *   composition can never terminate.
 * - [PipelineValidationError.PipelineNestingTooDeep] — a reference chain would
 *   run deeper than [SettingsRepository.pipelineMaxNestingDepth], mirroring the
 *   runtime ceiling enforced by `PipelineNodeExecutor`.
 *
 * Because the call graph is known ahead of time, this is the authoritative
 * defence: a composition rejected here can never start a run. The runtime depth
 * check in the executor is only the safety net for the window between
 * validation and execution.
 *
 * **Root resolution.** The [graph][validate] passed in is the in-memory graph
 * being saved (which may not yet be persisted, or may differ from the persisted
 * copy). Its own id therefore resolves to the supplied instance; every
 * descendant is resolved from the repository. This means closing a cycle by
 * editing the root is detected against the persisted descendants.
 */
class PipelineCompositionValidator @Inject constructor(
    private val pipelineRepository: PipelineRepository,
    private val settingsRepository: SettingsRepository,
) {
    /**
     * Validates the composition rooted at [graph].
     *
     * @param graph The in-memory pipeline graph being saved.
     * @return The composition errors found; empty when the composition is
     *   sound (and trivially empty when [graph] has no PIPELINE nodes).
     */
    suspend fun validate(graph: PipelineGraph): List<PipelineValidationError> {
        // Cheap short-circuit: nothing to compose if there are no PIPELINE nodes.
        if (graph.nodes.none { it.type == NodeType.PIPELINE }) return emptyList()

        val maxDepth = settingsRepository.pipelineMaxNestingDepth.first()
        val errors = LinkedHashSet<PipelineValidationError>()
        // The root resolves to the in-memory instance; descendants come from the repo.
        val resolved = mutableMapOf(graph.id to graph)

        suspend fun resolve(pipelineId: String): PipelineGraph? =
            resolved[pipelineId] ?: pipelineRepository.getPipelineById(pipelineId)?.also { resolved[pipelineId] = it }

        // Depth-first walk over the call graph. `path` holds the pipeline ids on
        // the current branch (root first); `depth` is the number of PIPELINE
        // hops from the root, matching the runtime depth semantics.
        suspend fun visit(current: PipelineGraph, path: List<String>, depth: Int) {
            current.nodes
                .filter { it.type == NodeType.PIPELINE }
                .forEach { node ->
                    val targetId = node.targetPipelineId
                    // Blank targets are a structural single-graph error reported
                    // by PipelineGraph.validate(); not this validator's concern.
                    if (targetId.isNullOrBlank()) return@forEach

                    if (targetId in path) {
                        val cycleStart = path.indexOf(targetId)
                        errors.add(
                            PipelineValidationError.PipelineCycle(path.subList(cycleStart, path.size) + targetId),
                        )
                        return@forEach
                    }

                    val targetGraph = resolve(targetId)
                    if (targetGraph == null) {
                        errors.add(PipelineValidationError.TargetPipelineNotFound(node.id, targetId))
                        return@forEach
                    }

                    val childDepth = depth + 1
                    if (childDepth > maxDepth) {
                        errors.add(PipelineValidationError.PipelineNestingTooDeep(path + targetId, maxDepth))
                        return@forEach
                    }

                    visit(targetGraph, path + targetId, childDepth)
                }
        }

        visit(graph, listOf(graph.id), depth = 0)
        return errors.toList()
    }
}
