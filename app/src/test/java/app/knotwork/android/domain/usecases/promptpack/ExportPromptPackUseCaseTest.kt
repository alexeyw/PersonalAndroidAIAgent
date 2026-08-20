package app.knotwork.android.domain.usecases.promptpack

import app.knotwork.android.domain.models.NodeType
import app.knotwork.android.domain.models.PromptPreset
import app.knotwork.android.domain.repositories.PromptPresetRepository
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/** Tests for rendering a saved prompt into its file form. */
class ExportPromptPackUseCaseTest {

    private lateinit var repository: PromptPresetRepository
    private lateinit var useCase: ExportPromptPackUseCase

    @Before
    fun setUp() {
        repository = mockk()
        useCase = ExportPromptPackUseCase(repository)
    }

    @Test
    fun `given a bundled prompt when exported then it renders like any other`() = runTest {
        // Exporting is a read, and a curated prompt is the likeliest thing
        // someone hands to another person.
        coEvery { repository.getPresetById("report-writer") } returns PromptPreset(
            id = "report-writer",
            name = "Report writer",
            description = "Structured reports.",
            nodeType = NodeType.OUTPUT,
            systemPrompt = "Draft a structured report.",
            tags = listOf("report"),
            isBundled = true,
        )

        val rendered = useCase("report-writer").getOrThrow()

        assertEquals("report-writer.md", rendered.fileName)
        assertEquals("Report writer", rendered.displayName)
        assertTrue(rendered.content.startsWith("---"))
        assertTrue(rendered.content.contains("nodeType: OUTPUT"))
        assertTrue(rendered.content.trimEnd().endsWith("Draft a structured report."))
    }

    @Test
    fun `given a prompt deleted between the tap and the picker when exported then it fails cleanly`() = runTest {
        coEvery { repository.getPresetById("gone") } returns null

        assertTrue(useCase("gone").isFailure)
    }
}
