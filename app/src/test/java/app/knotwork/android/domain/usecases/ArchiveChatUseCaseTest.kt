package app.knotwork.android.domain.usecases

import app.knotwork.android.domain.repositories.ChatRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Unit tests for [ArchiveChatUseCase].
 *
 * The use case is the single entry point the UI uses to archive a chat, so the
 * contract under test is: the archive flag is written as `true`, storage
 * failures arrive as [Result.failure] instead of escaping, and cancellation is
 * never swallowed into a failed [Result].
 */
class ArchiveChatUseCaseTest {

    private lateinit var chatRepository: ChatRepository
    private lateinit var useCase: ArchiveChatUseCase

    @Before
    fun setup() {
        chatRepository = mockk(relaxed = true)
        useCase = ArchiveChatUseCase(chatRepository)
    }

    @Test
    fun `given a session id when invoked then the archive flag is set to true`() = runTest {
        val result = useCase("session-1")

        assertTrue(result.isSuccess)
        coVerify(exactly = 1) { chatRepository.setSessionArchived("session-1", archived = true) }
    }

    @Test
    fun `given a blank id when invoked then fails without touching the repository`() = runTest {
        val result = useCase("   ")

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is IllegalArgumentException)
        coVerify(exactly = 0) { chatRepository.setSessionArchived(any(), any()) }
    }

    @Test
    fun `given an empty id when invoked then fails without touching the repository`() = runTest {
        val result = useCase("")

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is IllegalArgumentException)
        coVerify(exactly = 0) { chatRepository.setSessionArchived(any(), any()) }
    }

    @Test
    fun `given the repository throws when invoked then the error is wrapped in a failed Result`() = runTest {
        val boom = IllegalStateException("database is closed")
        coEvery { chatRepository.setSessionArchived(any(), any()) } throws boom

        val result = useCase("session-1")

        assertTrue(result.isFailure)
        assertEquals(boom, result.exceptionOrNull())
    }

    /**
     * Cancellation must propagate, not be captured as a failed [Result] — a
     * swallowed [CancellationException] leaves the calling coroutine believing
     * it is still alive (see the coroutine-cancellation gate).
     */
    @Test(expected = CancellationException::class)
    fun `given the coroutine is cancelled when invoked then CancellationException propagates`() = runTest {
        coEvery { chatRepository.setSessionArchived(any(), any()) } throws CancellationException("cancelled")

        useCase("session-1")
    }

    @Test
    fun `given an already archived session when invoked again then it still reports success`() = runTest {
        val first = useCase("session-1")
        val second = useCase("session-1")

        assertTrue(first.isSuccess)
        assertTrue(second.isSuccess)
        coVerify(exactly = 2) { chatRepository.setSessionArchived("session-1", archived = true) }
    }
}
