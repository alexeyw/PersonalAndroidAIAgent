package app.knotwork.android.domain.usecases.promptpack

import app.knotwork.android.domain.models.NodeType
import app.knotwork.android.domain.models.PromptPackParseError
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

/**
 * Tests for [ImportPromptPackUseCase] — the three things the parser
 * deliberately leaves to the caller: where an id comes from, what happens
 * when it is taken, and that an imported file can never claim the read-only
 * catalogue.
 */
class ImportPromptPackUseCaseTest {

    private lateinit var repository: PromptPresetRepository
    private lateinit var useCase: ImportPromptPackUseCase

    private val document = """
        ---
        name: Concise assistant
        nodeType: LITE_RT
        ---

        You are concise.
    """.trimIndent()

    @Before
    fun setUp() {
        repository = mockk(relaxed = true)
        coEvery { repository.getPresetById(any()) } returns null
        useCase = ImportPromptPackUseCase(repository)
    }

    private fun saved(): PromptPreset {
        val captured = slot<PromptPreset>()
        coVerify { repository.saveUserPreset(capture(captured)) }
        return captured.captured
    }

    @Test
    fun `given a file with no id when imported then the file name stem becomes the id`() = runTest {
        useCase(document = document, fileName = "Standup Digest.md")

        assertEquals("standup-digest", saved().id)
    }

    @Test
    fun `given a file name with no usable characters when imported then a unique id is generated`() = runTest {
        // An empty stem would collide with every other empty stem, quietly
        // overwriting an unrelated prompt on the next such import.
        useCase(document = document, fileName = "★★★.md")

        assertTrue(saved().id.isNotEmpty())
        assertNotEquals("", saved().id)
    }

    @Test
    fun `given a clean file when imported then it is saved as a user preset`() = runTest {
        val result = useCase(document = document, fileName = "a.md")

        assertTrue(result is PromptPackImportResult.Imported)
        assertFalse(saved().isBundled)
        assertEquals(NodeType.LITE_RT, saved().nodeType)
        assertEquals("You are concise.", saved().systemPrompt)
    }

    @Test
    fun `given a file whose id belongs to a bundled preset when imported then it is re-keyed`() = runTest {
        // `getPresetById` resolves bundled-first, so a user preset saved under
        // a bundled id would be permanently unreachable.
        coEvery { repository.getPresetById("summarizer") } returns PromptPreset(
            id = "summarizer",
            name = "Summarizer",
            description = "",
            nodeType = NodeType.SUMMARY,
            systemPrompt = "Summarize.",
            isBundled = true,
        )

        val result = useCase(document = document, fileName = "summarizer.md")

        assertTrue(result is PromptPackImportResult.Imported)
        assertNotEquals("summarizer", saved().id)
        assertFalse(saved().isBundled)
    }

    @Test
    fun `given a file identical to a saved prompt when imported then nothing is written`() = runTest {
        coEvery { repository.getPresetById("a") } returns PromptPreset(
            id = "a",
            name = "Concise assistant",
            description = "",
            nodeType = NodeType.LITE_RT,
            systemPrompt = "You are concise.",
            tags = emptyList(),
            isBundled = false,
        )

        val result = useCase(document = document, fileName = "a.md")

        assertTrue(result is PromptPackImportResult.Unchanged)
        coVerify(exactly = 0) { repository.saveUserPreset(any()) }
    }

    @Test
    fun `given a file differing from a saved prompt when imported then the user is asked`() = runTest {
        coEvery { repository.getPresetById("a") } returns PromptPreset(
            id = "a",
            name = "Concise assistant",
            description = "",
            nodeType = NodeType.LITE_RT,
            systemPrompt = "An edit the user made in the app.",
            isBundled = false,
        )

        val result = useCase(document = document, fileName = "a.md")

        assertTrue(result is PromptPackImportResult.NeedsDecision)
        // Nothing is written before the answer: a silent replace here is what
        // destroys the in-app edit.
        coVerify(exactly = 0) { repository.saveUserPreset(any()) }
    }

    @Test
    fun `given an unreadable file when imported then the cause is carried out and nothing is written`() = runTest {
        val result = useCase(document = "no frontmatter here", fileName = "a.md")

        assertTrue(result is PromptPackImportResult.Failed)
        assertTrue((result as PromptPackImportResult.Failed).cause is PromptPackParseError.MalformedFrontmatter)
        coVerify(exactly = 0) { repository.saveUserPreset(any()) }
    }

    @Test
    fun `given the id lookup throws when imported then the import proceeds instead of overwriting blindly`() = runTest {
        coEvery { repository.getPresetById(any()) } throws IllegalStateException("db down")

        val result = useCase(document = document, fileName = "a.md")

        assertTrue(result is PromptPackImportResult.Imported)
    }

    @Test
    fun `given a file asking for tools when imported then the refusal reaches the caller`() = runTest {
        val greedy = """
            ---
            name: Web summarizer
            nodeType: CLOUD
            allowed-tools: web_search
            ---

            Summarize the page.
        """.trimIndent()

        val result = useCase(document = greedy, fileName = "a.md") as PromptPackImportResult.Imported

        assertTrue(result.notes?.hasRefusal == true)
        // And the prompt itself still landed — a refusal is not a rejection.
        assertEquals("Summarize the page.", saved().systemPrompt)
    }
}
