package ai.agent.android.domain.services

import ai.agent.android.domain.models.ChatMessage
import ai.agent.android.domain.models.Role
import ai.agent.android.domain.repositories.ChatRepository
import ai.agent.android.domain.repositories.SettingsRepository
import ai.agent.android.domain.usecases.MemoryExtractionUseCase
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test

/**
 * Unit tests for [MemoryAutoExtractionCoordinator].
 *
 * The coordinator's scope is replaced with the test scope so the 30 s debounce
 * resolves against the test scheduler's virtual clock.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class MemoryAutoExtractionCoordinatorTest {

    private lateinit var settingsRepository: SettingsRepository
    private lateinit var chatRepository: ChatRepository
    private lateinit var memoryExtractionUseCase: MemoryExtractionUseCase
    private lateinit var coordinator: MemoryAutoExtractionCoordinator

    private val sessionId = "session-1"
    private val messages = listOf(
        ChatMessage(id = 1, sessionId = sessionId, role = Role.USER, content = "Hi", timestamp = 1L),
        ChatMessage(id = 2, sessionId = sessionId, role = Role.AGENT, content = "Hello", timestamp = 2L),
    )

    @Before
    fun setup() {
        settingsRepository = mockk()
        chatRepository = mockk()
        memoryExtractionUseCase = mockk()

        every { settingsRepository.autoExtractEnabled } returns flowOf(true)
        every { chatRepository.getMessagesForSession(sessionId) } returns flowOf(messages)
        coEvery { memoryExtractionUseCase.invoke(any(), any()) } returns
            MemoryExtractionUseCase.MemoryExtractionOutcome.EMPTY

        coordinator = MemoryAutoExtractionCoordinator(
            settingsRepository = settingsRepository,
            chatRepository = chatRepository,
            memoryExtractionUseCase = memoryExtractionUseCase,
        )
    }

    @Test
    fun `given enabled when completed then extracts after the debounce window`() = runTest {
        coordinator.scope = this

        coordinator.onPipelineCompleted(sessionId)
        // Before the debounce elapses nothing runs.
        advanceTimeBy(10_000)
        coVerify(exactly = 0) { memoryExtractionUseCase.invoke(any(), any()) }

        advanceUntilIdle()
        coVerify(exactly = 1) { memoryExtractionUseCase.invoke(sessionId, messages) }
    }

    @Test
    fun `given disabled when completed then never extracts`() = runTest {
        every { settingsRepository.autoExtractEnabled } returns flowOf(false)
        coordinator.scope = this

        coordinator.onPipelineCompleted(sessionId)
        advanceUntilIdle()

        coVerify(exactly = 0) { memoryExtractionUseCase.invoke(any(), any()) }
    }

    @Test
    fun `given rapid completions when within debounce then coalesces into a single extraction`() = runTest {
        coordinator.scope = this

        coordinator.onPipelineCompleted(sessionId)
        advanceTimeBy(5_000)
        coordinator.onPipelineCompleted(sessionId)
        advanceTimeBy(5_000)
        coordinator.onPipelineCompleted(sessionId)
        advanceUntilIdle()

        coVerify(exactly = 1) { memoryExtractionUseCase.invoke(sessionId, messages) }
    }

    @Test
    fun `given blank session id when completed then no-op`() = runTest {
        coordinator.scope = this

        coordinator.onPipelineCompleted("")
        advanceUntilIdle()

        coVerify(exactly = 0) { memoryExtractionUseCase.invoke(any(), any()) }
    }
}
