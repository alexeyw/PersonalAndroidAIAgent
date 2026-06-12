package app.knotwork.android.data.repositories

import app.knotwork.android.data.local.dao.PendingInteractionDao
import app.knotwork.android.data.local.models.PendingInteractionEntity
import app.knotwork.android.domain.models.PendingDecision
import app.knotwork.android.domain.models.PendingInteraction
import app.knotwork.android.domain.models.PendingInteractionKind
import app.knotwork.android.domain.models.ToolRisk
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Unit tests for [PendingInteractionRepositoryImpl].
 *
 * Cover: domain ↔ entity mapping in both directions (enum names, options as a
 * JSON array string), the explicit save-success contract, the
 * first-writer-wins result mapping of the guarded response writes, and the
 * best-effort absorption of storage failures. Robolectric runner because the
 * options mapping uses `org.json`, which is stubbed on the plain JVM.
 */
@RunWith(RobolectricTestRunner::class)
class PendingInteractionRepositoryImplTest {

    private lateinit var dao: PendingInteractionDao
    private lateinit var repository: PendingInteractionRepositoryImpl

    @Before
    fun setup() {
        dao = mockk(relaxed = true)
        repository = PendingInteractionRepositoryImpl(dao)
    }

    /** A full approval-kind domain record exercising every mapped field. */
    private fun approvalRecord(): PendingInteraction = PendingInteraction(
        runId = "run-1",
        sessionId = "session-1",
        kind = PendingInteractionKind.APPROVAL,
        toolName = "send_email",
        toolArgs = "{\"to\":\"a@b.c\"}",
        risk = ToolRisk.DESTRUCTIVE,
        decision = PendingDecision.APPROVED,
        requestedAt = 42L,
    )

    /** The persisted entity of a clarification park with options. */
    private fun clarificationEntity(): PendingInteractionEntity = PendingInteractionEntity(
        runId = "run-2",
        sessionId = "session-2",
        kind = "CLARIFICATION",
        toolName = null,
        toolArgs = null,
        risk = null,
        question = "Pick one",
        optionsJson = "[\"red\",\"blue\"]",
        decision = null,
        answer = "red",
        requestedAt = 7L,
    )

    @Test
    fun `save maps the domain record onto the entity and reports success`() = runTest {
        val captured = slot<PendingInteractionEntity>()
        coEvery { dao.upsert(capture(captured)) } returns Unit

        assertTrue(repository.save(approvalRecord()))

        with(captured.captured) {
            assertEquals("run-1", runId)
            assertEquals("APPROVAL", kind)
            assertEquals("send_email", toolName)
            assertEquals("DESTRUCTIVE", risk)
            assertEquals("APPROVED", decision)
            assertEquals(42L, requestedAt)
            assertNull(optionsJson)
        }
    }

    @Test
    fun `save maps answer options to a JSON array string`() = runTest {
        val captured = slot<PendingInteractionEntity>()
        coEvery { dao.upsert(capture(captured)) } returns Unit

        repository.save(
            PendingInteraction(
                runId = "run-2",
                sessionId = "session-2",
                kind = PendingInteractionKind.CLARIFICATION,
                question = "Pick one",
                options = listOf("red", "blue"),
                requestedAt = 7L,
            ),
        )

        assertEquals("[\"red\",\"blue\"]", captured.captured.optionsJson)
    }

    @Test
    fun `given DAO failure when save then reports failure instead of throwing`() = runTest {
        coEvery { dao.upsert(any()) } throws IllegalStateException("io")

        assertFalse(repository.save(approvalRecord()))
    }

    @Test
    fun `getForRun maps the entity back to the domain record`() = runTest {
        coEvery { dao.getForRun("run-2") } returns clarificationEntity()

        val record = repository.getForRun("run-2")

        assertEquals(PendingInteractionKind.CLARIFICATION, record!!.kind)
        assertEquals("Pick one", record.question)
        assertEquals(listOf("red", "blue"), record.options)
        assertEquals("red", record.answer)
        assertNull(record.decision)
    }

    @Test
    fun `given corrupted enum column when getForRun then degrades to null`() = runTest {
        coEvery { dao.getForRun("run-2") } returns clarificationEntity().copy(kind = "BOGUS")

        assertNull(repository.getForRun("run-2"))
    }

    @Test
    fun `getForSession maps the latest parked record of the session`() = runTest {
        coEvery { dao.getForSession("session-2") } returns clarificationEntity()

        assertEquals("run-2", repository.getForSession("session-2")!!.runId)
    }

    @Test
    fun `recordDecision maps the guarded update count to first-writer-wins`() = runTest {
        coEvery { dao.recordDecision("run-1", "DENIED") } returns 1
        assertTrue(repository.recordDecision("run-1", PendingDecision.DENIED))

        coEvery { dao.recordDecision("run-1", "DENIED") } returns 0
        assertFalse(repository.recordDecision("run-1", PendingDecision.DENIED))
    }

    @Test
    fun `recordAnswer maps the guarded update count to first-writer-wins`() = runTest {
        coEvery { dao.recordAnswer("run-2", "red") } returns 1
        assertTrue(repository.recordAnswer("run-2", "red"))

        coEvery { dao.recordAnswer("run-2", "red") } returns 0
        assertFalse(repository.recordAnswer("run-2", "red"))
    }

    @Test
    fun `delete forwards to the DAO and absorbs failures`() = runTest {
        repository.delete("run-1")
        coVerify { dao.delete("run-1") }

        coEvery { dao.delete("run-1") } throws IllegalStateException("io")
        repository.delete("run-1")
    }

    @Test
    fun `getRequestedAtOrBefore maps rows and degrades to empty on failure`() = runTest {
        coEvery { dao.getRequestedAtOrBefore(100L) } returns listOf(clarificationEntity())
        assertEquals(listOf("run-2"), repository.getRequestedAtOrBefore(100L).map { it.runId })

        coEvery { dao.getRequestedAtOrBefore(100L) } throws IllegalStateException("io")
        assertEquals(emptyList<PendingInteraction>(), repository.getRequestedAtOrBefore(100L))
    }

    @Test
    fun `getAllRunIds returns the id set and degrades to empty on failure`() = runTest {
        coEvery { dao.getAllRunIds() } returns listOf("run-1", "run-2")
        assertEquals(setOf("run-1", "run-2"), repository.getAllRunIds())

        coEvery { dao.getAllRunIds() } throws IllegalStateException("io")
        assertEquals(emptySet<String>(), repository.getAllRunIds())
    }
}
