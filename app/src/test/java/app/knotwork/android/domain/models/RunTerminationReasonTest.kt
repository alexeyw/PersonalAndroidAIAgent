package app.knotwork.android.domain.models

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Unit tests for [RunTerminationReason] and its persisted discriminator.
 *
 * The vocabulary is written into `pipeline_runs.terminationReason`, so the
 * discriminators are a storage format: renaming one silently reclassifies every
 * historical row. These tests pin the names and the round trip.
 */
class RunTerminationReasonTest {

    @Test
    fun `given the persisted discriminators then the names are exactly these`() {
        // Persisted values. Adding one is free; renaming one rewrites history.
        assertEquals(
            listOf(
                "STEP_CEILING",
                "TOKEN_CEILING",
                "HITL_WINDOW_EXPIRED",
                "NO_PROGRESS",
                "GRAPH_CHANGED",
                "PROCESS_DIED",
                "DISCARDED_BY_USER",
            ),
            RunTerminationKind.entries.map { it.name },
        )
    }

    @Test
    fun `given every kind then a reason exists for it`() {
        RunTerminationKind.entries.forEach { kind ->
            val reason = RunTerminationReason.fromPersisted(kind, stepsSpent = 7, tokensSpent = 900)
            assertNotNull("no reason for $kind", reason)
            assertEquals("wrong kind reconstructed for $kind", kind, reason?.kind)
        }
    }

    @Test
    fun `given an absent kind then no reason is reconstructed`() {
        // "Unclassified" is `null`, not a catch-all variant: an ordinary node
        // failure has no entry in this vocabulary, and neither does a row
        // written before the column existed.
        assertNull(RunTerminationReason.fromPersisted(null, stepsSpent = 0, tokensSpent = 0))
    }

    @Test
    fun `given a persisted ceiling then the run's own counters fill the numbers`() {
        val steps = RunTerminationReason.fromPersisted(
            RunTerminationKind.STEP_CEILING,
            stepsSpent = 15,
            tokensSpent = 4_000,
        )
        assertEquals(RunTerminationReason.StepCeiling(limit = 15, spent = 15), steps)

        val tokens = RunTerminationReason.fromPersisted(
            RunTerminationKind.TOKEN_CEILING,
            stepsSpent = 15,
            tokensSpent = 4_000,
        )
        assertEquals(RunTerminationReason.TokenCeiling(limit = 4_000, spent = 4_000), tokens)
    }

    @Test
    fun `given a live ceiling reason then it carries the configured limit, not the spend`() {
        // Live, the limit is the number the user configured and the spend is
        // what the run actually used — they are only equal by coincidence.
        // Reading a historical row back cannot recover the configured limit,
        // because the setting behind it is mutable; that asymmetry is the
        // reason `fromPersisted` reports the spend as the limit rather than
        // inventing one.
        val live = RunTerminationReason.TokenCeiling(limit = 100_000, spent = 100_450)
        assertEquals(100_000, live.limit)
        assertEquals(100_450, live.spent)
        assertEquals(RunTerminationKind.TOKEN_CEILING, live.kind)
    }
}
