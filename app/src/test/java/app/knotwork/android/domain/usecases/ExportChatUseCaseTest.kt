package app.knotwork.android.domain.usecases

import app.knotwork.android.domain.models.ChatMessage
import app.knotwork.android.domain.models.ChatSession
import app.knotwork.android.domain.models.Role
import app.knotwork.android.domain.repositories.ChatRepository
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Unit tests for [ExportChatUseCase].
 *
 * The use case is the single serialiser behind both export entry points (the
 * chat top bar and an archive row), so the contract under test is: the document
 * shape is stable, an **archived** session exports exactly like an active one,
 * and failures arrive as [Result.failure] rather than escaping.
 *
 * Runs under Robolectric because the serialiser uses the platform `org.json`.
 */
@RunWith(RobolectricTestRunner::class)
class ExportChatUseCaseTest {

    private lateinit var chatRepository: ChatRepository
    private lateinit var useCase: ExportChatUseCase

    private val session = ChatSession(id = SESSION_ID, name = "Trip planning", updatedAt = 1_000L)

    private val messages = listOf(
        ChatMessage(sessionId = SESSION_ID, role = Role.USER, content = "Where to?", timestamp = 10L),
        ChatMessage(sessionId = SESSION_ID, role = Role.AGENT, content = "Lisbon.", timestamp = 20L),
    )

    @Before
    fun setup() {
        chatRepository = mockk()
        useCase = ExportChatUseCase(chatRepository)
    }

    @Test
    fun `given a session when invoked then the document carries every message in order`() = runTest {
        coEvery { chatRepository.getSessionById(SESSION_ID) } returns session
        every { chatRepository.getMessagesForSession(SESSION_ID) } returns flowOf(messages)

        val document = useCase(SESSION_ID).getOrThrow()

        assertEquals("Trip planning", document.sessionName)
        val root = JSONObject(document.json)
        assertEquals(SESSION_ID, root.getString("sessionId"))
        assertEquals("Trip planning", root.getString("sessionName"))
        val exported = root.getJSONArray("messages")
        assertEquals(2, exported.length())
        assertEquals("USER", exported.getJSONObject(0).getString("role"))
        assertEquals("Where to?", exported.getJSONObject(0).getString("text"))
        assertEquals(10L, exported.getJSONObject(0).getLong("timestamp"))
        assertEquals("Lisbon.", exported.getJSONObject(1).getString("text"))
    }

    @Test
    fun `given an archived session when invoked then it exports exactly like an active one`() = runTest {
        val archived = session.copy(isArchived = true, archivedAt = 900L)
        coEvery { chatRepository.getSessionById(SESSION_ID) } returns archived
        every { chatRepository.getMessagesForSession(SESSION_ID) } returns flowOf(messages)

        val document = useCase(SESSION_ID).getOrThrow()

        // Archiving decides which list a chat appears in, never what it is made
        // of — so the transfer path must not lose an archived conversation.
        assertEquals("Trip planning", document.sessionName)
        assertEquals(2, JSONObject(document.json).getJSONArray("messages").length())
    }

    @Test
    fun `given an unnamed session when invoked then the fallback name is used`() = runTest {
        coEvery { chatRepository.getSessionById(SESSION_ID) } returns session.copy(name = "  ")
        every { chatRepository.getMessagesForSession(SESSION_ID) } returns flowOf(messages)

        val document = useCase(SESSION_ID).getOrThrow()

        assertEquals(ExportChatUseCase.FALLBACK_SESSION_NAME, document.sessionName)
    }

    @Test
    fun `given a blank id when invoked then fails without touching the repository`() = runTest {
        val result = useCase("   ")

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is IllegalArgumentException)
    }

    @Test
    fun `given a missing session when invoked then fails`() = runTest {
        coEvery { chatRepository.getSessionById(SESSION_ID) } returns null

        val result = useCase(SESSION_ID)

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is NoSuchElementException)
    }

    @Test
    fun `given the repository throws when invoked then the failure is captured`() = runTest {
        coEvery { chatRepository.getSessionById(SESSION_ID) } throws IllegalStateException("db down")

        val result = useCase(SESSION_ID)

        assertTrue(result.isFailure)
        assertEquals("db down", result.exceptionOrNull()?.message)
    }

    @Test(expected = CancellationException::class)
    fun `given the message flow is cancelled when invoked then cancellation propagates`() = runTest {
        coEvery { chatRepository.getSessionById(SESSION_ID) } returns session
        every { chatRepository.getMessagesForSession(SESSION_ID) } returns
            flow { throw CancellationException("cancelled") }

        useCase(SESSION_ID)
    }

    private companion object {
        const val SESSION_ID = "session-1"
    }
}
