package app.knotwork.android.domain.usecases

import app.knotwork.android.domain.models.PipelineGraph
import app.knotwork.android.domain.repositories.PipelineRepository
import kotlinx.coroutines.flow.first
import javax.inject.Inject

/**
 * Finds the saved pipelines that reference a given skill through a SKILL node
 * — the dependents that would be left with a dangling reference if the skill
 * were deleted. The Skill Library uses this to warn before a destructive
 * delete (mirroring `findDependentPipelines` for PIPELINE-node targets).
 *
 * **Dormant until the SKILL node ships (5/7).** A pipeline can only reference a
 * skill once `NodeType.SKILL` and `NodeModel.skillId` exist; until then no node
 * carries a skill reference, so the scan returns empty for every skill. The
 * delete flow is wired through this use case now so that 5/7 only has to fill
 * in the single reference check in [findPipelinesUsingSkill] — the UI's
 * 0/1/N-dependents dialog (already designed) needs no further change.
 */
class FindPipelinesUsingSkillUseCase @Inject constructor(private val pipelineRepository: PipelineRepository) {
    /**
     * Returns the pipelines that run the skill with [skillId] through a SKILL
     * node, in the order they appear in the repository.
     *
     * @param skillId The id of the skill about to be deleted.
     * @return The dependent pipelines (empty until the SKILL node ships).
     */
    suspend operator fun invoke(skillId: String): List<PipelineGraph> =
        findPipelinesUsingSkill(skillId, pipelineRepository.getAllPipelines().first())
}

/**
 * Pure helper: the pipelines in [all] that reference [skillId] through a SKILL
 * node.
 *
 * Returns empty today because no [PipelineGraph] node can carry a skill
 * reference yet — `NodeModel.skillId` ships with `NodeType.SKILL` in the
 * execution task (5/7). When that field exists, this becomes:
 * `all.filter { p -> p.nodes.any { it.type == NodeType.SKILL && it.skillId == skillId } }`.
 *
 * @param skillId the skill about to be deleted.
 * @param all every saved pipeline.
 * @return the dependent pipelines (currently always empty).
 */
@Suppress("UNUSED_PARAMETER")
fun findPipelinesUsingSkill(skillId: String, all: List<PipelineGraph>): List<PipelineGraph> = emptyList()
