package app.knotwork.android.data.repositories

import androidx.room.Room
import app.knotwork.android.data.local.AppDatabase
import app.knotwork.android.data.local.dao.TriggerJournalDao
import app.knotwork.android.data.local.models.TriggerEvaluationEntity
import app.knotwork.android.domain.models.TriggerEvaluation
import app.knotwork.android.domain.models.TriggerEvaluationSource
import app.knotwork.android.domain.models.TriggerEvaluationVerdict
import app.knotwork.android.domain.models.TriggerRunOutcome
import app.knotwork.android.domain.models.TriggerSkipReason
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

/**
 * Verifies [TriggerJournalRepositoryImpl] against a real in-memory Room database:
 * the verdict / source / run-outcome mapping round-trips, the two-phase outcome
 * write, newest-first ordering, retention, and the tolerant decode that drops a
 * corrupt row. A second section drives the best-effort contract with a mocked DAO.
 */
@RunWith(RobolectricTestRunner::class)
class TriggerJournalRepositoryImplTest {

    private lateinit var database: AppDatabase
    private lateinit var dao: TriggerJournalDao

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(RuntimeEnvironment.getApplication(), AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        dao = database.triggerJournalDao()
    }

    @After
    fun tearDown() {
        database.close()
    }

    private fun repository(realDao: TriggerJournalDao = dao): TriggerJournalRepositoryImpl =
        TriggerJournalRepositoryImpl(realDao).apply { dispatcher = Dispatchers.Unconfined }

    private fun evaluation(
        id: String,
        triggerId: String = "trig-1",
        evaluatedAt: Long = 0L,
        source: TriggerEvaluationSource = TriggerEvaluationSource.POLL,
        verdict: TriggerEvaluationVerdict = TriggerEvaluationVerdict.Fired,
        runId: String? = null,
        outcome: TriggerRunOutcome? = null,
    ) = TriggerEvaluation(id, triggerId, evaluatedAt, source, verdict, runId, outcome)

    @Test
    fun `given every verdict and source when recorded then it round-trips through the store`() = runTest {
        val repo = repository()
        val records = listOf(
            evaluation(
                "a",
                evaluatedAt = 1,
                source = TriggerEvaluationSource.POLL,
                verdict = TriggerEvaluationVerdict.Fired,
                runId = "run-a",
            ),
            evaluation(
                "b",
                evaluatedAt = 2,
                source = TriggerEvaluationSource.EVENT,
                verdict = TriggerEvaluationVerdict.ReArmed,
            ),
            evaluation(
                "c",
                evaluatedAt = 3,
                source = TriggerEvaluationSource.CHARGING_SWEEP,
                verdict = TriggerEvaluationVerdict.Skipped(TriggerSkipReason.CONDITION_NOT_MET),
            ),
        )
        records.forEach { repo.recordEvaluation(it) }

        val stored = repo.observeByTrigger("trig-1").first()

        // Newest-first ordering by evaluatedAt.
        assertEquals(listOf("c", "b", "a"), stored.map { it.id })
        assertEquals(records.reversed().toSet(), stored.toSet())
    }

    @Test
    fun `given a fired evaluation when a run outcome is recorded then it attaches to that row`() = runTest {
        val repo = repository()
        repo.recordEvaluation(evaluation("x", verdict = TriggerEvaluationVerdict.Fired, runId = "run-x"))

        repo.recordRunOutcome("run-x", TriggerRunOutcome.Failure("network down"))

        val stored = repo.observeByTrigger("trig-1").first().single()
        assertEquals(TriggerRunOutcome.Failure("network down"), stored.outcome)
    }

    @Test
    fun `given each run outcome kind when recorded then it round-trips`() = runTest {
        val repo = repository()
        val cases = mapOf(
            "run-s" to TriggerRunOutcome.Success,
            "run-c" to TriggerRunOutcome.CancelledBySystem,
            "run-u" to TriggerRunOutcome.Cancelled,
            "run-h" to TriggerRunOutcome.HitlTimeout,
        )
        cases.entries.forEachIndexed { index, (runId, _) ->
            repo.recordEvaluation(
                evaluation(
                    id = runId,
                    evaluatedAt = index.toLong(),
                    verdict = TriggerEvaluationVerdict.Fired,
                    runId = runId,
                ),
            )
        }
        cases.forEach { (runId, outcome) -> repo.recordRunOutcome(runId, outcome) }

        val byRun = repo.observeByTrigger("trig-1").first().associateBy { it.runId }
        cases.forEach { (runId, outcome) -> assertEquals(outcome, byRun.getValue(runId).outcome) }
    }

