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

/**
 * Instrumented coverage for [LocalModelDao] — every public method is
 * exercised against an in-memory Room database so the `isActive`
 * single-row invariant, the `countByName` / `findByName` lookups, and
 * the `observeActiveModel` Flow emissions are pinned by the suite.
 */
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
        dao.insertModel(LocalModelEntity(name = "A", path = "/a", size = 100, isActive = true))
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

    @Test
    fun observeActiveModel_emitsOnToggle() = runBlocking {
        val id = dao.insertModel(LocalModelEntity(name = "A", path = "/a", size = 100, isActive = false))

        // No active model initially.
        assertNull(dao.observeActiveModel().first())

        dao.activateModelById(id)
        val active = dao.observeActiveModel().first()
        assertNotNull(active)
        assertEquals("A", active?.name)

        dao.deactivateAllModels()
        assertNull(dao.observeActiveModel().first())
    }

    @Test
    fun updateModel_replacesFields() = runBlocking {
        val id = dao.insertModel(LocalModelEntity(name = "old", path = "/old", size = 1L, isActive = false))

        dao.updateModel(LocalModelEntity(id = id, name = "new", path = "/new", size = 99L, isActive = true))

        val loaded = dao.getAllModels().first().single()
        assertEquals("new", loaded.name)
        assertEquals("/new", loaded.path)
        assertEquals(99L, loaded.size)
        assertEquals(true, loaded.isActive)
    }

    @Test
    fun deleteModelById_removesSingleRow() = runBlocking {
        val keep = dao.insertModel(LocalModelEntity(name = "keep", path = "/k", size = 1, isActive = false))
        val drop = dao.insertModel(LocalModelEntity(name = "drop", path = "/d", size = 2, isActive = false))

        dao.deleteModelById(drop)

        val survivors = dao.getAllModels().first()
        assertEquals(listOf(keep), survivors.map { it.id })
    }

    @Test
    fun countByName_reportsRowCount() = runBlocking {
        assertEquals(0, dao.countByName("ghost.tflite"))

        dao.insertModel(LocalModelEntity(name = "gemma.tflite", path = "/g", size = 1, isActive = false))
        assertEquals(1, dao.countByName("gemma.tflite"))
        assertEquals(0, dao.countByName("phi.tflite"))
    }

    @Test
    fun findByName_returnsMatchOrNull() = runBlocking {
        assertNull(dao.findByName("nope"))

        val id = dao.insertModel(LocalModelEntity(name = "gemma.tflite", path = "/g", size = 1, isActive = false))
        val found = dao.findByName("gemma.tflite")
        assertNotNull(found)
        assertEquals(id, found?.id)
        assertEquals("/g", found?.path)
    }

    @Test
    fun findByName_isDeterministicUnderDuplicateNames() = runBlocking {
        // Two rows with the same filename — the LIMIT 1 in the DAO guarantees
        // a deterministic single-row return. Either row id is acceptable;
        // the contract is "returns one matching row, never throws".
        val firstId = dao.insertModel(LocalModelEntity(name = "dup", path = "/a", size = 1, isActive = false))
        val secondId = dao.insertModel(LocalModelEntity(name = "dup", path = "/b", size = 2, isActive = false))

        val found = dao.findByName("dup")
        assertNotNull(found)
        assertEquals("dup", found?.name)
        assertEquals(true, found?.id == firstId || found?.id == secondId)
    }
}
