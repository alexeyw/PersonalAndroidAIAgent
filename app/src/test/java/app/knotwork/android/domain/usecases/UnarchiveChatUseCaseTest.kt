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
 * Unit tests for [UnarchiveChatUseCase].
 *
 * Mirrors [ArchiveChatUseCaseTest]: the flag must be written as `false`,
 * storage failures arrive as [Result.failure], and cancellation propagates.
 */
class UnarchiveChatUseCaseTest {

    private lateinit var chatRepository: ChatRepository
    private lateinit var useCase: UnarchiveChatUseCase

    @Before
    fun setup() {
        chatRepository = mockk(relaxed = true)
        useCase = UnarchiveChatUseCase(chatRepository)
    }

    @Test
    fun `given a session id when invoked then the archive flag is cleared`() = runTest {
        val result = useCase("session-1")

        assertTrue(result.isSuccess)
        coVerify(exactly = 1) { chatRepository.setSessionArchived("session-1", archived = false) }
    }

    @Test
    fun `given a blank id when invoked then fails without touching the repository`() = runTest {
        val result = useCase("  ")

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

    @Test(expected = CancellationException::class)
    fun `given the coroutine is cancelled when invoked then CancellationException propagates`() = runTest {
        coEvery { chatRepository.setSessionArchived(any(), any()) } throws CancellationException("cancelled")

        useCase("session-1")
    }

    @Test
    fun `given a session that is not archived when invoked then it still reports success`() = runTest {
        val result = useCase("never-archived")

        assertTrue(result.isSuccess)
        coVerify(exactly = 1) { chatRepository.setSessionArchived("never-archived", archived = false) }
    }
}
