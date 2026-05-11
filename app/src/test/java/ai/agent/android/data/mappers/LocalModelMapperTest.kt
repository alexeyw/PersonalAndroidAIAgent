package ai.agent.android.data.mappers

import ai.agent.android.data.local.models.LocalModelEntity
import ai.agent.android.domain.models.LocalModel
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Unit tests for [LocalModelEntity] and [LocalModel] mapping extensions.
 */
class LocalModelMapperTest {

    @Test
    fun `toDomain maps entity to domain model correctly`() {
        val entity = LocalModelEntity(
            id = 1L,
            name = "Test Model",
            path = "/data/local/tmp/model.tflite",
            size = 1024L,
            isActive = true,
        )

        val domain = entity.toDomain()

        assertEquals(1L, domain.id)
        assertEquals("Test Model", domain.name)
        assertEquals("/data/local/tmp/model.tflite", domain.path)
        assertEquals(1024L, domain.size)
        assertEquals(true, domain.isActive)
    }

    @Test
    fun `toEntity maps domain model to entity correctly`() {
        val domain = LocalModel(
            id = 2L,
            name = "Another Model",
            path = "/path/to/another/model.bin",
            size = 2048L,
            isActive = false,
        )

        val entity = domain.toEntity()

        assertEquals(2L, entity.id)
        assertEquals("Another Model", entity.name)
        assertEquals("/path/to/another/model.bin", entity.path)
        assertEquals(2048L, entity.size)
        assertEquals(false, entity.isActive)
    }
}
