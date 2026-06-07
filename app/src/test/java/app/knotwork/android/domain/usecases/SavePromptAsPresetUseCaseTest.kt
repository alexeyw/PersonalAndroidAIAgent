package app.knotwork.android.domain.usecases

import app.knotwork.android.domain.constants.PromptPresetConstants
import app.knotwork.android.domain.models.NodeType
import app.knotwork.android.domain.models.PromptPreset
import app.knotwork.android.domain.repositories.PromptPresetRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class SavePromptAsPresetUseCaseTest {

    private lateinit var repository: PromptPresetRepository
    private lateinit var useCase: SavePromptAsPresetUseCase

    @Before
    fun setup() {
        repository = mockk(relaxed = true)
        useCase = SavePromptAsPresetUseCase(repository)
    }

    @Test
    fun `given valid inputs when invoke then persists preset and returns its id`() = runTest {
        val captured = slot<PromptPreset>()
        coEvery { repository.saveUserPreset(capture(captured)) } returns Unit

        val result = useCase(
            systemPrompt = "  You are concise.  ",
            name = "  My preset  ",
            description = "  desc  ",
            nodeType = NodeType.LITE_RT,
            tags = listOf("a", "b"),
        )

        assertTrue(result.isSuccess)
        coVerify(exactly = 1) { repository.saveUserPreset(any()) }
        val saved = captured.captured
        assertEquals("My preset", saved.name)
        assertEquals("desc", saved.description)
        assertEquals("You are concise.", saved.systemPrompt)
        assertEquals(NodeType.LITE_RT, saved.nodeType)
        assertEquals(listOf("a", "b"), saved.tags)
        assertFalse(saved.isBundled)
        assertEquals(saved.id, result.getOrNull())
    }

    @Test
    fun `given blank name when invoke then returns failure with IllegalArgumentException`() = runTest {
        val result = useCase(
            systemPrompt = "ok",
            name = "   ",
            description = "",
            nodeType = NodeType.LITE_RT,
        )

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is IllegalArgumentException)
        coVerify(exactly = 0) { repository.saveUserPreset(any()) }
    }

    @Test
    fun `given name exceeding MAX_NAME_LENGTH when invoke then returns failure`() = runTest {
        val tooLong = "x".repeat(PromptPresetConstants.MAX_NAME_LENGTH + 1)

        val result = useCase(
            systemPrompt = "ok",
            name = tooLong,
            description = "",
            nodeType = NodeType.LITE_RT,
        )

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is IllegalArgumentException)
    }

    @Test
    fun `given blank systemPrompt when invoke then returns failure`() = runTest {
        val result = useCase(
            systemPrompt = "   ",
            name = "ok",
            description = "",
            nodeType = NodeType.LITE_RT,
        )

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is IllegalArgumentException)
    }

    @Test
    fun `given systemPrompt exceeding MAX_SYSTEM_PROMPT_LENGTH when invoke then returns failure`() = runTest {
        val tooLong = "x".repeat(PromptPresetConstants.MAX_SYSTEM_PROMPT_LENGTH + 1)

        val result = useCase(
            systemPrompt = tooLong,
            name = "ok",
            description = "",
            nodeType = NodeType.LITE_RT,
        )

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is IllegalArgumentException)
    }

    @Test
    fun `given non-LLM nodeType when invoke then returns failure`() = runTest {
        val result = useCase(
            systemPrompt = "ok",
            name = "ok",
            description = "",
            nodeType = NodeType.TOOL,
        )

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is IllegalArgumentException)
        coVerify(exactly = 0) { repository.saveUserPreset(any()) }
    }

    @Test
    fun `given tags with blanks and duplicates when invoke then they are normalised`() = runTest {
        val captured = slot<PromptPreset>()
        coEvery { repository.saveUserPreset(capture(captured)) } returns Unit

        useCase(
            systemPrompt = "ok",
            name = "ok",
            description = "",
            nodeType = NodeType.OUTPUT,
            tags = listOf("  a  ", "", "b", "a", "  "),
        )

        assertEquals(listOf("a", "b"), captured.captured.tags)
    }

    @Test
    fun `given repository throws when invoke then returns failure wrapping the exception`() = runTest {
        coEvery { repository.saveUserPreset(any()) } throws RuntimeException("disk full")

        val result = useCase(
            systemPrompt = "ok",
            name = "ok",
            description = "",
            nodeType = NodeType.OUTPUT,
        )

        assertTrue(result.isFailure)
        assertEquals("disk full", result.exceptionOrNull()?.message)
    }

    @Test
    fun `given existingId when invoke then persists preset with that id (upsert)`() = runTest {
        val captured = slot<PromptPreset>()
        coEvery { repository.saveUserPreset(capture(captured)) } returns Unit

        val result = useCase(
            systemPrompt = "updated",
            name = "Reused",
            description = "",
            nodeType = NodeType.LITE_RT,
            existingId = "fixed-id-123",
        )

        assertTrue(result.isSuccess)
        assertEquals("fixed-id-123", captured.captured.id)
    }

    @Test
    fun `given blank existingId when invoke then falls back to a fresh UUID`() = runTest {
        val captured = slot<PromptPreset>()
        coEvery { repository.saveUserPreset(capture(captured)) } returns Unit

        useCase(
            systemPrompt = "ok",
            name = "ok",
            description = "",
            nodeType = NodeType.LITE_RT,
            existingId = "   ",
        )

        // Blank ids must not be persisted — they would collide on insert and would
        // also bypass our intent of "create new with fresh UUID".
        assertTrue(captured.captured.id.isNotBlank())
        assertNotEquals("   ", captured.captured.id)
    }
}
