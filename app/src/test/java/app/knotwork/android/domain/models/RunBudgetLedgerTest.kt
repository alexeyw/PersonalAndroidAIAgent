package app.knotwork.android.domain.models

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for [RunBudgetLedger] — the run-tree spend ledger every autonomous
 * ceiling is charged against.
 *
 * The behaviours worth pinning are the ones the ledger exists to fix: the
 * counters continue across a resume instead of restarting, the hard ceiling is
 * checked before a node is charged (so a stopped run spent exactly the ceiling),
 * and each soft threshold warns once rather than on every node for the rest of
 * the run.
 */
class RunBudgetLedgerTest {

    private fun ceilings(stepsHard: Int = 10, tokensHard: Int = 1_000, origin: RunOrigin = RunOrigin.TRIGGER) =
        RunCeilings(
            origin = origin,
            steps = RunCeilingLimit.Enforced(soft = RunCeilings.softFor(stepsHard), hard = stepsHard),
            tokens = RunCeilingLimit.Enforced(soft = RunCeilings.softFor(tokensHard), hard = tokensHard),
        )

    @Test
    fun `given a fresh ledger then nothing is spent and nothing has breached`() {
        val ledger = RunBudgetLedger(ceilings())

        assertEquals(0, ledger.stepsSpent)
        assertEquals(0, ledger.tokensSpent)
        assertNull(ledger.hardBreach())
        assertFalse(ledger.tokensApproximate)
    }

    @Test
    fun `given the step ceiling reached then the breach names the step axis with its numbers`() {
        val ledger = RunBudgetLedger(ceilings(stepsHard = 3))

        repeat(3) {
            assertNull("node ${it + 1} must still be allowed", ledger.hardBreach())
            ledger.chargeStep()
        }

        val breach = ledger.hardBreach()
        assertEquals(RunTerminationReason.StepCeiling(limit = 3, spent = 3), breach)
    }

    @Test
    fun `given the ceiling is reached then the refused node was never charged`() {
        // The run that stops at the ceiling has spent exactly the ceiling. The
        // engine asks before charging precisely so the refused node does not
        // land in the persisted counter and make the record say 4 of 3.
        val ledger = RunBudgetLedger(ceilings(stepsHard = 3))
        repeat(3) { ledger.chargeStep() }

        assertTrue(ledger.hardBreach() is RunTerminationReason.StepCeiling)
        assertEquals(3, ledger.stepsSpent)
    }

    @Test
    fun `given spend seeded from a previous attempt then the ceiling binds across a resume`() {
        // This is the whole point of persisting the counters. A run parked on a
        // background approval comes back through the resume path; before the
        // ledger was seeded it received a full fresh ceiling every time, so a
        // nightly loop raising one gate per iteration was never bounded.
        val ledger = RunBudgetLedger(ceilings(stepsHard = 10), stepsAlreadySpent = 10)

        assertEquals(
            RunTerminationReason.StepCeiling(limit = 10, spent = 10),
            ledger.hardBreach(),
        )
    }

    @Test
    fun `given the token ceiling reached then the breach names the token axis`() {
        val ledger = RunBudgetLedger(ceilings(tokensHard = 500))

        ledger.chargeTokens(400, approximate = false)
        assertNull(ledger.hardBreach())

        ledger.chargeTokens(150, approximate = false)
        assertEquals(
            RunTerminationReason.TokenCeiling(limit = 500, spent = 550),
            ledger.hardBreach(),
        )
    }

    @Test
    fun `given both axes breached then steps is reported first`() {
        // Deterministic precedence: an ambiguous breach must always be reported
        // the same way, or two runs that hit both limits would settle with
        // different reasons for the same situation.
        val ledger = RunBudgetLedger(
            ceilings(stepsHard = 2, tokensHard = 10),
            stepsAlreadySpent = 5,
            tokensAlreadySpent = 50,
        )

        assertTrue(ledger.hardBreach() is RunTerminationReason.StepCeiling)
    }

