package app.knotwork.android.domain.models

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Unit tests for [RunTerminationReason] and its persisted discriminator.
 *
 * The vocabulary is written into `pipeline_runs.terminationReason`, so the
 * discriminators are a storage format: renaming one silently reclassifies every
 * historical row. These tests pin the names and the kind-to-variant mapping.
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
                "NOT_RESUMABLE",
            ),
            RunTerminationKind.entries.map { it.name },
        )
    }

    @Test
    fun `given every kind then exactly one reason declares it`() {
        // The mapping has to be a bijection: two variants sharing a kind would
        // make the persisted column ambiguous on the way back.
        val declared = listOf(
            RunTerminationReason.StepCeiling(limit = 1, spent = 1),
            RunTerminationReason.TokenCeiling(limit = 1, spent = 1),
            RunTerminationReason.HitlWindowExpired,
            RunTerminationReason.NoProgress,
            RunTerminationReason.GraphChanged,
            RunTerminationReason.ProcessDied,
            RunTerminationReason.DiscardedByUser,
            RunTerminationReason.NotResumable,
        ).map { it.kind }

        assertEquals(RunTerminationKind.entries.toSet(), declared.toSet())
        assertEquals("no kind may be claimed twice", declared.size, declared.toSet().size)
    }

    @Test
    fun `given a live ceiling reason then it carries the configured limit, not the spend`() {
        // The limit is the number the user configured and the spend is what the
        // run actually used — they are only equal by coincidence, so the two
        // must not be conflated by a consumer rendering the sentence.
        val live = RunTerminationReason.TokenCeiling(limit = 100_000, spent = 100_450)
        assertEquals(100_000, live.limit)
        assertEquals(100_450, live.spent)
        assertEquals(RunTerminationKind.TOKEN_CEILING, live.kind)
    }
}