    @Test
    fun `given a row with an unknown source when read then it is dropped`() = runTest {
        val repo = repository()
        // A valid row plus a corrupt one written straight through the DAO.
        dao.insert(
            TriggerEvaluationEntity(
                id = "ok",
                triggerId = "trig-1",
                evaluatedAt = 1,
                source = "POLL",
                verdictKind = "FIRED",
                skipReason = null,
                runId = null,
                outcomeKind = null,
                outcomeError = null,
            ),
        )
        dao.insert(
            TriggerEvaluationEntity(
                id = "bad",
                triggerId = "trig-1",
                evaluatedAt = 2,
                source = "FROM_THE_FUTURE",
                verdictKind = "FIRED",
                skipReason = null,
                runId = null,
                outcomeKind = null,
                outcomeError = null,
            ),
        )

        val stored = repo.observeByTrigger("trig-1").first()

        assertEquals(listOf("ok"), stored.map { it.id })
    }

    @Test
    fun `given a skipped row with an unknown reason when read then it is dropped`() = runTest {
        val repo = repository()
        dao.insert(
            TriggerEvaluationEntity(
                id = "bad",
                triggerId = "trig-1",
                evaluatedAt = 1,
                source = "POLL",
                verdictKind = "SKIPPED",
                skipReason = "MYSTERY",
                runId = null,
                outcomeKind = null,
                outcomeError = null,
            ),
        )

        assertTrue(repo.observeByTrigger("trig-1").first().isEmpty())
    }

    @Test
    fun `given old rows and an overflowing count when retention runs then it prunes both`() = runTest {
        val repo = repository()
        // Two rows below the age cutoff, three above it.
        repo.recordEvaluation(evaluation("old-1", evaluatedAt = 10, verdict = TriggerEvaluationVerdict.ReArmed))
        repo.recordEvaluation(evaluation("old-2", evaluatedAt = 20, verdict = TriggerEvaluationVerdict.ReArmed))
        repo.recordEvaluation(evaluation("new-1", evaluatedAt = 200, verdict = TriggerEvaluationVerdict.ReArmed))
        repo.recordEvaluation(evaluation("new-2", evaluatedAt = 300, verdict = TriggerEvaluationVerdict.ReArmed))
        repo.recordEvaluation(evaluation("new-3", evaluatedAt = 400, verdict = TriggerEvaluationVerdict.ReArmed))

        // Cutoff drops the two old rows; cap keeps only the newest two of the rest.
        val deleted = repo.applyRetention(olderThanEpochMs = 100, maxRecords = 2)

        assertEquals(3, deleted)
        assertEquals(listOf("new-3", "new-2"), repo.observeByTrigger("trig-1").first().map { it.id })
    }

    // --- Best-effort contract (mocked DAO) --------------------------------

    @Test
    fun `given the dao write fails when recording then the failure is absorbed`() = runTest {
        val failingDao = mockk<TriggerJournalDao>()
        coEvery { failingDao.insert(any()) } throws IllegalStateException("db down")
        val repo = repository(failingDao)

        // Must not throw — the journal is an observer, never a participant.
        repo.recordEvaluation(evaluation("x"))
    }

    @Test
    fun `given the dao read fails when observing then it degrades to an empty list`() = runTest {
        val failingDao = mockk<TriggerJournalDao>()
        every { failingDao.observeByTrigger(any()) } returns flow { throw IllegalStateException("read boom") }
        val repo = repository(failingDao)

        assertTrue(repo.observeByTrigger("trig-1").first().isEmpty())
    }

    @Test
    fun `given the dao retention fails when applied then it reports zero deletions`() = runTest {
        val failingDao = mockk<TriggerJournalDao>()
        coEvery { failingDao.applyRetention(any(), any()) } throws IllegalStateException("retention boom")
        val repo = repository(failingDao)

        assertEquals(0, repo.applyRetention(olderThanEpochMs = 1, maxRecords = 1))
    }

