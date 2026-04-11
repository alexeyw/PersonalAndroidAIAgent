package ai.agent.android.domain.engine

import org.junit.Assert.assertEquals
import org.junit.Test

class DefaultPipelineFactoryTest {

    @Test
    fun `create returns pipeline with expected name and structure`() {
        val pipelineName = "Test Preset"
        val pipeline = DefaultPipelineFactory.create(pipelineName)

        assertEquals(pipelineName, pipeline.name)
        assertEquals(10, pipeline.nodes.size)
        assertEquals(12, pipeline.connections.size)
    }
}
