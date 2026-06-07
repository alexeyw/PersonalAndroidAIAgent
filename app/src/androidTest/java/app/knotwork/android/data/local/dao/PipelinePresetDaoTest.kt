package app.knotwork.android.data.local.dao

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.knotwork.android.data.local.AppDatabase
import app.knotwork.android.data.local.models.PipelinePresetEntity
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
 * Instrumented coverage for [PipelinePresetDao] (Phase 24 / Task 1).
 *
 * The DAO is intentionally small (CRUD over a self-contained row) but the
 * SQL semantics — REPLACE on conflict, ORDER BY createdAt DESC, scope of
 * `deleteById` — still need a real Room database to verify.
 */
@RunWith(AndroidJUnit4::class)
class PipelinePresetDaoTest {

    private lateinit var database: AppDatabase
    private lateinit var dao: PipelinePresetDao

    @Before
    fun setup() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        dao = database.pipelinePresetDao()
    }

    @After
    fun teardown() {
        database.close()
    }

    @Test
    fun upsertAndGetById_roundTripPreservesEveryColumn() = runBlocking {
        val entity = PipelinePresetEntity(
            id = "p1",
            name = "Test preset",
            description = "desc",
            categoryKey = "local",
            graphJson = """{"schemaVersion":1}""",
            tagsCsv = "alpha,beta",
            createdAt = 100L,
        )

        dao.upsert(entity)
        val loaded = dao.getById("p1")

        assertNotNull(loaded)
        assertEquals(entity, loaded)
    }

    @Test
    fun getById_returnsNullForUnknownId() = runBlocking {
        assertNull(dao.getById("missing"))
    }

    @Test
    fun upsert_replacesRowOnConflict() = runBlocking {
        val original = PipelinePresetEntity(
            id = "p1",
            name = "Old",
            description = "old",
            categoryKey = "local",
            graphJson = "{}",
            tagsCsv = "",
            createdAt = 1L,
        )
        val replacement = original.copy(name = "New", description = "new", createdAt = 2L)

        dao.upsert(original)
        dao.upsert(replacement)

        val loaded = dao.getById("p1")
        assertEquals("New", loaded?.name)
        assertEquals("new", loaded?.description)
        assertEquals(2L, loaded?.createdAt)
    }

    @Test
    fun getAll_ordersByCreatedAtDescending() = runBlocking {
        dao.upsert(entityAt(id = "oldest", createdAt = 100L))
        dao.upsert(entityAt(id = "newest", createdAt = 300L))
        dao.upsert(entityAt(id = "middle", createdAt = 200L))

        val ordered = dao.getAll().first().map { it.id }

        assertEquals(listOf("newest", "middle", "oldest"), ordered)
    }

    @Test
    fun deleteById_scopedToSingleRow() = runBlocking {
        dao.upsert(entityAt(id = "keep", createdAt = 1L))
        dao.upsert(entityAt(id = "remove", createdAt = 2L))

        dao.deleteById("remove")

        val remaining = dao.getAll().first().map { it.id }
        assertEquals(listOf("keep"), remaining)
        assertNull(dao.getById("remove"))
    }

    @Test
    fun deleteById_isNoOpForUnknownId() = runBlocking {
        dao.upsert(entityAt(id = "keep", createdAt = 1L))

        dao.deleteById("missing")

        assertEquals(1, dao.getAll().first().size)
    }

    private fun entityAt(id: String, createdAt: Long): PipelinePresetEntity = PipelinePresetEntity(
        id = id,
        name = id,
        description = "",
        categoryKey = "other",
        graphJson = "{}",
        tagsCsv = "",
        createdAt = createdAt,
    )
}
