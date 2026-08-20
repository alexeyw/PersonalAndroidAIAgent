package app.knotwork.android.domain.engine.stuck

import app.knotwork.android.domain.constants.SettingsDefaults
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for [GraphStuckDetector].
 *
 * Deliberately framework-free — no Robolectric, no MockK, no Android. The
 * detector is pure arithmetic over fingerprints, and a test that needed a
 * framework to exercise it would be evidence that it had stopped being.
 */
class GraphStuckDetectorTest {

    // ─── Fixtures ────────────────────────────────────────────────────────────

    /**
     * A step that did work: its output differs from its input.
     *
     * @param node Node identity.
     * @param input Input text.
     * @param output Output text.
     * @return The observation.
     */
    private fun step(node: String, input: String, output: String) = RunStepObservation(
        nodeId = node,
        inputFingerprint = GraphStuckDetector.fingerprint(input),
        outputFingerprint = GraphStuckDetector.fingerprint(output),
    )

    /**
     * A step that forwarded its input untouched.
     *
     * @param node Node identity.
     * @param text The text passing through.
     * @return The observation.
     */
    private fun passThrough(node: String, text: String) = step(node, text, text)

    // ─── Fingerprinting ──────────────────────────────────────────────────────

    @Test
    fun `given the same text when fingerprinted twice then the digests match`() {
        assertEquals(
            GraphStuckDetector.fingerprint("the same answer"),
            GraphStuckDetector.fingerprint("the same answer"),
        )
    }

    @Test
    fun `given texts differing by one character then the digests differ`() {
        assertNotEquals(
            GraphStuckDetector.fingerprint("answer"),
            GraphStuckDetector.fingerprint("answed"),
        )
    }

    @Test
    fun `given any text then the fingerprint is a fixed-width lower-case hex digest`() {
        // Fixed width matters: the window's memory cost must not scale with the
        // size of the answers a looping run is generating.
        val short = GraphStuckDetector.fingerprint("a")
        val long = GraphStuckDetector.fingerprint("a".repeat(100_000))
        assertEquals(64, short.length)
        assertEquals(64, long.length)
        assertTrue(short, short.all { it.isDigit() || it in 'a'..'f' })
    }

    // ─── The progress rule ───────────────────────────────────────────────────

    @Test
    fun `given a step that produces new output then progress resets the counter`() {
        val detector = GraphStuckDetector()
        detector.observe(step("a", "in", "first"))
        detector.observe(step("a", "in2", "second"))
        assertEquals(0, detector.stepsSinceProgress)
    }

    @Test
    fun `given a step that repeats an earlier output then it is not progress`() {
        val detector = GraphStuckDetector()
        detector.observe(step("a", "in", "same"))
        assertEquals(0, detector.stepsSinceProgress)
        detector.observe(step("b", "other", "same"))
        assertEquals("a text already produced is not new work", 1, detector.stepsSinceProgress)
    }

    @Test
    fun `given a pass-through step then it never counts as progress`() {
        val detector = GraphStuckDetector()
        detector.observe(step("a", "in", "produced"))
        assertEquals(0, detector.stepsSinceProgress)
        // A router forwarding text it did not write must not reset the counter,
        // or a loop containing one would be permanently invisible.
        detector.observe(passThrough("router", "brand new text"))
        assertEquals(1, detector.stepsSinceProgress)
    }

    @Test
    fun `given a pass-through then the text it forwarded can still be produced later`() {
        // The generous direction: a fingerprint first seen on a pass-through
        // does not enter the ledger, so the node that genuinely produces it
        // afterwards still counts as progress. Being wrong this way costs a
        // late stop; being wrong the other way kills a healthy run.
        val detector = GraphStuckDetector()
        detector.observe(passThrough("input", "the task"))
        assertEquals(1, detector.stepsSinceProgress)
        detector.observe(step("worker", "prompt", "the task"))
        assertEquals(0, detector.stepsSinceProgress)
    }

    // ─── REPEATED_STEP ───────────────────────────────────────────────────────