    @Test
    fun `given a fired row with no settled outcome when read then the outcome is null`() = runTest {
        val repo = repository()
        repo.recordEvaluation(evaluation("pending", verdict = TriggerEvaluationVerdict.Fired, runId = "run-p"))

        assertNull(repo.observeByTrigger("trig-1").first().single().outcome)
    }

    @Test
    fun `given a non-positive cap when retention is applied then it rejects rather than wiping the table`() {
        val repo = repository()

        // `require` throws before any suspension point, so runBlocking surfaces it.
        assertThrows(IllegalArgumentException::class.java) {
            runBlocking { repo.applyRetention(olderThanEpochMs = 1, maxRecords = 0) }
        }
    }

    @Test
    fun `given a failure row with an empty message when read then it stays a failure, but a null message is dropped`() =
        runTest {
            val repo = repository()
            // An empty message is a legitimate failure and must survive as such.
            dao.insert(row(id = "empty", evaluatedAt = 2, outcomeKind = "FAILURE", outcomeError = ""))
            // A FAILURE kind with a null message is corrupt and decodes to no outcome.
            dao.insert(row(id = "corrupt", evaluatedAt = 1, outcomeKind = "FAILURE", outcomeError = null))

            val byId = repo.observeByTrigger("trig-1").first().associateBy { it.id }
            assertEquals(TriggerRunOutcome.Failure(""), byId.getValue("empty").outcome)
            assertNull(byId.getValue("corrupt").outcome)
        }

    @Test
    fun `given rows across triggers when observing health inputs then latest eval and fired outcome per trigger`() =
        runTest {
            val repo = repository()
            // trig-1: fired+success at 20, then a later skip at 30 (the newest row).
            repo.recordEvaluation(
                evaluation("a", "trig-1", evaluatedAt = 20, runId = "r-a", outcome = TriggerRunOutcome.Success),
            )
            repo.recordEvaluation(
                evaluation(
                    "b",
                    "trig-1",
                    evaluatedAt = 30,
                    verdict = TriggerEvaluationVerdict.Skipped(TriggerSkipReason.ALREADY_FIRED),
                ),
            )
            // trig-2: a single fired+failure at 5.
            repo.recordEvaluation(
                evaluation("c", "trig-2", evaluatedAt = 5, runId = "r-c", outcome = TriggerRunOutcome.Failure("boom")),
            )

            val inputs = repo.observeHealthInputs().first()

            // Latest evaluation is the newest row of ANY verdict; the fired outcome
            // comes from the newest FIRED row even when a skip supersedes it.
            assertEquals(30L, inputs.getValue("trig-1").latestEvaluatedAt)
            assertEquals(TriggerRunOutcome.Success, inputs.getValue("trig-1").latestFiredOutcome)
            assertEquals(5L, inputs.getValue("trig-2").latestEvaluatedAt)
            assertEquals(TriggerRunOutcome.Failure("boom"), inputs.getValue("trig-2").latestFiredOutcome)
        }

    @Test
    fun `given a trigger that only ever skipped when observing health inputs then no fired outcome`() = runTest {
        val repo = repository()
        repo.recordEvaluation(
            evaluation(
                "s",
                "trig-1",
                evaluatedAt = 10,
                verdict = TriggerEvaluationVerdict.Skipped(TriggerSkipReason.DISABLED),
            ),
        )

        val entry = repo.observeHealthInputs().first().getValue("trig-1")

        assertEquals(10L, entry.latestEvaluatedAt)
        assertNull(entry.latestFiredOutcome)
    }

    @Test
    fun `given a fired run still pending when observing health inputs then no fired outcome yet`() = runTest {
        val repo = repository()
        repo.recordEvaluation(evaluation("p", "trig-1", evaluatedAt = 10, runId = "r-p", outcome = null))

        val entry = repo.observeHealthInputs().first().getValue("trig-1")

        assertEquals(10L, entry.latestEvaluatedAt)
        assertNull(entry.latestFiredOutcome)
    }

    /** Builds a raw fired-row entity for the tolerant-decode tests. */
    private fun row(id: String, evaluatedAt: Long, outcomeKind: String?, outcomeError: String?) =
        TriggerEvaluationEntity(
            id = id,
            triggerId = "trig-1",
            evaluatedAt = evaluatedAt,
            source = "POLL",
            verdictKind = "FIRED",
            skipReason = null,
            runId = "run-$id",
            outcomeKind = outcomeKind,
            outcomeError = outcomeError,
        )
}
