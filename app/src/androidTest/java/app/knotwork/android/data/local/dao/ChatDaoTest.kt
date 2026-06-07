package app.knotwork.android.data.local.dao

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.knotwork.android.data.local.AppDatabase
import app.knotwork.android.data.local.models.ChatMessageEntity
import app.knotwork.android.data.local.models.ChatSessionEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Instrumented coverage for [ChatDao] — both the `chat_messages` and
 * `chat_sessions` tables. The repository-level mocked tests can verify
 * call routing but cannot exercise the SQL semantics (defaults,
 * `isFinal` / `isStarred` filtering, `Upsert` conflict handling, partial
 * `UPDATE` statements that touch a single column), which is why every
 * branch here runs against an in-memory Room database.
 */
@RunWith(AndroidJUnit4::class)
class ChatDaoTest {

    private lateinit var db: AppDatabase
    private lateinit var chatDao: ChatDao

    @Before
    fun createDb() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(
            context,
            AppDatabase::class.java,
        ).allowMainThreadQueries().build()
        chatDao = db.chatDao()
    }

    @After
    fun closeDb() {
        db.close()
    }

    // ---------------------------------------------------------------------
    // chat_messages
    // ---------------------------------------------------------------------

    @Test
    fun insertAndGetMessages() = runBlocking {
        val message1 = ChatMessageEntity(sessionId = "session1", role = "USER", content = "Hello", timestamp = 1000L)
        val message2 = ChatMessageEntity(sessionId = "session1", role = "AGENT", content = "Hi", timestamp = 2000L)
        val messageOther = ChatMessageEntity(sessionId = "session2", role = "USER", content = "Test", timestamp = 3000L)

        chatDao.insertMessage(message1)
        chatDao.insertMessage(message2)
        chatDao.insertMessage(messageOther)

        val messages = chatDao.getMessagesBySessionId("session1").first()

        assertEquals(2, messages.size)
        assertEquals("Hello", messages[0].content)
        assertEquals("Hi", messages[1].content)
    }

    @Test
    fun getDisplayMessagesBySessionId_excludesNonFinalRows() = runBlocking {
        chatDao.insertMessage(
            ChatMessageEntity(sessionId = "s", role = "USER", content = "user", timestamp = 1L, isFinal = true),
        )
        chatDao.insertMessage(
            ChatMessageEntity(sessionId = "s", role = "TOOL", content = "internal", timestamp = 2L, isFinal = false),
        )
        chatDao.insertMessage(
            ChatMessageEntity(sessionId = "s", role = "AGENT", content = "answer", timestamp = 3L, isFinal = true),
        )

        val all = chatDao.getMessagesBySessionId("s").first()
        val display = chatDao.getDisplayMessagesBySessionId("s").first()

        assertEquals(3, all.size)
        assertEquals(2, display.size)
        assertEquals(listOf("user", "answer"), display.map { it.content })
    }

    @Test
    fun setMessageStarred_andGetStarredMessages() = runBlocking {
        chatDao.insertMessage(ChatMessageEntity(sessionId = "s", role = "USER", content = "a", timestamp = 1L))
        chatDao.insertMessage(ChatMessageEntity(sessionId = "s", role = "USER", content = "b", timestamp = 2L))
        chatDao.insertMessage(ChatMessageEntity(sessionId = "s", role = "USER", content = "c", timestamp = 3L))

        // No starred rows initially.
        assertTrue(chatDao.getStarredMessages().first().isEmpty())

        val rows = chatDao.getMessagesBySessionId("s").first()
        chatDao.setMessageStarred(messageId = rows[0].id, starred = true)
        chatDao.setMessageStarred(messageId = rows[2].id, starred = true)

        val starred = chatDao.getStarredMessages().first()
        assertEquals(listOf("a", "c"), starred.map { it.content })

        // Unstar one — the projection updates.
        chatDao.setMessageStarred(messageId = rows[0].id, starred = false)
        val afterUnstar = chatDao.getStarredMessages().first()
        assertEquals(listOf("c"), afterUnstar.map { it.content })
    }

    @Test
    fun getAllSessions_returnsDistinctSessionIds() = runBlocking {
        chatDao.insertMessage(ChatMessageEntity(sessionId = "a", role = "USER", content = "1", timestamp = 1L))
        chatDao.insertMessage(ChatMessageEntity(sessionId = "a", role = "AGENT", content = "2", timestamp = 2L))
        chatDao.insertMessage(ChatMessageEntity(sessionId = "b", role = "USER", content = "3", timestamp = 3L))

        val sessions = chatDao.getAllSessions().toSet()
        assertEquals(setOf("a", "b"), sessions)
    }

    @Test
    fun deleteMessageById_removesOnlyMatchingRow() = runBlocking {
        chatDao.insertMessage(ChatMessageEntity(sessionId = "s", role = "USER", content = "keep", timestamp = 1L))
        chatDao.insertMessage(ChatMessageEntity(sessionId = "s", role = "AGENT", content = "remove", timestamp = 2L))

        val rows = chatDao.getMessagesBySessionId("s").first()
        val toRemove = rows.single { it.content == "remove" }
        chatDao.deleteMessageById(toRemove.id)

        val survivors = chatDao.getMessagesBySessionId("s").first()
        assertEquals(listOf("keep"), survivors.map { it.content })
    }

    @Test
    fun getRecentMessagesByRole_filtersAndAppliesLimit() = runBlocking {
        chatDao.insertMessage(ChatMessageEntity(sessionId = "s", role = "USER", content = "u1", timestamp = 10L))
        chatDao.insertMessage(ChatMessageEntity(sessionId = "s", role = "AGENT", content = "a1", timestamp = 20L))
        chatDao.insertMessage(ChatMessageEntity(sessionId = "s", role = "USER", content = "u2", timestamp = 30L))
        chatDao.insertMessage(ChatMessageEntity(sessionId = "s", role = "USER", content = "u3", timestamp = 40L))

        val recent = chatDao.getRecentMessagesByRole(role = "USER", limit = 2).first()
        // DESC by timestamp + LIMIT 2 → u3, u2.
        assertEquals(listOf("u3", "u2"), recent.map { it.content })
    }

    @Test
    fun deleteSessionMessages() = runBlocking {
        val message1 = ChatMessageEntity(sessionId = "session1", role = "USER", content = "Hello", timestamp = 1000L)
        val message2 = ChatMessageEntity(sessionId = "session2", role = "USER", content = "Test", timestamp = 2000L)

        chatDao.insertMessage(message1)
        chatDao.insertMessage(message2)

        // Use the message-bearing DELETE: `deleteSession(id)` only removes a
        // row from `chat_sessions`, while `chat_messages` rows are scoped by
        // `deleteSessionMessages(sessionId)`.
        chatDao.deleteSessionMessages("session1")

        val session1Messages = chatDao.getMessagesBySessionId("session1").first()
        val session2Messages = chatDao.getMessagesBySessionId("session2").first()

        assertTrue(session1Messages.isEmpty())
        assertEquals(1, session2Messages.size)
    }

    @Test
    fun insertMessage_defaultsForIsFinalAndIsStarred() = runBlocking {
        // No-arg construction relies on the entity defaults (`isFinal = true`,
        // `isStarred = false`). Verifies the schema-level DEFAULT clauses
        // produced by the migrations match the Kotlin defaults.
        chatDao.insertMessage(ChatMessageEntity(sessionId = "s", role = "USER", content = "x", timestamp = 1L))
        val row = chatDao.getMessagesBySessionId("s").first().single()
        assertTrue(row.isFinal)
        assertFalse(row.isStarred)
    }

    // ---------------------------------------------------------------------
    // chat_sessions
    // ---------------------------------------------------------------------

    @Test
    fun insertSession_andGetSessionById() = runBlocking {
        val session = ChatSessionEntity(id = "s1", name = "Alpha", updatedAt = 100L)
        chatDao.insertSession(session)

        val loaded = chatDao.getSessionById("s1")
        assertNotNull(loaded)
        assertEquals("Alpha", loaded?.name)
        assertEquals(100L, loaded?.updatedAt)
        assertNull(loaded?.pipelineId)
        assertFalse(loaded?.isStarred ?: true)
    }

    @Test
    fun getSessionById_returnsNullForUnknownId() = runBlocking {
        assertNull(chatDao.getSessionById("missing"))
    }

    @Test
    fun getSessionsFlow_ordersByUpdatedAtDesc() = runBlocking {
        chatDao.insertSession(ChatSessionEntity(id = "old", name = "Old", updatedAt = 100L))
        chatDao.insertSession(ChatSessionEntity(id = "new", name = "New", updatedAt = 300L))
        chatDao.insertSession(ChatSessionEntity(id = "mid", name = "Mid", updatedAt = 200L))

        val ordered = chatDao.getSessionsFlow().first().map { it.id }
        assertEquals(listOf("new", "mid", "old"), ordered)
    }

    @Test
    fun updateSession_replacesFields() = runBlocking {
        chatDao.insertSession(ChatSessionEntity(id = "s", name = "Before", updatedAt = 100L))

        chatDao.updateSession(
            ChatSessionEntity(id = "s", name = "After", updatedAt = 500L, pipelineId = "p1"),
        )

        val loaded = chatDao.getSessionById("s")
        assertEquals("After", loaded?.name)
        assertEquals(500L, loaded?.updatedAt)
        assertEquals("p1", loaded?.pipelineId)
    }

    @Test
    fun deleteSession_removesOnlyMatchingRow() = runBlocking {
        chatDao.insertSession(ChatSessionEntity(id = "keep", name = "Keep", updatedAt = 1L))
        chatDao.insertSession(ChatSessionEntity(id = "drop", name = "Drop", updatedAt = 2L))

        chatDao.deleteSession("drop")

        val survivors = chatDao.getSessionsFlow().first().map { it.id }
        assertEquals(listOf("keep"), survivors)
    }

    @Test
    fun updateSessionTimestamp_updatesOnlyTimestamp() = runBlocking {
        chatDao.insertSession(ChatSessionEntity(id = "s", name = "Name", updatedAt = 1L, pipelineId = "p"))

        chatDao.updateSessionTimestamp(sessionId = "s", timestamp = 9999L)

        val loaded = chatDao.getSessionById("s")
        assertEquals(9999L, loaded?.updatedAt)
        assertEquals("Name", loaded?.name)
        assertEquals("p", loaded?.pipelineId)
    }

    @Test
    fun updateSessionTimestamp_noOpForUnknownId() = runBlocking {
        chatDao.insertSession(ChatSessionEntity(id = "s", name = "n", updatedAt = 5L))
        chatDao.updateSessionTimestamp(sessionId = "missing", timestamp = 9999L)

        assertEquals(5L, chatDao.getSessionById("s")?.updatedAt)
    }

    @Test
    fun upsertSession_insertsThenUpdates() = runBlocking {
        // Initial upsert behaves as INSERT.
        chatDao.upsertSession(ChatSessionEntity(id = "s", name = "First", updatedAt = 1L))
        assertEquals("First", chatDao.getSessionById("s")?.name)

        // Second upsert with the same primary key behaves as UPDATE.
        chatDao.upsertSession(ChatSessionEntity(id = "s", name = "Second", updatedAt = 2L, isStarred = true))
        val loaded = chatDao.getSessionById("s")
        assertEquals("Second", loaded?.name)
        assertEquals(2L, loaded?.updatedAt)
        assertTrue(loaded?.isStarred == true)
    }

    @Test
    fun renameSession_updatesName() = runBlocking {
        chatDao.insertSession(ChatSessionEntity(id = "s", name = "Before", updatedAt = 1L))

        chatDao.renameSession(sessionId = "s", newName = "After")

        assertEquals("After", chatDao.getSessionById("s")?.name)
    }

    @Test
    fun renameSession_noOpForUnknownId() = runBlocking {
        chatDao.insertSession(ChatSessionEntity(id = "s", name = "Stay", updatedAt = 1L))
        chatDao.renameSession(sessionId = "missing", newName = "Ghost")

        assertEquals("Stay", chatDao.getSessionById("s")?.name)
    }

    @Test
    fun setSessionStarred_togglesFlag() = runBlocking {
        chatDao.insertSession(ChatSessionEntity(id = "s", name = "n", updatedAt = 1L))

        chatDao.setSessionStarred(sessionId = "s", starred = true)
        assertTrue(chatDao.getSessionById("s")?.isStarred == true)

        chatDao.setSessionStarred(sessionId = "s", starred = false)
        assertFalse(chatDao.getSessionById("s")?.isStarred == true)
    }
}