    @Test
    fun `given the same node input and output three times then the run is nudged`() {
        val detector = GraphStuckDetector()
        // Seeded with the answer the loop will keep giving, so all three loop
        // steps are repetitions. A loop's own first pass produces that answer
        // for the first time and is therefore real progress — which is why a
        // loop reached cold is caught on its fourth pass, not its third.
        detector.observe(step("seed", "start", "same-out"))
        assertEquals(StuckVerdict.Healthy, detector.observe(step("loop", "same-in", "same-out")))
        assertEquals(StuckVerdict.Healthy, detector.observe(step("loop", "same-in", "same-out")))
        val verdict = detector.observe(step("loop", "same-in", "same-out"))
        assertEquals(StuckVerdict.Nudge(StuckSignal.REPEATED_STEP), verdict)
    }

    @Test
    fun `given the same node and input but a different output each time then nothing fires`() {
        // The result-aware property, and the reason the signal is keyed on the
        // triple: a node re-entered with the same prompt that answers
        // differently is exploring, not looping.
        val detector = GraphStuckDetector()
        repeat(GraphStuckDetector.WINDOW_SIZE) { i ->
            val verdict = detector.observe(step("worker", "same-prompt", "answer $i"))
            assertEquals("iteration $i", StuckVerdict.Healthy, verdict)
        }
    }

    @Test
    fun `given two repetitions then nothing fires yet`() {
        // Two is a coincidence — a graph legitimately revisits a node.
        val detector = GraphStuckDetector()
        detector.observe(step("seed", "start", "list"))
        detector.observe(step("loop", "in", "out"))
        assertEquals(StuckVerdict.Healthy, detector.observe(step("loop", "in", "out")))
    }

    @Test
    fun `given a repetition older than the window then it no longer counts`() {
        // The window is what stops a long unproductive stretch from
        // accumulating evidence forever. Three identical steps are spread so
        // that the oldest has fallen out by the time the third arrives: within
        // the window there are only two, and two is a coincidence.
        val detector = GraphStuckDetector(windowSize = 3, staleStreak = 99)
        detector.observe(step("seed", "start", "X"))
        detector.observe(step("loop", "in", "X"))
        detector.observe(step("loop", "in", "X"))
        detector.observe(step("filler", "other", "X"))
        assertEquals(
            "an evicted repetition must not still count",
            StuckVerdict.Healthy,
            detector.observe(step("loop", "in", "X")),
        )
    }

    @Test
    fun `given identical steps by different nodes then they are not one node repeating`() {
        val detector = GraphStuckDetector(staleStreak = 99)
        detector.observe(step("seed", "start", "out"))
        detector.observe(step("a", "in", "out"))
        detector.observe(step("b", "in", "out"))
        assertEquals(StuckVerdict.Healthy, detector.observe(step("c", "in", "out")))
    }

    // ─── NO_NEW_OUTPUT ───────────────────────────────────────────────────────

    @Test
    fun `given a whole streak of steps that say nothing new then the run is nudged`() {
        val detector = GraphStuckDetector()
        detector.observe(step("seed", "asked", "known"))
        // Distinct *nodes* each time, so REPEATED_STEP cannot fire — only the
        // streak is left to notice. The input is held constant on purpose: a
        // run still being handed something new is one this signal must leave
        // alone, however repetitive its answers (see the draining-queue test).
        repeat(GraphStuckDetector.STALE_STREAK - 1) { i ->
            assertEquals("iteration $i", StuckVerdict.Healthy, detector.observe(step("n$i", "asked", "known")))
        }
        assertEquals(
            StuckVerdict.Nudge(StuckSignal.NO_NEW_OUTPUT),
            detector.observe(step("last", "asked", "known")),
        )
    }

    @Test
    fun `given a short chain of pass-throughs then an ordinary run is not accused`() {
        // INPUT, a router, a condition and an echo OUTPUT in a row is four
        // pass-throughs, and the longest such chain a real pipeline strings
        // together. It must stay comfortably clear of the streak.
        val detector = GraphStuckDetector()
        detector.observe(step("model", "prompt", "the answer"))
        val verdicts = listOf("input", "router", "condition", "echo").map {
            detector.observe(passThrough(it, "the answer"))
        }
        assertTrue("no ordinary run may be nudged: $verdicts", verdicts.all { it == StuckVerdict.Healthy })
    }

