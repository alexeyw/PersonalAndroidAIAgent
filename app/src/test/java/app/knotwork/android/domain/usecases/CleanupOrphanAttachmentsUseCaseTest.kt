package app.knotwork.android.domain.usecases

import app.knotwork.android.domain.repositories.ChatRepository
import app.knotwork.android.domain.services.AttachmentStore
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

/**
 * Unit tests for [CleanupOrphanAttachmentsUseCase] — the backstop sweep that
 * deletes attachment files no chat message references.
 */
class CleanupOrphanAttachmentsUseCaseTest {

    private lateinit var chatRepository: ChatRepository
    private lateinit var attachmentStore: AttachmentStore
    private lateinit var useCase: CleanupOrphanAttachmentsUseCase

    @Before
    fun setup() {
        chatRepository = mockk(relaxed = true)
        attachmentStore = mockk(relaxed = true)
        useCase = CleanupOrphanAttachmentsUseCase(chatRepository, attachmentStore)
    }

    @Test
    fun `given unreferenced files when invoked then only orphans are deleted`() = runTest {
        coEvery { attachmentStore.listStoredPaths() } returns Result.success(listOf("a.jpg", "b.jpg", "c.jpg"))
        coEvery { chatRepository.getReferencedAttachmentPaths() } returns listOf("b.jpg")
        coEvery { attachmentStore.delete(any()) } returns Result.success(Unit)

        val deleted = useCase()

        assertEquals(2, deleted)
        coVerify(exactly = 1) { attachmentStore.delete("a.jpg") }
        coVerify(exactly = 1) { attachmentStore.delete("c.jpg") }
        coVerify(exactly = 0) { attachmentStore.delete("b.jpg") }
    }

    @Test
    fun `given all files referenced when invoked then nothing is deleted`() = runTest {
        coEvery { attachmentStore.listStoredPaths() } returns Result.success(listOf("a.jpg", "b.jpg"))
        coEvery { chatRepository.getReferencedAttachmentPaths() } returns listOf("a.jpg", "b.jpg")

        val deleted = useCase()

        assertEquals(0, deleted)
        coVerify(exactly = 0) { attachmentStore.delete(any()) }
    }

    @Test
    fun `given empty store when invoked then references are never queried`() = runTest {
        coEvery { attachmentStore.listStoredPaths() } returns Result.success(emptyList())

        val deleted = useCase()

        assertEquals(0, deleted)
        coVerify(exactly = 0) { chatRepository.getReferencedAttachmentPaths() }
    }

    @Test
    fun `given store listing fails when invoked then zero is returned`() = runTest {
        coEvery { attachmentStore.listStoredPaths() } returns Result.failure(RuntimeException("io"))

        val deleted = useCase()

        assertEquals(0, deleted)
        coVerify(exactly = 0) { attachmentStore.delete(any()) }
    }

    @Test
    fun `given a delete fails when invoked then it is not counted`() = runTest {
        coEvery { attachmentStore.listStoredPaths() } returns Result.success(listOf("a.jpg", "b.jpg"))
        coEvery { chatRepository.getReferencedAttachmentPaths() } returns emptyList()
        coEvery { attachmentStore.delete("a.jpg") } returns Result.success(Unit)
        coEvery { attachmentStore.delete("b.jpg") } returns Result.failure(RuntimeException("locked"))

        val deleted = useCase()

        assertEquals(1, deleted)
    }
}
