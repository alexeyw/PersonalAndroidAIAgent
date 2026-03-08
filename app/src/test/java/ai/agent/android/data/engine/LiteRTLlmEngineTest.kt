package ai.agent.android.data.engine

import ai.agent.android.domain.models.Result
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import io.mockk.verify
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File

/**
 * Unit tests for [LiteRTLlmEngine].
 */
class LiteRTLlmEngineTest {

    private lateinit var engine: LiteRTLlmEngine

    @Before
    fun setup() {
        engine = LiteRTLlmEngine()
        mockkStatic(File::class)
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
        val mockFile = mockk<File>()
        every { mockFile.exists() } returns false
        every { File(path) } returns mockFile

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
}