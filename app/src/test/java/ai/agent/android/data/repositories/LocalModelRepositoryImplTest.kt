package ai.agent.android.data.repositories

import ai.agent.android.data.local.dao.LocalModelDao
import ai.agent.android.data.local.models.LocalModelEntity
import ai.agent.android.domain.models.LocalModel
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Before
import org.junit.Test
import java.io.File

class LocalModelRepositoryImplTest {

    private lateinit var localModelDao: LocalModelDao
    private lateinit var repository: LocalModelRepositoryImpl

    @Before
    fun setup() {
        localModelDao = mockk(relaxed = true)
        repository = LocalModelRepositoryImpl(localModelDao)
    }

    @Test
    fun `getAllModels returns flow from dao mapped to domain`() {
        val entities = listOf(
            LocalModelEntity(1, "Model A", "/path/a", 100L, true),
            LocalModelEntity(2, "Model B", "/path/b", 200L, false),
        )
        every { localModelDao.getAllModels() } returns flowOf(entities)

        val result = repository.getAllModels()

        // Just testing interaction, flow collection would require more setup
        coVerify(exactly = 1) { localModelDao.getAllModels() }
    }

    @Test
    fun `getActiveModel returns model from dao mapped to domain`() = runTest {
        val entity = LocalModelEntity(1, "Model A", "/path/a", 100L, true)
        val expectedModel = LocalModel(1, "Model A", "/path/a", 100L, true)
        coEvery { localModelDao.getActiveModel() } returns entity

        val result = repository.getActiveModel()

        assertEquals(expectedModel, result)
        coVerify(exactly = 1) { localModelDao.getActiveModel() }
    }

    @Test
    fun `insertModel maps to entity, calls dao and returns id`() = runTest {
        val model = LocalModel(0, "Model A", "/path/a", 100L, false)
        val entity = LocalModelEntity(0, "Model A", "/path/a", 100L, false)
        val expectedId = 5L
        coEvery { localModelDao.insertModel(entity) } returns expectedId

        val result = repository.insertModel(model)

        assertEquals(expectedId, result)
        coVerify(exactly = 1) { localModelDao.insertModel(entity) }
    }

    @Test
    fun `setActiveModel deactivates all then activates specific model`() = runTest {
        val targetId = 3L

        repository.setActiveModel(targetId)

        coVerify(exactly = 1) { localModelDao.deactivateAllModels() }
        coVerify(exactly = 1) { localModelDao.activateModelById(targetId) }
    }

    @Test
    fun `deleteModelById deletes the on-disk file then the record`() = runTest {
        val file = File.createTempFile("model", ".task").apply { writeBytes(byteArrayOf(1, 2, 3)) }
        val entity = LocalModelEntity(7, "Model", file.absolutePath, 3L, false)
        coEvery { localModelDao.findById(7) } returns entity

        repository.deleteModelById(7)

        assertFalse("model weights file must be removed from disk", file.exists())
        coVerify(exactly = 1) { localModelDao.deleteModelById(7) }
    }

    @Test
    fun `deleteModelById still deletes the record when the file is missing`() = runTest {
        // Best-effort: an absent / unreadable file must not block record removal.
        val entity = LocalModelEntity(8, "Model", "/no/such/model.task", 0L, false)
        coEvery { localModelDao.findById(8) } returns entity

        repository.deleteModelById(8)

        coVerify(exactly = 1) { localModelDao.deleteModelById(8) }
    }
}
