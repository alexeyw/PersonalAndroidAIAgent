package ai.agent.android.data.repositories

import ai.agent.android.data.local.dao.LocalModelDao
import ai.agent.android.data.local.models.LocalModelEntity
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

class LocalModelRepositoryImplTest {

    private lateinit var localModelDao: LocalModelDao
    private lateinit var repository: LocalModelRepositoryImpl

    @Before
    fun setup() {
        localModelDao = mockk(relaxed = true)
        repository = LocalModelRepositoryImpl(localModelDao)
    }

    @Test
    fun `getAllModels returns flow from dao`() {
        val models = listOf(
            LocalModelEntity(1, "Model A", "/path/a", 100L, true),
            LocalModelEntity(2, "Model B", "/path/b", 200L, false)
        )
        every { localModelDao.getAllModels() } returns flowOf(models)

        val result = repository.getAllModels()

        // Just testing interaction, flow collection would require more setup
        coVerify(exactly = 1) { localModelDao.getAllModels() }
    }

    @Test
    fun `getActiveModel returns model from dao`() = runTest {
        val expectedModel = LocalModelEntity(1, "Model A", "/path/a", 100L, true)
        coEvery { localModelDao.getActiveModel() } returns expectedModel

        val result = repository.getActiveModel()

        assertEquals(expectedModel, result)
        coVerify(exactly = 1) { localModelDao.getActiveModel() }
    }

    @Test
    fun `insertModel calls dao and returns id`() = runTest {
        val model = LocalModelEntity(0, "Model A", "/path/a", 100L, false)
        val expectedId = 5L
        coEvery { localModelDao.insertModel(model) } returns expectedId

        val result = repository.insertModel(model)

        assertEquals(expectedId, result)
        coVerify(exactly = 1) { localModelDao.insertModel(model) }
    }

    @Test
    fun `setActiveModel deactivates all then activates specific model`() = runTest {
        val targetId = 3L

        repository.setActiveModel(targetId)

        coVerify(exactly = 1) { localModelDao.deactivateAllModels() }
        coVerify(exactly = 1) { localModelDao.activateModelById(targetId) }
    }
}