    @Test
    fun `given a queue whose items all answer identically then it is still not accused`() {
        // The false positive the input conjunct exists for. Twelve recipients
        // marked through one tool that answers `{"status":"ok"}` for each: one
        // novel output and eleven repeats, which is no progress by the rule and
        // a stall by any streak worth setting. What separates it from a loop is
        // that the run is being asked something new every time.
        val detector = GraphStuckDetector()
        repeat(12) { item ->
            val verdict = detector.observe(step("mark", "recipient $item", """{"status":"ok"}"""))
            assertEquals("item $item", StuckVerdict.Healthy, verdict)
        }
    }

    @Test
    fun `given a draining queue then it is never accused of looping`() {
        // The false positive that matters most. A queue re-enters the same item
        // node once per item, and each iteration rewrites the input with the
        // results accumulated so far and produces a fresh answer.
        val detector = GraphStuckDetector()
        val results = mutableListOf<String>()
        repeat(20) { item ->
            val queue = detector.observe(passThrough("queue", "items left: ${20 - item}"))
            assertEquals("queue node, item $item", StuckVerdict.Healthy, queue)
            val worker = detector.observe(
                step("item-worker", "context=${results.joinToString()}|task=$item", "result of $item"),
            )
            assertEquals("worker, item $item", StuckVerdict.Healthy, worker)
            results += "result of $item"
        }
    }

    @Test
    fun `given a loop that repeats a step but keeps producing new work then it is never accused`() {
        // The false positive that the progress rule exists for, and the one a
        // naive "count repetitions in the window" misses: a graph alternating a
        // stable pass-through — a condition on a flag that does not change —
        // with a node producing a fresh answer repeats that pass-through on
        // every single pass, forever, while advancing the whole time.
        val detector = GraphStuckDetector()
        repeat(GraphStuckDetector.WINDOW_SIZE * 2) { i ->
            val gate = detector.observe(passThrough("condition", "keep going"))
            assertEquals("condition on pass $i", StuckVerdict.Healthy, gate)
            val work = detector.observe(step("worker", "prompt $i", "result $i"))
            assertEquals("worker on pass $i", StuckVerdict.Healthy, work)
        }
    }

    @Test
    fun `given repetitions on the far side of real progress then they are not counted`() {
        // Same rule, stated on the arithmetic: two identical steps, then a
        // productive one, then a third identical step must not add up to three.
        val detector = GraphStuckDetector(staleStreak = 99)
        detector.observe(step("loop", "in", "out"))
        detector.observe(step("loop", "in", "out"))
        detector.observe(step("worker", "fresh", "genuinely new"))
        assertEquals(
            "evidence from before the run last advanced is history, not a loop",
            StuckVerdict.Healthy,
            detector.observe(step("loop", "in", "out")),
        )
    }

    // ─── Escalation ──────────────────────────────────────────────────────────

    @Test
    fun `given a nudge that goes unheeded then the run is stopped after the grace period`() {
        val detector = GraphStuckDetector()
        detector.observe(step("seed", "start", "out"))
        repeat(2) { detector.observe(step("loop", "in", "out")) }
        assertEquals(StuckVerdict.Nudge(StuckSignal.REPEATED_STEP), detector.observe(step("loop", "in", "out")))

        // The nudge reaches a model only through the next prompt-composing
        // node, so the run gets a few steps to act on it before being ended.
        repeat(GraphStuckDetector.GRACE_STEPS - 1) { i ->
            assertEquals("grace step $i", StuckVerdict.Healthy, detector.observe(step("loop", "in", "out")))
        }
        assertEquals(StuckVerdict.Stop(StuckSignal.REPEATED_STEP), detector.observe(step("loop", "in", "out")))
    }

