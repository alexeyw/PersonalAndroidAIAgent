package ai.agent.android.domain.usecases

import ai.agent.android.domain.models.ChatMessage
import ai.agent.android.domain.models.Role
import ai.agent.android.domain.repositories.ChatRepository
import ai.agent.android.domain.repositories.SettingsRepository
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

/**
 * Unit tests for [GetContextWindowUseCase].
 */
class GetContextWindowUseCaseTest {

    private lateinit var chatRepository: ChatRepository
    private lateinit var settingsRepository: SettingsRepository
    private lateinit var getContextWindowUseCase: GetContextWindowUseCase

    @Before
    fun setup() {
        chatRepository = mockk()
        settingsRepository = mockk()
        getContextWindowUseCase = GetContextWindowUseCase(chatRepository, settingsRepository)
    }

    @Test
    fun `invoke returns formatted messages when total length is under limit`() = runTest {
        val sessionId = "session1"
        val messages = listOf(
            ChatMessage(1, sessionId, Role.USER, "Hello", 1000),
            ChatMessage(2, sessionId, Role.AGENT, "Hi there", 2000),
        )
        // USER: Hello -> 11 chars
        // AGENT: Hi there -> 15 chars
        // Total with newline: 11 + 1 + 15 = 27 chars

        coEvery { chatRepository.getMessagesForSession(sessionId) } returns flowOf(messages)
        coEvery { settingsRepository.maxContextLength } returns flowOf(100) // generous limit

        val result = getContextWindowUseCase(sessionId)

        val expected = "USER: Hello\nAGENT: Hi there"
        assertEquals(expected, result)
    }

    @Test
    fun `invoke truncates older messages when total length exceeds limit`() = runTest {
        val sessionId = "session1"
        val messages = listOf(
            ChatMessage(1, sessionId, Role.USER, "First message", 1000), // USER: First message -> 19
            ChatMessage(2, sessionId, Role.AGENT, "Second message", 2000), // AGENT: Second message -> 21
            ChatMessage(3, sessionId, Role.USER, "Third message", 3000), // USER: Third message -> 19
        )

        coEvery { chatRepository.getMessagesForSession(sessionId) } returns flowOf(messages)
        // Last two messages = 21 + 1 (newline) + 19 = 41 chars; all three = 61 chars.
        // Budget 12 tokens × CHARS_PER_TOKEN(4) = 48 chars → keep the last two, drop the first.
        coEvery { settingsRepository.maxContextLength } returns flowOf(12)

        val result = getContextWindowUseCase(sessionId)

        val expected = "AGENT: Second message\nUSER: Third message"
        assertEquals(expected, result)
    }

    @Test
    fun `invoke handles empty message list`() = runTest {
        val sessionId = "session1"
        coEvery { chatRepository.getMessagesForSession(sessionId) } returns flowOf(emptyList())
        coEvery { settingsRepository.maxContextLength } returns flowOf(100)

        val result = getContextWindowUseCase(sessionId)

        assertEquals("", result)
    }

    @Test
    fun `invoke includes only the latest message if it barely fits`() = runTest {
        val sessionId = "session1"
        val messages = listOf(
            ChatMessage(1, sessionId, Role.USER, "Short", 1000),
            ChatMessage(2, sessionId, Role.AGENT, "This is a very long message indeed", 2000),
        )
        // AGENT: This is a very long message indeed -> 41 chars;
        // including the older "Short" -> 41 + 1 + 12 = 54 chars.

        coEvery { chatRepository.getMessagesForSession(sessionId) } returns flowOf(messages)
        // Budget 11 tokens × 4 = 44 chars → only the latest message fits (54 > 44).
        coEvery { settingsRepository.maxContextLength } returns flowOf(11)

        val result = getContextWindowUseCase(sessionId)

        val expected = "AGENT: This is a very long message indeed"
        assertEquals(expected, result)
    }

    @Test
    fun `invoke returns empty if even the latest message exceeds limit`() = runTest {
        val sessionId = "session1"
        val messages = listOf(
            ChatMessage(1, sessionId, Role.USER, "Short", 1000),
            ChatMessage(2, sessionId, Role.AGENT, "This is a very long message indeed", 2000),
        )
        // AGENT: This is a very long message indeed -> 41 chars

        coEvery { chatRepository.getMessagesForSession(sessionId) } returns flowOf(messages)
        // Budget 10 tokens × 4 = 40 chars < 41 → nothing fits.
        coEvery { settingsRepository.maxContextLength } returns flowOf(10)

        val result = getContextWindowUseCase(sessionId)

        assertEquals("", result)
    }

    @Test
    fun `invoke treats maxContextLength as a token budget not a character budget`() = runTest {
        // Regression: a 41-character formatted message kept under an
        // 11-"length" budget proves the budget is interpreted as tokens
        // (11 × CHARS_PER_TOKEN = 44 chars) rather than characters — the old
        // behaviour compared the 41-char message against the bare value 11 and
        // dropped it, truncating history to ~1/4 of the intended window.
        val sessionId = "session1"
        val messages = listOf(
            ChatMessage(1, sessionId, Role.AGENT, "This is a very long message indeed", 1000),
        )
        coEvery { chatRepository.getMessagesForSession(sessionId) } returns flowOf(messages)
        coEvery { settingsRepository.maxContextLength } returns flowOf(11)

        val result = getContextWindowUseCase(sessionId)

        assertEquals("AGENT: This is a very long message indeed", result)
    }
}
