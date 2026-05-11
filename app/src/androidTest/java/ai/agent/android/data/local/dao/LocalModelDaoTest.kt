package ai.agent.android.data.local.dao

import ai.agent.android.data.local.AppDatabase
import ai.agent.android.data.local.models.LocalModelEntity
import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LocalModelDaoTest {

    private lateinit var database: AppDatabase
    private lateinit var dao: LocalModelDao

    @Before
    fun setup() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        dao = database.localModelDao()
    }

    @After
    fun teardown() {
        database.close()
    }

    @Test
    fun insertAndGetModel() = runBlocking {
        val model = LocalModelEntity(name = "Model A", path = "/a", size = 100, isActive = false)
        val id = dao.insertModel(model)

        val allModels = dao.getAllModels().first()
        assertEquals(1, allModels.size)
        assertEquals("Model A", allModels[0].name)
        assertEquals(id, allModels[0].id)
    }

    @Test
    fun getActiveModel() = runBlocking {
        val model1 = LocalModelEntity(name = "Model A", path = "/a", size = 100, isActive = false)
        val model2 = LocalModelEntity(name = "Model B", path = "/b", size = 200, isActive = true)

        dao.insertModel(model1)
        dao.insertModel(model2)

        val active = dao.getActiveModel()
        assertNotNull(active)
        assertEquals("Model B", active?.name)
    }

    @Test
    fun deactivateAllAndActivateById() = runBlocking {
        val id1 = dao.insertModel(LocalModelEntity(name = "A", path = "/a", size = 100, isActive = true))
        val id2 = dao.insertModel(LocalModelEntity(name = "B", path = "/b", size = 200, isActive = false))

        // Deactivate all
        dao.deactivateAllModels()
        assertNull(dao.getActiveModel())

        // Activate second
        dao.activateModelById(id2)
        val active = dao.getActiveModel()
        assertNotNull(active)
        assertEquals("B", active?.name)
        assertEquals(id2, active?.id)
    }
}
