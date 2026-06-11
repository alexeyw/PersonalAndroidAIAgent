package app.knotwork.android.data.repositories

import app.knotwork.android.data.local.dao.ChatDao
import app.knotwork.android.data.local.models.ChatMessageEntity
import app.knotwork.android.data.local.models.ChatSessionEntity
import app.knotwork.android.domain.models.ChatMessage
import app.knotwork.android.domain.models.ChatSession
import app.knotwork.android.domain.models.Role
import io.mockk.CapturingSlot
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

class ChatRepositoryImplTest {

    private lateinit var chatDao: ChatDao
    private lateinit var repository: ChatRepositoryImpl

    @Before
    fun setup() {
        chatDao = mockk(relaxed = true)
        repository = ChatRepositoryImpl(chatDao)
    }

    /**
     * Session deletion must go through the single transactional DAO method —
     * messages, pipeline-run records (no FK cascade) and the session row die
     * together or not at all.
     */
    @Test
    fun `given session deletion then transactional complete delete is used`() = runTest {
        repository.deleteSession("session-abc")

        coVerify { chatDao.deleteSessionCompletely("session-abc") }
    }

    @Test
    fun `given same sessionId when saveMessage called twice then getSessionById called only once`() = runTest {
        val sessionId = "session-abc"
        val existingSession = ChatSessionEntity(id = sessionId, name = "Chat session", updatedAt = 0L)
        val message1 =
            ChatMessage(id = 1L, sessionId = sessionId, role = Role.USER, content = "Hello", timestamp = 1000L)
        val message2 = ChatMessage(id = 2L, sessionId = sessionId, role = Role.AGENT, content = "Hi", timestamp = 2000L)

        coEvery { chatDao.getSessionById(sessionId) } returns existingSession

        repository.saveMessage(message1)
        repository.saveMessage(message2)

        coVerify(exactly = 1) { chatDao.getSessionById(sessionId) }
        coVerify(exactly = 1) { chatDao.updateSessionTimestamp(sessionId, message2.timestamp) }
    }

    @Test
    fun `given non-existent sessionId when saveMessage called then session is created`() = runTest {
        val sessionId = "new-session"
        val message =
            ChatMessage(id = 1L, sessionId = sessionId, role = Role.USER, content = "Hello", timestamp = 1000L)

        coEvery { chatDao.getSessionById(sessionId) } returns null

        repository.saveMessage(message)

        coVerify(exactly = 1) { chatDao.getSessionById(sessionId) }
        coVerify(exactly = 1) {
            chatDao.insertSession(
                ChatSessionEntity(
                    id = sessionId,
                    name = "Chat " + sessionId.take(6),
                    updatedAt = message.timestamp,
                ),
            )
        }
    }

    @Test
    fun `given different sessionIds when saveMessage called for each then getSessionById called for each`() = runTest {
        val sessionId1 = "session-1"
        val sessionId2 = "session-2"
        val session1 = ChatSessionEntity(id = sessionId1, name = "Chat ses", updatedAt = 0L)
        val session2 = ChatSessionEntity(id = sessionId2, name = "Chat ses", updatedAt = 0L)
        val message1 = ChatMessage(id = 1L, sessionId = sessionId1, role = Role.USER, content = "A", timestamp = 1000L)
        val message2 = ChatMessage(id = 2L, sessionId = sessionId2, role = Role.USER, content = "B", timestamp = 2000L)

        coEvery { chatDao.getSessionById(sessionId1) } returns session1
        coEvery { chatDao.getSessionById(sessionId2) } returns session2

        repository.saveMessage(message1)
        repository.saveMessage(message2)

        coVerify(exactly = 1) { chatDao.getSessionById(sessionId1) }
        coVerify(exactly = 1) { chatDao.getSessionById(sessionId2) }
    }

    @Test
    fun `given existing sessionId when saveMessage called then session timestamp is updated`() = runTest {
        val sessionId = "existing-session"
        val existingSession = ChatSessionEntity(id = sessionId, name = "Chat existi", updatedAt = 500L)
        val message = ChatMessage(id = 1L, sessionId = sessionId, role = Role.USER, content = "Hi", timestamp = 1500L)

        coEvery { chatDao.getSessionById(sessionId) } returns existingSession

        repository.saveMessage(message)

        coVerify(exactly = 1) { chatDao.updateSession(existingSession.copy(updatedAt = message.timestamp)) }
    }

    @Test
    fun `given display flow when collected then only isFinal=true messages are emitted`() = runTest {
        val sessionId = "display-session"
        val finalEntity = ChatMessageEntity(
            id = 1L,
            sessionId = sessionId,
            role = "AGENT",
            content = "final answer",
            timestamp = 1000L,
            isFinal = true,
            isStarred = false,
        )
        every { chatDao.getDisplayMessagesBySessionId(sessionId) } returns flowOf(listOf(finalEntity))

        val emitted = repository.getDisplayMessagesForSession(sessionId).first()

        assertEquals(1, emitted.size)
        assertEquals("final answer", emitted.first().content)
        assertEquals(true, emitted.first().isFinal)
    }

