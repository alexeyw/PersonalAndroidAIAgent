package app.knotwork.android.presentation.ui.common

import app.knotwork.android.domain.models.RunCeilingAxis
import app.knotwork.android.domain.models.RunNoticeCause
import app.knotwork.android.domain.models.RunTerminationKind
import app.knotwork.android.domain.models.RunTerminationReason
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

/**
 * Guards the single vocabulary of a stopped run.
 *
 * The defect this class exists to prevent is specific and had already shipped:
 * six of the eight termination kinds rendered as `"Pipeline execution was
 * stopped (NO_PROGRESS)."` — the enum constant, shown to a person. A mapper
 * that covers only the two ceilings would leave exactly that in place, so the
 * tests below walk **every** kind rather than the interesting ones.
 */
@RunWith(RobolectricTestRunner::class)
class RunTerminationCopyMapperTest {

    private val context = RuntimeEnvironment.getApplication()

    /** One representative reason per kind, so a `when` cannot be dodged. */
    private val everyReason: List<RunTerminationReason> = listOf(
        RunTerminationReason.StepCeiling(limit = 15, spent = 15),
        RunTerminationReason.TokenCeiling(limit = 1_000_000, spent = 1_000_000),
        RunTerminationReason.NoProgress,
        RunTerminationReason.HitlWindowExpired,
        RunTerminationReason.GraphChanged,
        RunTerminationReason.ProcessDied,
        RunTerminationReason.DiscardedByUser,
        RunTerminationReason.NotResumable,
    )

    @Test
    fun `given every reason then the fixture covers the whole vocabulary`() {
        assertEquals(
            "add the new kind to everyReason, or the tests below silently stop covering it",
            RunTerminationKind.entries.toSet(),
            everyReason.map { it.kind }.toSet(),
        )
    }

    @Test
    fun `given every reason when rendered then no constant name reaches the user`() {
        everyReason.forEach { reason ->
            val copy = RunTerminationCopyMapper.terminationCopy(reason)
            val rendered = listOf(copy.title, copy.body, copy.banner).map(context::resolve)
            rendered.forEach { text ->
                assertFalse(
                    "'$text' leaks an identifier for ${reason.kind}",
                    text.contains(CONSTANT_NAME) || text.contains(reason.kind.name),
                )
                assertTrue("blank copy for ${reason.kind}", text.isNotBlank())
            }
        }
    }

    @Test
    fun `given every reason then the body is specific to its kind`() {
        val bodies = everyReason.map { context.resolve(RunTerminationCopyMapper.terminationCopy(it).body) }
        assertEquals("each kind needs its own explanation", bodies.size, bodies.toSet().size)
    }

    @Test
    fun `given the two ceilings then they share one title and differ in the body`() {
        val step = RunTerminationCopyMapper.terminationCopy(RunTerminationReason.StepCeiling(15, 15))
        val token = RunTerminationCopyMapper.terminationCopy(RunTerminationReason.TokenCeiling(100, 100))
        // One event from the user's side — a limit they set stopped the run —
        // so one title. Which limit is the body's job.
        assertEquals(context.resolve(step.title), context.resolve(token.title))
        assertNotEquals(context.resolve(step.body), context.resolve(token.body))
    }

    @Test
    fun `given a ceiling stop then the chat title matches the trigger journal wording`() {
        val copy = RunTerminationCopyMapper.terminationCopy(RunTerminationReason.StepCeiling(15, 15))
        // The journal has called it this since the ceilings shipped. Two
        // surfaces describing one event differently is the whole defect.
        assertEquals(
            context.getString(app.knotwork.android.R.string.triggers_journal_outcome_stopped_by_ceiling),
            context.resolve(copy.title),
        )
    }

    @Test
    fun `given every reason then the banner is never the tile body`() {
        everyReason.forEach { reason ->
            val copy = RunTerminationCopyMapper.terminationCopy(reason)
            // The banner is clamped to two lines. Sharing the tile's sentence is
            // what cut the copy in half at large font scales.
            assertNotEquals(
                "banner and body must be separate strings for ${reason.kind}",
                context.resolve(copy.body),
                context.resolve(copy.banner),
            )
        }
    }

