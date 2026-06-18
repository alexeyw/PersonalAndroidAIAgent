package app.knotwork.android.domain.engine.structured

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

/**
 * Unit tests for [StructuredOutputGate]: each output form (JSON object, JSON
 * array, constrained token), extraction from every payload packaging, the
 * repair loop fixing on a later attempt, exhaustion producing a fully-populated
 * [GateResult.Failed], the lowered repair temperature, repair-listener
 * notifications, and cancellation propagation.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class StructuredOutputGateTest {

    @Serializable
    private data class Verdict(val name: String, val count: Int)

    /**
     * Serializer whose `deserialize` throws a *bare* [IllegalArgumentException]
     * (not a `SerializationException`) over otherwise well-formed JSON — mimics
     * the invalid-number / unmappable-enum decode paths `kotlinx.serialization`
     * surfaces as a plain `IllegalArgumentException`. Confirms the gate routes
     * those into the repair loop rather than letting them tear down the run.
     */
    private object ThrowingStringSerializer : KSerializer<String> {
        override val descriptor: SerialDescriptor =
            PrimitiveSerialDescriptor("ThrowingString", PrimitiveKind.STRING)

        override fun deserialize(decoder: Decoder): String = throw IllegalArgumentException("decode rejected the value")

        override fun serialize(encoder: Encoder, value: String): Unit = error("unused in tests")
    }

    /**
     * Scripted [StructuredInferenceClient] that returns queued responses in
     * order and records the prompt and temperature of every call so the test can
     * assert the gate drove the repair loop correctly.
     */
    private class FakeInference(responses: List<String>) : StructuredInferenceClient {
        private val queue = ArrayDeque(responses)
        val temperatures = mutableListOf<Float?>()
        val prompts = mutableListOf<String>()
        var calls = 0
            private set

        override suspend fun infer(prompt: String, temperature: Float?): String {
            calls += 1
            prompts.add(prompt)
            temperatures.add(temperature)
            return queue.removeFirstOrNull() ?: error("no scripted response for inference call $calls")
        }
    }

    /** Records every repair notification for assertion. */
    private class RecordingListener : RepairListener {
        val events = mutableListOf<Triple<String, Int, Int>>()

        override fun onRepairAttempt(nodeName: String, attempt: Int, maxRepairs: Int) {
            events.add(Triple(nodeName, attempt, maxRepairs))
        }
    }

    private val gate = StructuredOutputGate()

    @Test
    fun `given valid json object on first attempt then success with zero repairs`() = runTest {
        val client = FakeInference(listOf("""{"name":"router","count":3}"""))

        val result = gate.runJson(client, "prompt", Verdict.serializer(), "Node", maxRepairs = 2)

        assertTrue(result is GateResult.Success)
        result as GateResult.Success
        assertEquals(Verdict("router", 3), result.value)
        assertEquals(0, result.repairs)
        assertEquals(1, client.calls)
        // First (only) attempt must not override the engine's default temperature.
        assertNull(client.temperatures.single())
    }

    @Test
    fun `given valid json array then list is deserialized`() = runTest {
        val client = FakeInference(listOf("""[{"name":"a","count":1},{"name":"b","count":2}]"""))

        val result = gate.runJson(
            client,
            "prompt",
            ListSerializer(Verdict.serializer()),
            "Node",
            maxRepairs = 0,
        )

        assertTrue(result is GateResult.Success)
        result as GateResult.Success
        assertEquals(listOf(Verdict("a", 1), Verdict("b", 2)), result.value)
    }

    @Test
    fun `given json wrapped in a fenced block then it is extracted and parsed`() = runTest {
        val client = FakeInference(listOf("Here:\n```json\n{\"name\":\"x\",\"count\":9}\n```\ndone"))

        val result = gate.runJson(client, "prompt", Verdict.serializer(), "Node", maxRepairs = 0)

        assertTrue(result is GateResult.Success)
        assertEquals(Verdict("x", 9), (result as GateResult.Success).value)
    }

    @Test
    fun `given malformed then valid on repair then success with one repair and lowered temperature`() = runTest {
        val client = FakeInference(
            listOf(
                "not json at all",
                """{"name":"fixed","count":1}""",
            ),
        )
        val listener = RecordingListener()

        val result = gate.runJson(client, "prompt", Verdict.serializer(), "Router", maxRepairs = 2, listener)

        assertTrue(result is GateResult.Success)
        result as GateResult.Success
        assertEquals(Verdict("fixed", 1), result.value)
        assertEquals(1, result.repairs)
        assertEquals(2, client.calls)
        // First call default temperature, repair call lowered to REPAIR_TEMPERATURE.
        assertNull(client.temperatures[0])
        assertEquals(StructuredOutputGate.REPAIR_TEMPERATURE, client.temperatures[1])
        // The repair prompt is fed back to the model, not the original prompt.
        assertTrue(client.prompts[1].contains("previous output was invalid", ignoreCase = true))
        // Exactly one repair notification, carrying the node name and the 1/2 fraction.
        assertEquals(listOf(Triple("Router", 1, 2)), listener.events)
    }

    @Test
    fun `given every attempt invalid then failed carries last raw error and repair count`() = runTest {
        val client = FakeInference(listOf("garbage one", "garbage two"))
        val listener = RecordingListener()

        val result = gate.runJson(client, "prompt", Verdict.serializer(), "Node", maxRepairs = 1, listener)

        assertTrue(result is GateResult.Failed)
        result as GateResult.Failed
        assertEquals("garbage two", result.lastRaw)
        assertEquals(1, result.repairs)
        assertTrue(result.lastError.isNotBlank())
        assertEquals(2, client.calls)
        assertEquals(1, listener.events.size)
    }

    @Test
    fun `given maxRepairs zero then a single invalid attempt fails without repairing`() = runTest {
        val client = FakeInference(listOf("nope"))
        val listener = RecordingListener()

        val result = gate.runJson(client, "prompt", Verdict.serializer(), "Node", maxRepairs = 0, listener)

        assertTrue(result is GateResult.Failed)
        assertEquals(0, (result as GateResult.Failed).repairs)
        assertEquals(1, client.calls)
        assertTrue(listener.events.isEmpty())
    }

    @Test
    fun `given negative maxRepairs then it is clamped to zero`() = runTest {
        val client = FakeInference(listOf("still nope"))

        val result = gate.runJson(client, "prompt", Verdict.serializer(), "Node", maxRepairs = -5)

        assertTrue(result is GateResult.Failed)
        assertEquals(0, (result as GateResult.Failed).repairs)
        assertEquals(1, client.calls)
    }

    @Test
    fun `given decode throws a bare IllegalArgumentException then it is treated as invalid not fatal`() = runTest {
        // Both attempts feed well-formed JSON, so extraction and JSON parsing
        // succeed and the failure comes solely from the serializer's deserialize.
        val client = FakeInference(listOf("\"hello\"", "\"world\""))

        val result = gate.runJson(client, "prompt", ThrowingStringSerializer, "Node", maxRepairs = 1)

        assertTrue(result is GateResult.Failed)
        result as GateResult.Failed
        assertEquals("\"world\"", result.lastRaw)
        assertEquals(1, result.repairs)
        assertTrue(result.lastError.contains("decode rejected the value"))
    }

    @Test
    fun `given a constrained token then the canonical spelling is returned regardless of case`() = runTest {
        val client = FakeInference(listOf("I think the answer is retry here."))

        val result = gate.runToken(
            client,
            "prompt",
            allowed = setOf("Pass", "Retry", "Fail"),
            nodeName = "Evaluation",
            maxRepairs = 0,
        )

        assertTrue(result is GateResult.Success)
        assertEquals("Retry", (result as GateResult.Success).value)
    }

    @Test
    fun `given a true-false token form then it matches whole words only`() = runTest {
        val client = FakeInference(listOf("False"))

        val result = gate.runToken(client, "prompt", setOf("True", "False"), "Condition", maxRepairs = 0)

        assertEquals("False", (result as GateResult.Success).value)
    }

    @Test
    fun `given no recognised token then it repairs then succeeds`() = runTest {
        val client = FakeInference(listOf("maybe?", "Pass"))
        val listener = RecordingListener()

        val result = gate.runToken(
            client,
            "prompt",
            allowed = setOf("Pass", "Retry", "Fail"),
            nodeName = "Evaluation",
            maxRepairs = 2,
            listener = listener,
        )

        assertTrue(result is GateResult.Success)
        assertEquals("Pass", (result as GateResult.Success).value)
        assertEquals(1, listener.events.size)
        assertEquals(StructuredOutputGate.REPAIR_TEMPERATURE, client.temperatures[1])
    }

    @Test
    fun `given an empty allowed set then runToken rejects the call`() = runTest {
        try {
            gate.runToken(FakeInference(emptyList()), "prompt", emptySet(), "Node", maxRepairs = 0)
            fail("expected IllegalArgumentException for an empty allowed token set")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message!!.contains("must not be empty"))
        }
    }

    @Test
    fun `given inference is cancelled then the cancellation propagates and is not swallowed`() = runTest {
        val client = StructuredInferenceClient { _, _ -> throw CancellationException("cancelled") }

        var propagated = false
        try {
            gate.runJson(client, "prompt", Verdict.serializer(), "Node", maxRepairs = 2)
        } catch (e: CancellationException) {
            propagated = true
            assertEquals("cancelled", e.message)
        }
        assertTrue(propagated)
    }
}
