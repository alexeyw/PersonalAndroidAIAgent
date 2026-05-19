package ai.agent.android.data.local.dao

import ai.agent.android.data.local.AppDatabase
import ai.agent.android.data.local.models.MemoryChunkEntity
import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Instrumented coverage for the SQL-bearing methods on [MemoryDao]. The
 * mocked unit tests in `MemoryRepositoryImplTest` can verify call routing
 * but cannot exercise the SQL — these tests run against an in-memory Room
 * database to lock the compaction / pinning semantics.
 */
@RunWith(AndroidJUnit4::class)
class MemoryDaoTest {

    private lateinit var database: AppDatabase
    private lateinit var dao: MemoryDao

    @Before
    fun setup() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        dao = database.memoryDao()
    }

    @After
    fun teardown() {
        database.close()
    }

    @Test
    fun deleteOldestMemories_keepsPinnedChunksRegardlessOfKeepLimit() = runBlocking {
        // Three unpinned rows + two pinned rows. With keepLimit = 1, the
        // compaction should leave 1 most-recent unpinned + both pinned = 3
        // rows total. Without the `isPinned = 0` guard the pinned rows would
        // be silently dropped because their ids fall outside the LIMIT 1
        // window.
        val oldestUnpinnedId = dao.insertMemory(unpinned(text = "u-old", timestamp = 100L))
        val midUnpinnedId = dao.insertMemory(unpinned(text = "u-mid", timestamp = 200L))
        val newestUnpinnedId = dao.insertMemory(unpinned(text = "u-new", timestamp = 300L))
        val pinnedOldId = dao.insertMemory(pinned(text = "p-old", timestamp = 50L))
        val pinnedRecentId = dao.insertMemory(pinned(text = "p-recent", timestamp = 250L))

        dao.deleteOldestMemories(keepLimit = 1)

        val survivors = dao.getAllMemories().associateBy { it.id }
        assertTrue("Pinned old chunk must survive", survivors.containsKey(pinnedOldId))
        assertTrue("Pinned recent chunk must survive", survivors.containsKey(pinnedRecentId))
        assertTrue("Most-recent unpinned must survive", survivors.containsKey(newestUnpinnedId))
        assertFalse("Older unpinned must be deleted", survivors.containsKey(midUnpinnedId))
        assertFalse("Oldest unpinned must be deleted", survivors.containsKey(oldestUnpinnedId))
        assertEquals(3, survivors.size)
    }

    @Test
    fun setMemoryPinned_flipsFlagOnSingleRow() = runBlocking {
        val id = dao.insertMemory(unpinned(text = "x", timestamp = 1L))

        dao.setMemoryPinned(id = id, isPinned = true)
        assertTrue(dao.getAllMemories().first { it.id == id }.isPinned)

        dao.setMemoryPinned(id = id, isPinned = false)
        assertFalse(dao.getAllMemories().first { it.id == id }.isPinned)
    }

    @Test
    fun updateMemory_replacesTextAndEmbeddingWithoutTouchingTimestamp() = runBlocking {
        val originalTimestamp = 42L
        val id = dao.insertMemory(
            MemoryChunkEntity(
                text = "before",
                embedding = "0.1,0.2",
                timestamp = originalTimestamp,
                isPinned = false,
            ),
        )

        dao.updateMemory(id = id, text = "after", embedding = "0.9,0.8")

        val updated = dao.getAllMemories().first { it.id == id }
        assertEquals("after", updated.text)
        assertEquals("0.9,0.8", updated.embedding)
        assertEquals(originalTimestamp, updated.timestamp)
        assertFalse(updated.isPinned)
    }

    private fun unpinned(text: String, timestamp: Long): MemoryChunkEntity = MemoryChunkEntity(
        text = text,
        embedding = "0.0",
        timestamp = timestamp,
        isPinned = false,
    )

    private fun pinned(text: String, timestamp: Long): MemoryChunkEntity = MemoryChunkEntity(
        text = text,
        embedding = "0.0",
        timestamp = timestamp,
        isPinned = true,
    )
}