    @Test
    fun `given a ceiling then the numbers ride on their own line`() {
        val copy = RunTerminationCopyMapper.terminationCopy(RunTerminationReason.StepCeiling(limit = 15, spent = 15))
        val meter = context.resolve(requireNotNull(copy.meter) { "a ceiling knows its numbers" })
        assertTrue("the meter states the spend and the limit: $meter", meter.contains("15"))
        // Kept out of the body so one sentence serves surfaces that have the
        // numbers and surfaces that do not.
        assertFalse(context.resolve(copy.body).contains("15"))
    }

    @Test
    fun `given a reason with no numbers then there is no meter`() {
        everyReason.filter { it.kind !in CEILING_KINDS }.forEach { reason ->
            assertNull("${reason.kind} has no numbers to show", RunTerminationCopyMapper.terminationCopy(reason).meter)
        }
    }

    @Test
    fun `given every reason then the tone matches what the app decided`() {
        fun toneOf(reason: RunTerminationReason) = RunTerminationCopyMapper.terminationCopy(reason).tone
        assertEquals(RunTerminationTone.LIMIT, toneOf(RunTerminationReason.StepCeiling(1, 1)))
        assertEquals(RunTerminationTone.LIMIT, toneOf(RunTerminationReason.TokenCeiling(1, 1)))
        assertEquals(RunTerminationTone.STUCK, toneOf(RunTerminationReason.NoProgress))
        listOf(
            RunTerminationReason.HitlWindowExpired,
            RunTerminationReason.GraphChanged,
            RunTerminationReason.ProcessDied,
            RunTerminationReason.DiscardedByUser,
            RunTerminationReason.NotResumable,
        ).forEach { assertEquals("${it.kind} is housekeeping", RunTerminationTone.INFO, toneOf(it)) }
    }

    @Test
    fun `given every reason then retry is never offered`() {
        // Retry re-runs the identical turn into the identical outcome. It
        // survives only on the untyped error tile, which this mapper never
        // produces copy for.
        val actions = everyReason.mapNotNull { RunTerminationCopyMapper.terminationCopy(it).action }
        assertTrue(actions.none { it.name.contains("RETRY", ignoreCase = true) })
    }

    @Test
    fun `given a ceiling then the action leads to the limits`() {
        assertEquals(
            RunTerminationAction.ADJUST_LIMITS,
            RunTerminationCopyMapper.terminationCopy(RunTerminationReason.StepCeiling(1, 1)).action,
        )
    }

    @Test
    fun `given a run the user discarded then nothing is offered`() {
        // The app arguing with a decision it was just given.
        assertNull(RunTerminationCopyMapper.terminationCopy(RunTerminationReason.DiscardedByUser).action)
    }

    @Test
    fun `given every kind when notified then the title matches the chat`() {
        RunTerminationKind.entries.forEach { kind ->
            val notification = RunTerminationCopyMapper.notificationCopy(kind, "Morning digest", 15, 100)
            val chat = everyReason.first { it.kind == kind }.let { RunTerminationCopyMapper.terminationCopy(it) }
            assertEquals(
                "the notification and the chat must not word one event twice",
                context.resolve(chat.title),
                context.resolve(notification.title),
            )
            assertFalse(context.resolve(notification.body).contains(kind.name))
        }
    }

    @Test
    fun `given a ceiling stop when notified then the body names the run and the allowance`() {
        val body = context.resolve(
            RunTerminationCopyMapper.notificationCopy(
                kind = RunTerminationKind.STEP_CEILING,
                runLabel = "Morning digest",
                stepsSpent = 15,
                tokensSpent = 0,
            ).body,
        )
        assertTrue(body, body.contains("Morning digest"))
        // A run stopped by a ceiling has spent exactly the ceiling, so the spend
        // is the allowance and the notification can state it without the limit.
        assertTrue(body, body.contains("15"))
    }

    @Test
    fun `given a soft crossing then the notice names the axis and both numbers`() {
        val text = context.resolve(
            RunTerminationCopyMapper.noticeCopy(
                RunNoticeCause.ApproachingCeiling(axis = RunCeilingAxis.STEPS, spent = 12, hardLimit = 15),
            ).text,
        )
        assertTrue(text, text.contains("12"))
        assertTrue(text, text.contains("15"))
        assertTrue(text, text.contains("step", ignoreCase = true))
    }

    private companion object {
        /** Substring every SCREAMING_SNAKE identifier in this vocabulary shares. */
        const val CONSTANT_NAME: String = "_"

        val CEILING_KINDS = setOf(RunTerminationKind.STEP_CEILING, RunTerminationKind.TOKEN_CEILING)
    }
}
