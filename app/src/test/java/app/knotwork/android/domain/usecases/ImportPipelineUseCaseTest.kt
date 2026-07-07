package app.knotwork.android.domain.usecases

import app.knotwork.android.domain.models.ImportCollisionResolution
import app.knotwork.android.domain.models.PipelineGraph
import app.knotwork.android.domain.models.PipelineImportOutcome
import app.knotwork.android.domain.pipelineio.PipelineJsonSerializer
import app.knotwork.android.domain.repositories.PipelineRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Tests for [ImportPipelineUseCase].
 *
 * Boundaries:
 *
 * - The parsing pipeline lives inside [PipelineJsonSerializer] and is
 *   tested separately. Here we focus on the use case's
 *   parse-then-conditionally-persist orchestration: which outcomes
 *   trigger a save, which do not, and how the save's own [Result] is
 *   propagated.
 */
class ImportPipelineUseCaseTest {

    private lateinit var savePipelineUseCase: SavePipelineUseCase
    private lateinit var pipelineRepository: PipelineRepository
    private lateinit var useCase: ImportPipelineUseCase

    @Before
    fun setup() {
        savePipelineUseCase = mockk()
        pipelineRepository = mockk()
        // Default: no collision — the imported id is free.
        coEvery { pipelineRepository.getPipelineById(any()) } returns null
        useCase = ImportPipelineUseCase(savePipelineUseCase, pipelineRepository)
    }

    /** Minimal valid v1 document covering the happy path. */
    private val validJson = """
        {
          "schemaVersion": 1,
          "id": "p", "name": "demo", "updatedAt": 0,
          "nodes":[
            {"id":"n1","type":"INPUT","position":{"x":0,"y":0},
             "label":"In","config":{},"contextConfig":{}},
            {"id":"n2","type":"OUTPUT","position":{"x":200,"y":0},
             "label":"Out","config":{"systemPrompt":"reply"},"contextConfig":{}}
          ],
          "connections":[
            {"id":"c1","fromNodeId":"n1","toNodeId":"n2","label":null}
          ]
        }
    """.trimIndent()

    /** Same shape as [validJson] but with a future schemaVersion. */
    private val mismatchJson = validJson.replace("\"schemaVersion\": 1", "\"schemaVersion\": 99")

    @Test
    fun `given valid JSON when invoke then save is called and outcome is Success`() = runTest {
        coEvery { savePipelineUseCase(any()) } returns Result.success(Unit)

        val invocation = useCase(validJson)

        assertTrue(invocation.outcome is PipelineImportOutcome.Success)
        assertTrue(invocation.saveResult?.isSuccess == true)
        coVerify(exactly = 1) {
            savePipelineUseCase(match<PipelineGraph> { it.id == "p" && it.nodes.size == 2 })
        }
    }

    @Test
    fun `given schema mismatch when invoke then save is not called`() = runTest {
        val invocation = useCase(mismatchJson)

        assertTrue(invocation.outcome is PipelineImportOutcome.SchemaMismatch)
        assertNull(
            "saveResult should be null when no save was attempted",
            invocation.saveResult,
        )
        coVerify(exactly = 0) { savePipelineUseCase(any()) }
    }

    @Test
    fun `given malformed JSON when invoke then Failure and save is not called`() = runTest {
        val invocation = useCase("{ not json")

        assertTrue(invocation.outcome is PipelineImportOutcome.Failure)
        assertNull(invocation.saveResult)
        coVerify(exactly = 0) { savePipelineUseCase(any()) }
    }

    @Test
    fun `given save failure when invoke then save error is propagated`() = runTest {
        val boom = RuntimeException("disk full")
        coEvery { savePipelineUseCase(any()) } returns Result.failure(boom)

        val invocation = useCase(validJson)

        assertTrue(invocation.outcome is PipelineImportOutcome.Success)
        assertFalse(invocation.saveResult?.isSuccess == true)
        assertEquals(boom, invocation.saveResult?.exceptionOrNull())
    }

    @Test
    fun `persistConfirmed saves the SchemaMismatch graph when its id is free`() = runTest {
        coEvery { savePipelineUseCase(any()) } returns Result.success(Unit)
        val mismatch = useCase(mismatchJson).outcome as PipelineImportOutcome.SchemaMismatch

        val confirmed = useCase.persistConfirmed(mismatch)

        assertTrue(confirmed is ConfirmedImport.Saved)
        assertTrue((confirmed as ConfirmedImport.Saved).result.isSuccess)
        coVerify(exactly = 1) {
            savePipelineUseCase(match<PipelineGraph> { it.id == mismatch.graph.id })
        }
    }

    @Test
    fun `persistConfirmed returns Collision without saving when the id already exists`() = runTest {
        coEvery { pipelineRepository.getPipelineById("p") } returns
            PipelineGraph(id = "p", name = "existing")
        val mismatch = useCase(mismatchJson).outcome as PipelineImportOutcome.SchemaMismatch

        val confirmed = useCase.persistConfirmed(mismatch)

        assertTrue(confirmed is ConfirmedImport.Collision)
        coVerify(exactly = 0) { savePipelineUseCase(any()) }
    }

    @Test
    fun `given id collision when invoke then no save and pendingCollision is set`() = runTest {
        coEvery { pipelineRepository.getPipelineById("p") } returns
            PipelineGraph(id = "p", name = "existing")

        val invocation = useCase(validJson)

        assertTrue(invocation.outcome is PipelineImportOutcome.Success)
        assertNull("no save is attempted on collision", invocation.saveResult)
        assertNotNull("pendingCollision carries the parsed graph", invocation.pendingCollision)
        assertEquals("p", invocation.pendingCollision?.id)
        coVerify(exactly = 0) { savePipelineUseCase(any()) }
    }

    @Test
    fun `given collision when resolve REPLACE then save keeps the original id`() = runTest {
        coEvery { savePipelineUseCase(any()) } returns Result.success(Unit)
        val graph = PipelineGraph(id = "p", name = "demo")

        useCase.persistWithResolution(graph, ImportCollisionResolution.REPLACE)

        coVerify(exactly = 1) { savePipelineUseCase(match<PipelineGraph> { it.id == "p" }) }
    }

    @Test
    fun `given collision when resolve IMPORT_AS_COPY then save uses a fresh id`() = runTest {
        val saved = slot<PipelineGraph>()
        coEvery { savePipelineUseCase(capture(saved)) } returns Result.success(Unit)
        val graph = PipelineGraph(id = "p", name = "demo")

        useCase.persistWithResolution(graph, ImportCollisionResolution.IMPORT_AS_COPY)

        assertFalse("copy must not reuse the colliding id", saved.captured.id == "p")
    }
}
