package app.knotwork.android.domain.usecases

import app.knotwork.android.domain.models.PipelineGraph
import app.knotwork.android.domain.repositories.PipelineRepository
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The skill-usage scan is wired now but dormant until the SKILL node ships
 * (no [PipelineGraph] node can reference a skill yet), so it must return empty
 * for every skill regardless of the saved pipelines.
 */
class FindPipelinesUsingSkillUseCaseTest {

    @Test
    fun `given saved pipelines when scanned then no dependents are found yet`() = runTest {
        val pipelineRepository: PipelineRepository = mockk {
            every { getAllPipelines() } returns flowOf(
                listOf(PipelineGraph(id = "p1", name = "One"), PipelineGraph(id = "p2", name = "Two")),
            )
        }
        val useCase = FindPipelinesUsingSkillUseCase(pipelineRepository)

        assertEquals(emptyList<PipelineGraph>(), useCase("any-skill"))
    }

    @Test
    fun `given the pure helper when called then it returns empty`() {
        val all = listOf(PipelineGraph(id = "p1", name = "One"))
        assertEquals(emptyList<PipelineGraph>(), findPipelinesUsingSkill("s", all))
    }
}