    @Test
    fun `given a nudge the run acts on then it is never stopped`() {
        // The outcome the first stage exists to produce, and the common one.
        val detector = GraphStuckDetector()
        detector.observe(step("seed", "start", "out"))
        repeat(2) { detector.observe(step("loop", "in", "out")) }
        assertEquals(StuckVerdict.Nudge(StuckSignal.REPEATED_STEP), detector.observe(step("loop", "in", "out")))

        repeat(GraphStuckDetector.WINDOW_SIZE * 2) { i ->
            val verdict = detector.observe(step("loop", "in-$i", "recovered $i"))
            assertEquals("step $i after recovering", StuckVerdict.Healthy, verdict)
        }
    }

    @Test
    fun `given a run that recovers and relapses then it is stopped without a second warning`() {
        // Deliberate, and the alternative is worse: rearming the escalation on
        // every recovery is what a loop alternating three repetitions with one
        // productive step — a queue re-seeding itself — would exploit forever.
        // A run gets one chance to take the advice, not one per episode.
        val detector = GraphStuckDetector()
        detector.observe(step("seed", "start", "out"))
        repeat(2) { detector.observe(step("loop", "in", "out")) }
        assertEquals(StuckVerdict.Nudge(StuckSignal.REPEATED_STEP), detector.observe(step("loop", "in", "out")))

        // It takes the advice and works productively for a long stretch.
        repeat(GraphStuckDetector.WINDOW_SIZE * 2) { i ->
            assertEquals("recovery step $i", StuckVerdict.Healthy, detector.observe(step("w", "in-$i", "new $i")))
        }

        // Then it relapses. The grace period was spent long ago, so this stops.
        repeat(GraphStuckDetector.REPEAT_THRESHOLD - 1) { detector.observe(step("loop2", "x", "new 0")) }
        assertEquals(
            StuckVerdict.Stop(StuckSignal.REPEATED_STEP),
            detector.observe(step("loop2", "x", "new 0")),
        )
    }

    @Test
    fun `given a run already stopped then it is never stopped twice`() {
        // The engine breaks out of its walk on the first Stop, but a detector
        // that kept returning one would make a second caller's control flow
        // depend on how often it asked.
        val detector = GraphStuckDetector()
        detector.observe(step("seed", "start", "out"))
        repeat(2) { detector.observe(step("loop", "in", "out")) }
        detector.observe(step("loop", "in", "out"))
        repeat(GraphStuckDetector.GRACE_STEPS) { detector.observe(step("loop", "in", "out")) }
        assertEquals(StuckVerdict.Healthy, detector.observe(step("loop", "in", "out")))
    }

    @Test
    fun `given a healthy run then it is never nudged`() {
        val detector = GraphStuckDetector()
        repeat(GraphStuckDetector.WINDOW_SIZE * 3) { i ->
            val verdict = detector.observe(step("n${i % 4}", "input $i", "output $i"))
            assertEquals("step $i", StuckVerdict.Healthy, verdict)
        }
    }

    // ─── Replay ──────────────────────────────────────────────────────────────

    @Test
    fun `given a replayed prefix then it rebuilds the window without ending the run`() {
        // A run that parks — which every answered background approval does —
        // must not resume blind to what it had already repeated. But the
        // attempt that ran this prefix did not stop on it, and reaching a
        // different verdict now would rewrite what already happened.
        val detector = GraphStuckDetector()
        detector.replay(step("seed", "start", "out"))
        // One repetition short of the nudge, so the prefix itself earns nothing
        // and only the warmth of the window is under test here.
        repeat(GraphStuckDetector.REPEAT_THRESHOLD - 1) {
            detector.replay(step("loop", "in", "out"))
        }

        // The window is warm: the very first live step completes the pattern
        // and is nudged immediately, rather than starting the count over.
        assertEquals(StuckVerdict.Nudge(StuckSignal.REPEATED_STEP), detector.observe(step("loop", "in", "out")))
    }

    @Test
    fun `given a replay of a healthy prefix then the live run starts clean`() {
        val detector = GraphStuckDetector()
        repeat(GraphStuckDetector.WINDOW_SIZE) { i -> detector.replay(step("n$i", "in$i", "out$i")) }
        assertEquals(StuckVerdict.Healthy, detector.observe(step("next", "in-next", "out-next")))
    }

