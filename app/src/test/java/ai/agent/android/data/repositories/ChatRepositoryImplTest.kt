package ai.agent.android.data.repositories

import ai.agent.android.data.local.dao.ChatDao
import ai.agent.android.data.local.dao.TraceStepDao
import ai.agent.android.data.local.models.ChatSessionEntity
import ai.agent.android.domain.models.ChatMessage
import ai.agent.android.domain.models.Role
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
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
    fun `given existing sessionId when saveMessage called then session timestamp is updated`() = runTest {
        val sessionId = "existing-session"
        val existingSession = ChatSessionEntity(id = sessionId, name = "Chat existi", updatedAt = 500L)
        val message = ChatMessage(id = 1L, sessionId = sessionId, role = Role.USER, content = "Hi", timestamp = 1500L)

        coEvery { chatDao.getSessionById(sessionId) } returns existingSession

        repository.saveMessage(message)

        coVerify(exactly = 1) { chatDao.updateSession(existingSession.copy(updatedAt = message.timestamp)) }
    }
}
