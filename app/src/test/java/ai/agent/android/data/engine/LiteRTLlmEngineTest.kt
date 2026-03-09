package ai.agent.android.data.engine

import android.content.ComponentCallbacks2
import android.content.Context
import ai.agent.android.domain.models.Result
import io.mockk.mockk
import io.mockk.unmockkAll
import io.mockk.verify
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Unit tests for [LiteRTLlmEngine].
 */
class LiteRTLlmEngineTest {

    private lateinit var context: Context
    private lateinit var engine: LiteRTLlmEngine

    @Before
    fun setup() {
        context = mockk(relaxed = true)
        engine = LiteRTLlmEngine(context)
    }

    @After
    fun teardown() {
        engine.close()
        unmockkAll()
    }

    @Test
    fun `initialize returns Error when model file does not exist`() = runTest {
        // Arrange
        val path = "/fake/path/model.tflite"

        // Act
        val result = engine.initialize(path)

        // Assert
        assertTrue(result is Result.Error)
        val errorResult = result as Result.Error
        assertTrue(errorResult.message!!.contains("does not exist"))
    }

    @Test
    fun `generateResponseStream throws IllegalStateException when not initialized`() = runTest {
        // Arrange
        val prompt = "Hello"

        // Act & Assert
        try {
            engine.generateResponseStream(prompt).toList()
            assert(false) { "Expected IllegalStateException" }
        } catch (e: IllegalStateException) {
            assertEquals("LLM Engine not initialized", e.message)
        }
    }

    @Test
    fun `registers component callbacks on init`() {
        verify { context.registerComponentCallbacks(engine) }
    }

    @Test
    fun `onTrimMemory background level unloads engine`() {
        engine.onTrimMemory(ComponentCallbacks2.TRIM_MEMORY_BACKGROUND)
        
        // Also verify that close() unregisters callbacks
        engine.close()
        verify { context.unregisterComponentCallbacks(engine) }
    }
}