    @Test
    fun `given a replayed prefix that had already earned a nudge then the resumed run does not start over`() {
        // The escape that made the per-attempt step budget useless in the task
        // before this one: answering a background approval is a RESUME, so a
        // loop that raises one gate per iteration gets a fresh everything after
        // every answer. If the escalation restarted here too, a loop that parks
        // more often than the grace period would never be stopped by the
        // detector at all.
        val detector = GraphStuckDetector()
        assertFalse("a healthy first step owes no note", detector.replay(step("seed", "start", "out")))
        val armed = (1..GraphStuckDetector.REPEAT_THRESHOLD + GraphStuckDetector.GRACE_STEPS).count {
            detector.replay(step("loop", "in", "out"))
        }
        // Exactly once, and only on the step that armed it: a note re-queued on
        // every replayed step afterwards would stack up on one prompt.
        assertEquals("the escalation is armed once", 1, armed)

        // The replayed prefix earned a nudge and spent its grace. The first
        // live repetition therefore stops the run rather than nudging it again.
        assertEquals(
            StuckVerdict.Stop(StuckSignal.REPEATED_STEP),
            detector.observe(step("loop", "in", "out")),
        )
    }

    // ─── Thresholds ──────────────────────────────────────────────────────────

    @Test
    fun `given the shipped thresholds then the detector can bind before the default step ceiling`() {
        // The whole point of the detector beside the ceilings: it must reach a
        // verdict inside the default allowance of 15 steps, or it would only
        // ever be an elaborate way of never being reached.
        val defaultStepCeiling = 15
        // One pass to produce the answer for the first time (which is genuine
        // progress), REPEAT_THRESHOLD passes repeating it to earn the nudge,
        // then GRACE_STEPS more to earn the stop.
        val worstCase = 1 + GraphStuckDetector.REPEAT_THRESHOLD + GraphStuckDetector.GRACE_STEPS
        assertTrue(
            "a tight loop must be stopped within $defaultStepCeiling steps, needs $worstCase",
            worstCase < defaultStepCeiling,
        )
        assertTrue(
            "a window wider than the ceiling could never fire",
            GraphStuckDetector.WINDOW_SIZE < defaultStepCeiling,
        )
        assertTrue(
            "the weaker signal must need more evidence than the stronger one",
            GraphStuckDetector.STALE_STREAK > GraphStuckDetector.REPEAT_THRESHOLD,
        )
        // The weaker signal must still be *reachable*. `worstCase` above covers
        // REPEATED_STEP only, so raising STALE_STREAK to quiet a false positive
        // could push NO_NEW_OUTPUT past every ceiling a user can set (the
        // maximum is 100) and silently retire it — a detector with one signal,
        // on a green build.
        val staleWorstCase = 1 + GraphStuckDetector.STALE_STREAK + GraphStuckDetector.GRACE_STEPS
        assertTrue(
            "NO_NEW_OUTPUT must stay reachable inside the largest configurable ceiling, needs $staleWorstCase",
            staleWorstCase < SettingsDefaults.PIPELINE_MAX_STEPS_MAX,
        )
        // And inside the *smallest* ceiling a run can be given, the strong
        // signal must still fit — otherwise a user who tightens their limit to
        // 5 has a detector that can never speak before the ceiling does, which
        // is the configuration where an unexplained stop is least welcome.
        assertTrue(
            "REPEATED_STEP must fit the tightest configurable ceiling, needs $worstCase",
            worstCase <= SettingsDefaults.PIPELINE_MAX_STEPS_MIN + GraphStuckDetector.GRACE_STEPS,
        )
    }

    @Test
    fun `given every signal then its diagnostic is terse and distinct`() {
        // Same contract the termination reasons hold themselves to: a console
        // line an engineer greps six months from now.
        val lines = StuckSignal.entries.map { it.diagnostic }
        assertEquals("two signals must not log the same line", lines.size, lines.toSet().size)
        lines.forEach { line ->
            assertTrue("one line: '$line'", !line.contains("\n"))
            assertTrue("lower case: '$line'", line == line.lowercase())
            assertTrue("no trailing punctuation: '$line'", !line.endsWith("."))
        }
    }
}
