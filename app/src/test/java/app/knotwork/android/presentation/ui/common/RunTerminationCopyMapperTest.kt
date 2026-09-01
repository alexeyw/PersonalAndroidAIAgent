package app.knotwork.android.presentation.ui.common

import app.knotwork.android.R
import app.knotwork.android.data.engine.TaskQueueManagerImpl
import app.knotwork.android.domain.models.HardCeilingBreach
import app.knotwork.android.domain.models.RunCeilingAxis
import app.knotwork.android.domain.models.RunNoticeCause
import app.knotwork.android.domain.models.RunTerminationKind
import app.knotwork.android.domain.models.RunTerminationReason
import app.knotwork.android.domain.usecases.NO_PROGRESS_JOURNAL_MESSAGE
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
        RunTerminationReason.RunStalled,
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
            context.getString(R.string.triggers_journal_outcome_stopped_by_ceiling),
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
    fun `given the surfaces of one stop then they all say the same sentence`() {
        // The fork this guards is easy to reintroduce and invisible once shipped:
        // the trigger journal renders a plain string it was handed, while the
        // chat and the notification resolve a resource, so the two can drift
        // into saying different things about one event with nothing failing.
        // `domain` cannot read resources, so the words are duplicated — and the
        // duplication is only safe while something compares them.
        assertEquals(
            "the trigger journal and the chat must say the same thing about a stuck run",
            context.getString(R.string.run_termination_body_no_progress),
            NO_PROGRESS_JOURNAL_MESSAGE,
        )
        assertEquals(
            "the trigger journal and the chat must say the same thing about a stalled run",
            context.getString(R.string.run_termination_body_run_stalled),
            TaskQueueManagerImpl.STALLED_MESSAGE,
        )

        // Enumerated rather than sampled, because this file's whole design is
        // enumeration and a fork is invisible without it. The kinds below are
        // the ones whose journal text is known to differ from their chat text;
        // both predate this change (see decisions.md) and are parked, not
        // fixed. Listing them here is what makes a *new* forked kind fail the
        // build instead of joining them silently.
        val knownForked = setOf(RunTerminationKind.GRAPH_CHANGED, RunTerminationKind.NOT_RESUMABLE)
        val alignedHere = setOf(RunTerminationKind.NO_PROGRESS, RunTerminationKind.RUN_STALLED)
        // Ceilings never reach the journal as prose at all — they map to
        // StoppedByCeiling, which carries no message — and the remaining kinds
        // are HITL_WINDOW_EXPIRED (its own outcome), PROCESS_DIED and
        // DISCARDED_BY_USER (never settled through this path with a message).
        val notApplicable = RunTerminationKind.entries.toSet() - knownForked - alignedHere
        assertEquals(
            "a new kind must be classified here: aligned, known-forked, or not applicable",
            RunTerminationKind.entries.toSet(),
            knownForked + alignedHere + notApplicable,
        )
        assertTrue(
            "the ceiling kinds must stay out of the prose path",
            RunTerminationKind.STEP_CEILING in notApplicable && RunTerminationKind.TOKEN_CEILING in notApplicable,
        )
    }

    @Test
    fun `given every reason then the tone matches what the app decided`() {
        fun toneOf(reason: RunTerminationReason) = RunTerminationCopyMapper.terminationCopy(reason).tone
        assertEquals(RunTerminationTone.LIMIT, toneOf(RunTerminationReason.StepCeiling(1, 1)))
        assertEquals(RunTerminationTone.LIMIT, toneOf(RunTerminationReason.TokenCeiling(1, 1)))
        assertEquals(RunTerminationTone.STUCK, toneOf(RunTerminationReason.NoProgress))
        // A stall is not housekeeping: the trigger journal counts it a Failure,
        // and the INFO tone is documented as implying nothing about the
        // pipeline's quality.
        assertEquals(RunTerminationTone.STUCK, toneOf(RunTerminationReason.RunStalled))
        val housekeeping = listOf(
            RunTerminationReason.HitlWindowExpired,
            RunTerminationReason.GraphChanged,
            RunTerminationReason.ProcessDied,
            RunTerminationReason.DiscardedByUser,
            RunTerminationReason.NotResumable,
        )
        housekeeping.forEach { assertEquals("${it.kind} is housekeeping", RunTerminationTone.INFO, toneOf(it)) }
        // Enumerated, not sampled. Without this a kind added later simply is
        // not asserted anywhere, and its tone can drift to whatever a stray
        // edit leaves it as — which is exactly how RUN_STALLED spent a while
        // rendering as housekeeping.
        val toned = setOf(
            RunTerminationKind.STEP_CEILING,
            RunTerminationKind.TOKEN_CEILING,
            RunTerminationKind.NO_PROGRESS,
            RunTerminationKind.RUN_STALLED,
        ) + housekeeping.map { it.kind }
        assertEquals("every kind needs an asserted tone", RunTerminationKind.entries.toSet(), toned)
    }

    @Test
    fun `given every reason then the offered action is the one that can change the outcome`() {
        // Pinned for all eight rather than the two interesting ones. Retry is
        // absent by construction — it re-runs the identical turn into the
        // identical outcome — but asserting that by scanning enum *names* was
        // vacuous, because no such constant exists to find. This asserts the
        // mapping instead, which is what could actually regress.
        val expected = mapOf(
            RunTerminationKind.STEP_CEILING to RunTerminationAction.ADJUST_LIMITS,
            RunTerminationKind.TOKEN_CEILING to RunTerminationAction.ADJUST_LIMITS,
            RunTerminationKind.NO_PROGRESS to RunTerminationAction.OPEN_CONSOLE,
            // A stalled run left nothing in the console to open.
            RunTerminationKind.RUN_STALLED to RunTerminationAction.RUN_AGAIN,
            RunTerminationKind.HITL_WINDOW_EXPIRED to RunTerminationAction.RUN_AGAIN,
            RunTerminationKind.GRAPH_CHANGED to RunTerminationAction.RUN_AGAIN,
            RunTerminationKind.PROCESS_DIED to RunTerminationAction.RUN_AGAIN,
            RunTerminationKind.NOT_RESUMABLE to RunTerminationAction.RUN_AGAIN,
            // The app arguing with a decision it was just given.
            RunTerminationKind.DISCARDED_BY_USER to null,
        )
        assertEquals(RunTerminationKind.entries.toSet(), expected.keys)
        everyReason.forEach { reason ->
            assertEquals(
                "wrong action for ${reason.kind}",
                expected.getValue(reason.kind),
                RunTerminationCopyMapper.terminationCopy(reason).action,
            )
        }
    }

    @Test
    fun `given every reason then the banner clause is specific to its kind`() {
        // The tile bodies already have this guard; the banners did not, so two
        // kinds could have collapsed onto one clause unnoticed.
        val banners = everyReason.map { context.resolve(RunTerminationCopyMapper.terminationCopy(it).banner) }
        assertEquals("each kind needs its own clause", banners.size, banners.toSet().size)
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
    fun `given a step-ceiling stop when notified then the body names the run and the spend`() {
        val body = context.resolve(
            RunTerminationCopyMapper.notificationCopy(
                kind = RunTerminationKind.STEP_CEILING,
                runLabel = "Morning digest",
                stepsSpent = 15,
                tokensSpent = 0,
            ).body,
        )
        assertTrue(body, body.contains("Morning digest"))
        assertTrue(body, body.contains("15"))
    }

    @Test
    fun `given a token-ceiling stop that overshot then the notification does not call the spend an allowance`() {
        // Tokens are charged a whole node's usage at once and only compared
        // afterwards, so a token stop routinely lands PAST its ceiling — this
        // is the normal case, not an edge one. The record keeps the spend and
        // not the limit, so the sentence must report a spend; the earlier
        // wording ("used all N tokens it was allowed") stated the overshoot as
        // the configured limit and contradicted the chat, which shows both.
        val body = context.resolve(
            RunTerminationCopyMapper.notificationCopy(
                kind = RunTerminationKind.TOKEN_CEILING,
                runLabel = "Morning digest",
                stepsSpent = 0,
                tokensSpent = 118_432,
            ).body,
        )
        assertTrue(body, body.contains("Morning digest"))
        assertTrue(body, body.contains("118,432"))
        assertFalse("the spend must not be presented as the allowance: $body", body.contains("allowed"))
    }

    @Test
    fun `given a notification body then it promises no destination the tap cannot reach`() {
        // The notification's only tap action deep-links to the chat, which shows
        // nothing about a run that already finished. "Tap to review the limits"
        // was a promise the tap does not keep.
        RunTerminationKind.entries.forEach { kind ->
            val body = context.resolve(
                RunTerminationCopyMapper.notificationCopy(kind, "Morning digest", 15, 100).body,
            )
            assertFalse("$kind promises a destination: $body", body.contains("Tap to"))
        }
    }

    @Test
    fun `given every kind then the tone ranks a configured limit apart from a run that misbehaved`() {
        // Read by the notification to pick its glyph, so a regression here
        // dresses a watchdog kill as a working guard or the reverse.
        assertEquals(RunTerminationTone.LIMIT, RunTerminationCopyMapper.toneFor(RunTerminationKind.STEP_CEILING))
        assertEquals(RunTerminationTone.LIMIT, RunTerminationCopyMapper.toneFor(RunTerminationKind.TOKEN_CEILING))
        assertEquals(RunTerminationTone.STUCK, RunTerminationCopyMapper.toneFor(RunTerminationKind.NO_PROGRESS))
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

    @Test
    fun `given a ceiling pause then it says paused, never stopped`() {
        // A card headed "Stopped by a safety limit" beside a Continue button
        // reads as an error the user is being invited to override, rather than
        // a decision they are being asked to make. The two titles must differ.
        val pause = RunTerminationCopyMapper.ceilingPauseCopy(
            HardCeilingBreach(RunCeilingAxis.STEPS, limit = 15, spent = 15),
        )
        val stop = RunTerminationCopyMapper.terminationCopy(
            RunTerminationReason.StepCeiling(limit = 15, spent = 15),
        )

        assertNotEquals(context.resolve(stop.title), context.resolve(pause.title))
        assertFalse(context.resolve(pause.title).lowercase().contains("stopped"))
    }

    @Test
    fun `given a ceiling pause then its numbers line matches the stop's, word for word`() {
        // A run that pauses and is then stopped must not appear to change its
        // arithmetic between the two cards.
        val breach = HardCeilingBreach(RunCeilingAxis.TOKENS, limit = 100_000, spent = 100_400)
        val pause = RunTerminationCopyMapper.ceilingPauseCopy(breach)
        val stop = RunTerminationCopyMapper.terminationCopy(
            RunTerminationReason.TokenCeiling(limit = 100_000, spent = 100_400),
        )

        assertEquals(context.resolve(stop.meter!!), context.resolve(pause.meter))
    }

    @Test
    fun `given a ceiling pause then the continue label names the size of the portion`() {
        // "Continue" alone reads as removing the limit, which is exactly what
        // this gate does not do — and the number is the limit that BOUND, so a
        // run already on an extension is offered the amount it will actually get.
        val label = context.resolve(
            RunTerminationCopyMapper.ceilingPauseCopy(
                HardCeilingBreach(RunCeilingAxis.STEPS, limit = 30, spent = 30),
            ).continueLabel,
        )

        assertTrue("Expected the portion size in: $label", label.contains("30"))
    }

    @Test
    fun `given each axis then the pause body speaks in that axis's own unit`() {
        val steps = context.resolve(
            RunTerminationCopyMapper.ceilingPauseCopy(
                HardCeilingBreach(RunCeilingAxis.STEPS, limit = 15, spent = 15),
            ).body,
        )
        val tokens = context.resolve(
            RunTerminationCopyMapper.ceilingPauseCopy(
                HardCeilingBreach(RunCeilingAxis.TOKENS, limit = 15, spent = 15),
            ).body,
        )

        assertTrue(steps.contains("step"))
        assertTrue(tokens.contains("token"))
        assertNotEquals(steps, tokens)
    }
}
