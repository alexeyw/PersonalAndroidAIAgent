package app.knotwork.android.domain.engine.structured

import app.knotwork.android.domain.engine.LlmInferenceEngine
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Unit tests for [EngineStructuredInferenceClient].
 */
class EngineStructuredInferenceClientTest {

    private val engine: LlmInferenceEngine = mockk()

    @Test
    fun `given streamed tokens when infer then returns the concatenation`() = runTest {
        every { engine.generateResponseStream(any(), any(), any()) } returns flowOf("Hel", "lo")

        val result = EngineStructuredInferenceClient(engine).infer("prompt", temperature = null)

        assertEquals("Hello", result)
    }

    @Test
    fun `given a temperature when infer then it is forwarded to the engine`() = runTest {
        every { engine.generateResponseStream(any(), any(), any()) } returns flowOf("ok")

        EngineStructuredInferenceClient(engine).infer("prompt", temperature = 0.1f)

        verify { engine.generateResponseStream("prompt", null, 0.1f) }
    }

    @Test
    fun `given an onToken hook when infer then every token is forwarded before being appended`() = runTest {
        every { engine.generateResponseStream(any(), any(), any()) } returns flowOf("a", "b", "c")
        val seen = mutableListOf<String>()

        val result = EngineStructuredInferenceClient(engine) { seen += it }.infer("prompt", temperature = null)

        assertEquals(listOf("a", "b", "c"), seen)
        assertEquals("abc", result)
    }
}