    @Test
    fun `given a null or non-positive token count then nothing is charged`() {
        val ledger = RunBudgetLedger(ceilings())

        ledger.chargeTokens(null, approximate = false)
        ledger.chargeTokens(0, approximate = false)
        ledger.chargeTokens(-5, approximate = true)

        assertEquals(0, ledger.tokensSpent)
        // A node that reported nothing must not make the whole run's number
        // read as an estimate — the flag has to mean something.
        assertFalse(ledger.tokensApproximate)
    }

    @Test
    fun `given one estimated contribution then the whole run's token count is flagged approximate`() {
        val ledger = RunBudgetLedger(ceilings())

        ledger.chargeTokens(100, approximate = false)
        assertFalse(ledger.tokensApproximate)

        ledger.chargeTokens(50, approximate = true)
        assertTrue(ledger.tokensApproximate)

        // And it does not un-flag when a later exact node arrives: the total
        // still contains an estimate.
        ledger.chargeTokens(10, approximate = false)
        assertTrue(ledger.tokensApproximate)
    }

    @Test
    fun `given the soft threshold crossed then it is claimed once per axis`() {
        val ledger = RunBudgetLedger(ceilings(stepsHard = 4))
        // softFor(4) == 3.

        repeat(2) { ledger.chargeStep() }
        assertNull("below the soft threshold nothing is claimed", ledger.claimSoftBreach())

        ledger.chargeStep()
        val first = ledger.claimSoftBreach()
        assertEquals(RunCeilingAxis.STEPS, first?.axis)
        assertEquals(3, first?.spent)
        assertEquals(4, first?.hardLimit)

        // Repeating the warning on every remaining node teaches the user to
        // ignore it, so the crossing is consumed by the claim.
        assertNull(ledger.claimSoftBreach())
    }

    @Test
    fun `given both axes cross then each warns once, independently`() {
        val ledger = RunBudgetLedger(ceilings(stepsHard = 4, tokensHard = 100))

        repeat(3) { ledger.chargeStep() }
        assertEquals(RunCeilingAxis.STEPS, ledger.claimSoftBreach()?.axis)

        ledger.chargeTokens(80, approximate = false)
        assertEquals(RunCeilingAxis.TOKENS, ledger.claimSoftBreach()?.axis)

        assertNull(ledger.claimSoftBreach())
    }

    @Test
    fun `given softFor then it never exceeds the hard limit nor drops below one`() {
        assertEquals(1, RunCeilings.softFor(1))
        // 75% of 2 is 1.5 → 1, still a real warning point below the hard stop.
        assertEquals(1, RunCeilings.softFor(2))
        assertEquals(11, RunCeilings.softFor(15))
        assertEquals(750_000, RunCeilings.softFor(1_000_000))
        // The arithmetic runs through Long, so a ceiling near Int.MAX_VALUE does
        // not overflow into a negative "soft" threshold that would fire at once.
        assertTrue(RunCeilings.softFor(Int.MAX_VALUE) in 1..Int.MAX_VALUE)
    }

    @Test
    fun `given no root run id then the ledger is purely in-memory`() {
        // An editor test run has no persisted record; the ledger must still
        // count so the ceiling applies, it just has nowhere to write.
        val ledger = RunBudgetLedger(ceilings())

        assertNull(ledger.rootRunId)
        ledger.chargeStep()
        assertEquals(1, ledger.stepsSpent)
    }

    @Test
    fun `given no extensions then the effective ceiling is the configured one`() {
        val ledger = RunBudgetLedger(ceilings(stepsHard = 10))

        assertEquals(10, ledger.effectiveHard(RunCeilingAxis.STEPS))
        assertEquals(1_000, ledger.effectiveHard(RunCeilingAxis.TOKENS))
    }

