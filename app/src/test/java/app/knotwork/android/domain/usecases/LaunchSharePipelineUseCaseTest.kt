package app.knotwork.android.domain.usecases

import app.knotwork.android.domain.constants.DefaultPrompts
import app.knotwork.android.domain.models.ChatSession
import app.knotwork.android.domain.models.MessageAttachment
import app.knotwork.android.domain.models.RunOrigin
import app.knotwork.android.domain.models.SharedPayload
import app.knotwork.android.domain.repositories.ChatRepository
import app.knotwork.android.domain.services.AttachmentStore
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for [LaunchSharePipelineUseCase]: the empty / unbound guards and
 * the text-only, image-only and failed-ingest launch branches.
 */
class LaunchSharePipelineUseCaseTest {

    private val resolveSurfacePipeline = mockk<ResolveSurfacePipelineUseCase>()
    private val chatRepository = mockk<ChatRepository>(relaxed = true)
    private val attachmentStore = mockk<AttachmentStore>()
    private val orchestrator = mockk<AgentOrchestratorUseCase>(relaxed = true)
    private val useCase =
        LaunchSharePipelineUseCase(resolveSurfacePipeline, chatRepository, attachmentStore, orchestrator)

    private suspend fun launch(payload: SharedPayload): ShareLaunchResult =
        useCase(payload, imageSessionName = "Shared image", contentSessionName = "Shared content")

    @Test
    fun `given empty payload when invoked then reports NothingShared`() = runTest {
        val result = launch(SharedPayload(text = null, imageUri = null))

        assertEquals(ShareLaunchResult.NothingShared, result)
    }

    @Test
    fun `given no bound share pipeline when invoked then reports NotConfigured`() = runTest {
        coEvery { resolveSurfacePipeline(any()) } returns null

        val result = launch(SharedPayload(text = "hi", imageUri = null))

        assertEquals(ShareLaunchResult.NotConfigured, result)
    }

    @Test
    fun `given text share when invoked then creates a bound session and enqueues a SHARE run`() = runTest {
        coEvery { resolveSurfacePipeline(any()) } returns "share-pipe"
        val sessionSlot = slot<ChatSession>()
        coEvery { chatRepository.saveSession(capture(sessionSlot)) } returns Unit

        val result = launch(SharedPayload(text = "summarise this", imageUri = null))

        val sessionId = (result as ShareLaunchResult.Launched).sessionId
        assertEquals(sessionId, sessionSlot.captured.id)
        assertEquals("share-pipe", sessionSlot.captured.pipelineId)
        coVerify {
            orchestrator(
                sessionId = sessionId,
                userPrompt = "summarise this",
                pipelineId = "share-pipe",
                attachment = null,
                displayContent = null,
                origin = RunOrigin.SHARE,
            )
        }
    }

    @Test
    fun `given image-only share when invoked then uses the image-only instruction with empty display`() = runTest {
        coEvery { resolveSurfacePipeline(any()) } returns "share-pipe"
        val attachment = MessageAttachment(path = "img.jpg", mimeType = "image/jpeg", width = 100, height = 80)
        coEvery { attachmentStore.ingestUri("content://media/1") } returns Result.success(attachment)

        val result = launch(SharedPayload(text = null, imageUri = "content://media/1"))

        val sessionId = (result as ShareLaunchResult.Launched).sessionId
        coVerify {
            orchestrator(
                sessionId = sessionId,
                userPrompt = DefaultPrompts.IMAGE_ONLY_DEFAULT_INSTRUCTION,
                pipelineId = "share-pipe",
                attachment = attachment,
                displayContent = "",
                origin = RunOrigin.SHARE,
            )
        }
    }

    @Test
    fun `given image-only share whose ingest fails when invoked then reports NothingShared`() = runTest {
        coEvery { resolveSurfacePipeline(any()) } returns "share-pipe"
        coEvery { attachmentStore.ingestUri(any()) } returns Result.failure(IllegalStateException("bad"))

        val result = launch(SharedPayload(text = null, imageUri = "content://media/1"))

        assertTrue(result is ShareLaunchResult.NothingShared)
    }
}
