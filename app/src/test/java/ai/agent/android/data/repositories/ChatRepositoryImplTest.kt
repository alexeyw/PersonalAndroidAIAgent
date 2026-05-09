package ai.agent.android.data.repositories

import ai.agent.android.data.local.dao.ChatDao
import ai.agent.android.data.local.dao.TraceStepDao
import ai.agent.android.data.local.models.ChatMessageEntity
import ai.agent.android.data.local.models.ChatSessionEntity
import ai.agent.android.data.local.models.TraceStepEntity
import ai.agent.android.domain.models.ChatMessage
import ai.agent.android.domain.models.Role
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
    private lateinit var traceStepDao: TraceStepDao
    private lateinit var repository: ChatRepositoryImpl

    @Before
    fun setup() {
        chatDao = mockk(relaxed = true)
        traceStepDao = mockk(relaxed = true)
        repository = ChatRepositoryImpl(chatDao, traceStepDao)
    }

    @Test
    fun `given same sessionId when saveMessage called twice then getSessionById called only once`() = runTest {
        val sessionId = "session-abc"
        val existingSession = ChatSessionEntity(id = sessionId, name = "Chat session", updatedAt = 0L)
        val message1 = ChatMessage(id = 1L, sessionId = sessionId, role = Role.USER, content = "Hello", timestamp = 1000L)
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
        val message = ChatMessage(id = 1L, sessionId = sessionId, role = Role.USER, content = "Hello", timestamp = 1000L)

        coEvery { chatDao.getSessionById(sessionId) } returns null

        repository.saveMessage(message)

        coVerify(exactly = 1) { chatDao.getSessionById(sessionId) }
        coVerify(exactly = 1) {
            chatDao.insertSession(
                ChatSessionEntity(
                    id = sessionId,
                    name = "Chat " + sessionId.take(6),
                    updatedAt = message.timestamp
                )
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
    fun `given saveTraceStep when called then entity carries durationMs and tokenCount`() = runTest {
        val captured: CapturingSlot<TraceStepEntity> = slot()
        coEvery { traceStepDao.insertTraceStep(capture(captured)) } returns Unit

        repository.saveTraceStep(
            sessionId = "s1",
            nodeName = "LITE_RT",
            outputText = "hello",
            durationMs = 234L,
            tokenCount = 18,
        )

        assertEquals("s1", captured.captured.sessionId)
        assertEquals("LITE_RT", captured.captured.nodeName)
        assertEquals("hello", captured.captured.outputText)
        assertEquals(234L, captured.captured.durationMs)
        assertEquals(18, captured.captured.tokenCount)
    }

    @Test
    fun `given saveTraceStep when tokenCount is null then entity preserves null`() = runTest {
        val captured: CapturingSlot<TraceStepEntity> = slot()
        coEvery { traceStepDao.insertTraceStep(capture(captured)) } returns Unit

        repository.saveTraceStep(
            sessionId = "s2",
            nodeName = "INTENT_ROUTER",
            outputText = "Route=Data",
            durationMs = 10L,
            tokenCount = null,
        )

        assertEquals(null, captured.captured.tokenCount)
        assertEquals(10L, captured.captured.durationMs)
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
    fun `given saveSession when called then upsertSession is invoked once`() = runTest {
        // Defect 8 regression guard: `saveSession` must perform a single DAO round-trip
        // via `@Upsert`, replacing the previous SELECT + INSERT/UPDATE pattern. Verifying
        // the legacy methods are NOT called also guards against silent regressions where
        // an old code path is reintroduced.
        val session = ai.agent.android.domain.models.ChatSession(
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