    @Test
    fun `given one granted extension then the axis gets one more whole allowance`() {
        // "Continue for another N" is the promise the card makes, so the number
        // has to be the same allowance again — not a doubling of some other
        // quantity, and not the removal of the limit.
        val ledger = RunBudgetLedger(ceilings(stepsHard = 10), stepsAlreadySpent = 10)
        assertEquals(RunTerminationReason.StepCeiling(limit = 10, spent = 10), ledger.hardBreach())

        ledger.grantExtension(RunCeilingAxis.STEPS)

        assertEquals(20, ledger.effectiveHard(RunCeilingAxis.STEPS))
        assertNull(ledger.hardBreach())
    }

    @Test
    fun `given a granted extension on one axis then the other axis is untouched`() {
        // The answer buys a portion of the axis that bound. A run waved past its
        // step ceiling has not been granted more tokens, and treating the grant
        // as budget-wide would let one answer authorise spending nobody agreed to.
        val ledger = RunBudgetLedger(ceilings(stepsHard = 10, tokensHard = 1_000))

        ledger.grantExtension(RunCeilingAxis.STEPS)

        assertEquals(20, ledger.effectiveHard(RunCeilingAxis.STEPS))
        assertEquals(1_000, ledger.effectiveHard(RunCeilingAxis.TOKENS))
    }

    @Test
    fun `given seeded extensions then a resumed ledger binds where the previous attempt was allowed to`() {
        // The seed is the whole reason the counts are persisted: a resumed run
        // that reads the spend but not the grant breaches on its first node and
        // re-asks the question the user just answered.
        val ledger = RunBudgetLedger(
            ceilings(stepsHard = 10),
            stepsAlreadySpent = 15,
            stepCeilingExtensions = 1,
        )

        assertNull(ledger.hardBreach())
        assertEquals(1, ledger.stepCeilingExtensions)
    }

    @Test
    fun `given an extension then the axis warns again inside the allowance it was granted`() {
        // The claim set remembers the axis already warned, and the threshold it
        // warned at is now far behind. Without re-arming, the extra portion
        // would be spent with no advisory at all.
        val ledger = RunBudgetLedger(ceilings(stepsHard = 10), stepsAlreadySpent = 8)
        assertEquals(RunCeilingAxis.STEPS, ledger.claimSoftBreach()?.axis)
        assertNull(ledger.claimSoftBreach())

        ledger.grantExtension(RunCeilingAxis.STEPS)

        // 8 of 20 is below the new 75% threshold, so nothing fires yet…
        assertNull(ledger.claimSoftBreach())
        repeat(7) { ledger.chargeStep() }
        // …and 15 of 20 crosses it, naming the raised ceiling rather than the base one.
        val soft = ledger.claimSoftBreach()
        assertEquals(RunCeilingAxis.STEPS, soft?.axis)
        assertEquals(20, soft?.hardLimit)
    }

    @Test
    fun `given many extensions on a huge ceiling then the limit clamps instead of wrapping negative`() {
        // Nothing bounds how often a user may answer "continue". An overflow
        // here would not merely report a wrong number — it would wrap negative
        // and kill the run on the step after the one just paid for.
        val ledger = RunBudgetLedger(ceilings(stepsHard = Int.MAX_VALUE))

        repeat(3) { ledger.grantExtension(RunCeilingAxis.STEPS) }

        assertEquals(Int.MAX_VALUE, ledger.effectiveHard(RunCeilingAxis.STEPS))
        assertNull(ledger.hardBreach())
    }

    @Test
    fun `given the unmeasured money axis then a grant changes nothing`() {
        val ledger = RunBudgetLedger(ceilings())

        ledger.grantExtension(RunCeilingAxis.MONEY)

        assertEquals(0, ledger.stepCeilingExtensions)
        assertEquals(0, ledger.tokenCeilingExtensions)
    }

    @Test
    fun `given the money axis then it is unavailable rather than zero`() {
        val ledger = RunBudgetLedger(ceilings())

        // The product must say it does not measure money, not imply it measures
        // it and found nothing.
        assertEquals(RunCeilingLimit.Unavailable, ledger.ceilings.limitOn(RunCeilingAxis.MONEY))
    }
}
