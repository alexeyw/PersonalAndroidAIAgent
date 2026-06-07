package app.knotwork.android.data.local.dao

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.knotwork.android.data.local.AppDatabase
import app.knotwork.android.data.local.models.ChatSessionEntity
import app.knotwork.android.data.local.models.TraceStepEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Instrumented coverage for [TraceStepDao]. Verifies the per-session
 * `timestamp ASC` query contract, the `durationMs` / `tokenCount`
 * column round-trip introduced by MIGRATION_15_16, scoped deletion and
 * the foreign-key cascade from `chat_sessions` (Room enforces FK
 * cascades when the schema was created via the @Entity declaration on
 * the in-memory DB used here).
 */
@RunWith(AndroidJUnit4::class)
class TraceStepDaoTest {

    private lateinit var database: AppDatabase
    private lateinit var traceStepDao: TraceStepDao
    private lateinit var chatDao: ChatDao

    @Before
    fun setup() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        traceStepDao = database.traceStepDao()
        chatDao = database.chatDao()
    }

    @After
    fun teardown() {
        database.close()
    }

    @Test
    fun insertAndGetTraceStepsForSession_ordersByTimestampAsc() = runBlocking {
        chatDao.insertSession(ChatSessionEntity(id = "s", name = "n", updatedAt = 0L))

        traceStepDao.insertTraceStep(step(sessionId = "s", nodeName = "LATE", timestamp = 30L))
        traceStepDao.insertTraceStep(step(sessionId = "s", nodeName = "EARLY", timestamp = 10L))
        traceStepDao.insertTraceStep(step(sessionId = "s", nodeName = "MID", timestamp = 20L))

        val ordered = traceStepDao.getTraceStepsForSession("s").first().map { it.nodeName }
        assertEquals(listOf("EARLY", "MID", "LATE"), ordered)
    }

    @Test
    fun getTraceStepsForSession_filtersByOwningSession() = runBlocking {
        chatDao.insertSession(ChatSessionEntity(id = "a", name = "A", updatedAt = 0L))
        chatDao.insertSession(ChatSessionEntity(id = "b", name = "B", updatedAt = 0L))

        traceStepDao.insertTraceStep(step(sessionId = "a", nodeName = "Na", timestamp = 1L))
        traceStepDao.insertTraceStep(step(sessionId = "b", nodeName = "Nb", timestamp = 2L))

        assertEquals(listOf("Na"), traceStepDao.getTraceStepsForSession("a").first().map { it.nodeName })
        assertEquals(listOf("Nb"), traceStepDao.getTraceStepsForSession("b").first().map { it.nodeName })
    }

    @Test
    fun insertTraceStep_roundTripsDurationAndTokenCount() = runBlocking {
        chatDao.insertSession(ChatSessionEntity(id = "s", name = "n", updatedAt = 0L))

        traceStepDao.insertTraceStep(
            TraceStepEntity(
                sessionId = "s",
                nodeName = "LITE_RT",
                outputText = "hello",
                timestamp = 100L,
                durationMs = 250L,
                tokenCount = 42,
            ),
        )
        traceStepDao.insertTraceStep(
            TraceStepEntity(
                sessionId = "s",
                nodeName = "TOOL",
                outputText = "no-tokens",
                timestamp = 200L,
                durationMs = 12L,
                tokenCount = null,
            ),
        )

        val rows = traceStepDao.getTraceStepsForSession("s").first()
        val litert = rows.first { it.nodeName == "LITE_RT" }
        val tool = rows.first { it.nodeName == "TOOL" }
        assertEquals(250L, litert.durationMs)
        assertEquals(42, litert.tokenCount)
        assertEquals(12L, tool.durationMs)
        assertNull(tool.tokenCount)
    }

    @Test
    fun deleteTraceStepsForSession_scopedDelete() = runBlocking {
        chatDao.insertSession(ChatSessionEntity(id = "a", name = "A", updatedAt = 0L))
        chatDao.insertSession(ChatSessionEntity(id = "b", name = "B", updatedAt = 0L))
        traceStepDao.insertTraceStep(step(sessionId = "a", nodeName = "Na", timestamp = 1L))
        traceStepDao.insertTraceStep(step(sessionId = "b", nodeName = "Nb", timestamp = 2L))

        traceStepDao.deleteTraceStepsForSession("a")

        assertTrue(traceStepDao.getTraceStepsForSession("a").first().isEmpty())
        assertEquals(listOf("Nb"), traceStepDao.getTraceStepsForSession("b").first().map { it.nodeName })
    }

    @Test
    fun deletingOwningSession_cascadesTraceSteps() = runBlocking {
        // Room enables FK enforcement by default, so deleting the owning
        // chat_sessions row must cascade to trace_steps via the
        // `ON DELETE CASCADE` declared on the entity.
        chatDao.insertSession(ChatSessionEntity(id = "s", name = "n", updatedAt = 0L))
        traceStepDao.insertTraceStep(step(sessionId = "s", nodeName = "node", timestamp = 1L))

        chatDao.deleteSession("s")

        assertTrue(traceStepDao.getTraceStepsForSession("s").first().isEmpty())
    }

    private fun step(sessionId: String, nodeName: String, timestamp: Long): TraceStepEntity = TraceStepEntity(
        sessionId = sessionId,
        nodeName = nodeName,
        outputText = "out-$nodeName",
        timestamp = timestamp,
    )
}