    @Test
    fun `given setMessageStarred when called then dao update is invoked with same args`() = runTest {
        repository.setMessageStarred(messageId = 42L, starred = true)
        coVerify(exactly = 1) { chatDao.setMessageStarred(42L, true) }
    }

    @Test
    fun `given starred flow when collected then mapped messages preserve isStarred`() = runTest {
        val starred = ChatMessageEntity(
            id = 7L,
            sessionId = "any",
            role = "USER",
            content = "saved msg",
            timestamp = 1L,
            isFinal = true,
            isStarred = true,
        )
        every { chatDao.getStarredMessages() } returns flowOf(listOf(starred))

        val emitted = repository.getStarredMessages().first()

        assertEquals(1, emitted.size)
        assertEquals(true, emitted.first().isStarred)
    }

    @Test
    fun `given renameSession when called then dao renameSession is invoked with same args`() = runTest {
        repository.renameSession("sess-rename", "Brand new name")
        coVerify(exactly = 1) { chatDao.renameSession("sess-rename", "Brand new name") }
        // No other session-touching DAO call should fire — rename is a single UPDATE.
        coVerify(exactly = 0) { chatDao.getSessionById(any()) }
        coVerify(exactly = 0) { chatDao.upsertSession(any()) }
        coVerify(exactly = 0) { chatDao.updateSession(any()) }
    }

    @Test
    fun `given setSessionFavorite when called then dao setSessionStarred flips the flag`() = runTest {
        repository.setSessionFavorite("sess-fav", true)
        repository.setSessionFavorite("sess-fav", false)
        coVerify(exactly = 1) { chatDao.setSessionStarred("sess-fav", true) }
        coVerify(exactly = 1) { chatDao.setSessionStarred("sess-fav", false) }
    }

    @Test
    fun `given importChat with export-shaped json when called then session and messages persisted`() = runTest {
        val sessionSlot: CapturingSlot<ChatSessionEntity> = slot()
        val messages = mutableListOf<ChatMessageEntity>()
        coEvery { chatDao.upsertSession(capture(sessionSlot)) } returns Unit
        coEvery { chatDao.insertMessage(capture(messages)) } returns Unit

        val json = """{"sessionName":"Trip plan","messages":[
            {"role":"USER","text":"Plan a trip","timestamp":111},
            {"role":"AGENT","text":"Sure","timestamp":222}
        ]}
        """.trimIndent()

        val newId = repository.importChat(json)

        assertEquals("Trip plan", sessionSlot.captured.name)
        assertEquals(newId, sessionSlot.captured.id)
        assertEquals(2, messages.size)
        assertEquals("Plan a trip", messages[0].content)
        assertEquals("USER", messages[0].role)
        assertEquals(111L, messages[0].timestamp)
        assertEquals("AGENT", messages[1].role)
        assertEquals(newId, messages[0].sessionId)
        assertEquals(newId, messages[1].sessionId)
    }

    @Test
    fun `given importChat with bare-array json when called then session uses default imported name`() = runTest {
        val sessionSlot: CapturingSlot<ChatSessionEntity> = slot()
        coEvery { chatDao.upsertSession(capture(sessionSlot)) } returns Unit

        val json = """[{"role":"USER","text":"hi","timestamp":1}]"""
        repository.importChat(json)

        assertEquals("Imported Chat", sessionSlot.captured.name)
    }

    @Test(expected = org.json.JSONException::class)
    fun `given importChat with non-JSON when called then throws JSONException`() = runTest {
        repository.importChat("not json at all")
    }

    @Test
    fun `given saveSession when called then upsertSession is invoked once`() = runTest {
        // Defect 8 regression guard: `saveSession` must perform a single DAO round-trip
        // via `@Upsert`, replacing the previous SELECT + INSERT/UPDATE pattern. Verifying
        // the legacy methods are NOT called also guards against silent regressions where
        // an old code path is reintroduced.
        val session = ChatSession(
            id = "sess-x",
            name = "Some chat",
            updatedAt = 1000L,
        )

        repository.saveSession(session)

        coVerify(exactly = 1) {
            chatDao.upsertSession(
                ChatSessionEntity(id = "sess-x", name = "Some chat", updatedAt = 1000L),
            )
        }
        coVerify(exactly = 0) { chatDao.getSessionById("sess-x") }
        coVerify(exactly = 0) { chatDao.insertSession(any()) }
        coVerify(exactly = 0) { chatDao.updateSession(any()) }
    }
}
