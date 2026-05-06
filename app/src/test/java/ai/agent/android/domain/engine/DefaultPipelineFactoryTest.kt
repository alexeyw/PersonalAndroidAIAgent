package ai.agent.android.domain.engine

import ai.agent.android.domain.models.NodeContextConfig
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

    @Test
    fun `create assigns recommended contextConfig defaults to every node`() {
        val pipeline = DefaultPipelineFactory.create()

        pipeline.nodes.forEach { node ->
            assertEquals(
                "Node ${node.label} (${node.type}) must use defaultForType()",
                NodeContextConfig.defaultForType(node.type),
                node.contextConfig,
            )
        }
    }
}
